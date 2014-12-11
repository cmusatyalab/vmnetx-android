/**
 * Copyright (C) 2013 Iordan Iordanov
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

#include "android-service.h"

#include "android-spice-widget.h"
#include "android-spicy.h"

#define TAG "vmnetx-service"

static void spice_session_setup(JNIEnv *env, SpiceSession *session, jstring pw) {
    g_return_if_fail(SPICE_IS_SESSION(session));

    const char *password = (*env)->GetStringUTFChars(env, pw, NULL);
    if (password)
        g_object_set(session, "password", password, NULL);
}


static void signal_handler(int signal, siginfo_t *info, void *reserved) {
    kill(getpid(), SIGKILL);
}


JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientDisconnect (JNIEnv * env, jobject  obj, jlong context) {
    struct spice_context *ctx = (struct spice_context *) context;

    if (g_main_loop_is_running(ctx->mainloop))
        g_main_loop_quit(ctx->mainloop);
}


JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientConnect (JNIEnv *env, jobject obj, jlong context, jstring pw)
{
    struct spice_context *ctx = (struct spice_context *) context;

    // Store JNIEnv and object for use in callbacks.
    ctx->jenv = env;
    ctx->jni_connector = obj;

    // Get method IDs for callback methods.
    jclass cls                = (*env)->GetObjectClass(env, obj);
    ctx->jni_get_fd           = (*env)->GetMethodID(env, cls, "OnGetFd", "(J)V");
    ctx->jni_settings_changed = (*env)->GetMethodID(env, cls, "OnSettingsChanged", "(IIII)V");
    ctx->jni_graphics_update  = (*env)->GetMethodID(env, cls, "OnGraphicsUpdate", "(IIIII)V");
    ctx->jni_cursor_config    = (*env)->GetMethodID(env, cls, "OnCursorConfig", "(Z)V");

    g_thread_init(NULL);
    bindtextdomain(GETTEXT_PACKAGE, SPICE_GTK_LOCALEDIR);
    bind_textdomain_codeset(GETTEXT_PACKAGE, "UTF-8");
    textdomain(GETTEXT_PACKAGE);

    g_type_init();
    ctx->mainloop = g_main_loop_new(NULL, false);

    spice_connection *conn = connection_new(ctx);
    spice_session_setup(env, conn->session, pw);

    connection_connect(conn);
    if (ctx->connections > 0) {
        g_main_loop_run(ctx->mainloop);
        connection_disconnect(conn);
        g_object_unref(ctx->mainloop);
        __android_log_write(ANDROID_LOG_INFO, TAG, "Exiting main loop.");
    } else {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Wrong hostname, port, or password.");
    }

    ctx->jni_get_fd           = NULL;
    ctx->jni_settings_changed = NULL;
    ctx->jni_graphics_update  = NULL;
    ctx->jni_cursor_config    = NULL;
    ctx->jni_connector        = NULL;
    ctx->jenv                 = NULL;
}

JNIEXPORT jlong JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientNewContext (JNIEnv *env, jobject obj)
{
    struct spice_context *ctx = g_slice_new0(struct spice_context);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientFreeContext (JNIEnv *env, jobject obj, jlong context)
{
    struct spice_context *ctx = (struct spice_context *) context;
    g_slice_free(struct spice_context, ctx);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    struct sigaction handler;
    memset(&handler, 0, sizeof(handler));
    handler.sa_sigaction = signal_handler;
    handler.sa_flags = SA_SIGINFO;
    sigaction(SIGILL, &handler, NULL);
    sigaction(SIGABRT, &handler, NULL);
    sigaction(SIGBUS, &handler, NULL);
    sigaction(SIGFPE, &handler, NULL);
    sigaction(SIGSEGV, &handler, NULL);
    sigaction(SIGSTKFLT, &handler, NULL);
    return(JNI_VERSION_1_6);
}
