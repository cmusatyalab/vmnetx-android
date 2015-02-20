/**
 * Copyright (C) 2014-2015 Carnegie Mellon University
 * Copyright (C) 2013 Iordan Iordanov
 * Copyright (C) 2010 Red Hat, Inc.
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <glib.h>
#include <stdlib.h>
#include <spice-audio.h>

#include "android-io.h"
#include "android-spice.h"
#include "android-spice-widget.h"

#define TAG "vmnetx-spice"

static GOnce main_loop_starter = G_ONCE_INIT;

static gpointer spice_main_loop(gpointer data) {
    struct spice_main_thread *thr = data;

    // Attach thread and get JNIEnv
    JNIEnv *env;
    if ((*thr->jvm)->AttachCurrentThread(thr->jvm, &env, NULL) != JNI_OK) {
        __android_log_write(ANDROID_LOG_FATAL, TAG, "Couldn't attach main loop thread to JVM");
        abort();
    }
    thr->jenv = env;
    thr->jvm = NULL;

    // Run main loop
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Starting SPICE main loop");
    GMainLoop *mainloop = g_main_loop_new(NULL, false);
    g_main_loop_run(mainloop);

    // Main loop should never exit
    __android_log_write(ANDROID_LOG_FATAL, TAG, "SPICE main loop exited");
    abort();
}

static gpointer start_main_loop(gpointer data) {
    JNIEnv *env = data;
    struct spice_main_thread *thr = g_slice_new0(struct spice_main_thread);

    // Get JVM reference
    if ((*env)->GetJavaVM(env, &thr->jvm) != JNI_OK) {
        __android_log_write(ANDROID_LOG_FATAL, TAG, "Couldn't obtain JVM reference");
        abort();
    }

    // Get method IDs for callback methods
    jclass cls                = (*env)->FindClass(env, "org/olivearchive/vmnetx/android/SpiceCommunicator");
    thr->jni_get_fd           = (*env)->GetMethodID(env, cls, "OnGetFd", "(J)V");
    thr->jni_settings_changed = (*env)->GetMethodID(env, cls, "OnSettingsChanged", "(II)V");
    thr->jni_graphics_update  = (*env)->GetMethodID(env, cls, "OnGraphicsUpdate", "(IIII)V");
    thr->jni_mouse_mode       = (*env)->GetMethodID(env, cls, "OnMouseMode", "(Z)V");
    thr->jni_cursor_config    = (*env)->GetMethodID(env, cls, "OnCursorConfig", "(Z[IIIII)V");
    thr->jni_disconnect       = (*env)->GetMethodID(env, cls, "OnDisconnect", "()V");

    // Start thread
    GThread *thread = g_thread_new("main loop", spice_main_loop, thr);
    g_thread_unref(thread);
    return thr;
}

static void channel_open_fd(SpiceChannel *channel, gint with_tls,
                            gpointer data)
{
    struct spice_context *ctx = data;
    uiCallbackGetFd(ctx, channel);
}

static void main_channel_event(SpiceChannel *channel, SpiceChannelEvent event,
                               gpointer data)
{
    struct spice_context *ctx = data;

    switch (event) {
    case SPICE_CHANNEL_CLOSED:
    case SPICE_CHANNEL_ERROR_IO:
    case SPICE_CHANNEL_ERROR_TLS:
    case SPICE_CHANNEL_ERROR_LINK:
    case SPICE_CHANNEL_ERROR_CONNECT:
    case SPICE_CHANNEL_ERROR_AUTH:
        if (ctx->session)
            spice_session_disconnect(ctx->session);
        break;
    default:
        break;
    }
}

static void channel_new(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    struct spice_context *ctx = data;
    int id;

    g_object_get(channel, "channel-id", &id, NULL);
    ctx->channels++;
    SPICE_DEBUG("new channel (#%d)", id);

    g_signal_connect(channel, "open-fd",
                     G_CALLBACK(channel_open_fd), ctx);

    if (SPICE_IS_MAIN_CHANNEL(channel)) {
        SPICE_DEBUG("new main channel");
        g_signal_connect(channel, "channel-event",
                         G_CALLBACK(main_channel_event), ctx);
    }

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (ctx->display != NULL)
            return;
        SPICE_DEBUG("new display channel (#%d)", id);
        ctx->display = spice_display_new(ctx, id);
        ctx->display_channel = id;
    }

    if (SPICE_IS_PLAYBACK_CHANNEL(channel)) {
        SPICE_DEBUG("new audio channel");
        spice_audio_get(s, NULL);
    }
}

static void channel_destroy(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "channel_destroy called");

    struct spice_context *ctx = data;
    int id;

    g_object_get(channel, "channel-id", &id, NULL);

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (ctx->display && ctx->display_channel == id) {
            SPICE_DEBUG("zap display channel (#%d)", id);
            ctx->display = NULL;
        }
    }

    ctx->channels--;
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Number of channels: %d", ctx->channels);
    if (ctx->channels == 0) {
        //__android_log_write(ANDROID_LOG_DEBUG, TAG, "tearing down connection");
        g_object_unref(ctx->session);
        ctx->session = NULL;
        uiCallbackDisconnect(ctx);
    }
}

static gboolean do_disconnect(void *data) {
    struct spice_context *ctx = (struct spice_context *) data;
    if (ctx->session)
        spice_session_disconnect(ctx->session);
    return false;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientDisconnect (JNIEnv * env, jobject  obj, jlong context) {
    g_idle_add_full(G_PRIORITY_DEFAULT, do_disconnect, (void *) context, NULL);
}

struct connect_args {
    struct spice_context *ctx;
    char *password;
};

static gboolean do_connect(void *data) {
    struct connect_args *args = data;
    struct spice_context *ctx = args->ctx;

    ctx->session = spice_session_new();
    if (args->password)
        g_object_set(ctx->session, "password", args->password, NULL);

    g_signal_connect(ctx->session, "channel-new",
                     G_CALLBACK(channel_new), ctx);
    g_signal_connect(ctx->session, "channel-destroy",
                     G_CALLBACK(channel_destroy), ctx);
    spice_session_open_fd(ctx->session, -1);

    g_free(args->password);
    g_slice_free(struct connect_args, args);
    return false;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientConnect (JNIEnv *env, jobject obj, jlong context, jstring pw)
{
    struct connect_args *args = g_slice_new(struct connect_args);
    args->ctx = (struct spice_context *) context;

    const char *password = (*env)->GetStringUTFChars(env, pw, NULL);
    args->password = g_strdup(password);
    (*env)->ReleaseStringUTFChars(env, pw, password);

    g_idle_add_full(G_PRIORITY_DEFAULT, do_connect, args, NULL);
}

JNIEXPORT jlong JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientNewContext (JNIEnv *env, jobject obj)
{
    // Ensure main loop thread is running
    g_once(&main_loop_starter, start_main_loop, env);

    struct spice_context *ctx = g_slice_new0(struct spice_context);
    ctx->thr = main_loop_starter.retval;
    ctx->jni_connector = (*env)->NewGlobalRef(env, obj);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientFreeContext (JNIEnv *env, jobject obj, jlong context)
{
    struct spice_context *ctx = (struct spice_context *) context;
    (*env)->DeleteGlobalRef(env, ctx->jni_connector);
    g_slice_free(struct spice_context, ctx);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    return JNI_VERSION_1_6;
}
