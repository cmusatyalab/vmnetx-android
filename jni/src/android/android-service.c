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
#include <glib.h>

#include "android-io.h"
#include "android-service.h"

#include "android-spice-widget.h"
#include "android-spicy.h"

#define TAG "vmnetx-service"

static GOnce main_loop_starter = G_ONCE_INIT;

static void signal_handler(int signal, siginfo_t *info, void *reserved) {
    kill(getpid(), SIGKILL);
}

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
    GError *my_err = NULL;

    // Initialize native libraries
    g_thread_init(NULL);
    bindtextdomain(GETTEXT_PACKAGE, SPICE_GTK_LOCALEDIR);
    bind_textdomain_codeset(GETTEXT_PACKAGE, "UTF-8");
    textdomain(GETTEXT_PACKAGE);
    g_type_init();

    // Get JVM reference
    if ((*env)->GetJavaVM(env, &thr->jvm) != JNI_OK) {
        __android_log_write(ANDROID_LOG_FATAL, TAG, "Couldn't obtain JVM reference");
        abort();
    }

    // Get method IDs for callback methods
    jclass cls                = (*env)->FindClass(env, "org/olivearchive/vmnetx/android/SpiceCommunicator");
    thr->jni_get_fd           = (*env)->GetMethodID(env, cls, "OnGetFd", "(J)V");
    thr->jni_settings_changed = (*env)->GetMethodID(env, cls, "OnSettingsChanged", "(IIII)V");
    thr->jni_graphics_update  = (*env)->GetMethodID(env, cls, "OnGraphicsUpdate", "(IIIII)V");
    thr->jni_cursor_config    = (*env)->GetMethodID(env, cls, "OnCursorConfig", "(Z)V");
    thr->jni_disconnect       = (*env)->GetMethodID(env, cls, "OnDisconnect", "()V");

    // Start thread
    if (!g_thread_create(spice_main_loop, thr, false, &my_err)) {
        __android_log_print(ANDROID_LOG_FATAL, TAG, "Couldn't start main loop thread: %s", my_err->message);
        abort();
    }
    return thr;
}

static gboolean do_disconnect(void *data) {
    struct spice_context *ctx = (struct spice_context *) data;
    connection_disconnect(ctx->conn);
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

    ctx->conn = connection_new(ctx);
    if (args->password)
        g_object_set(ctx->conn->session, "password", args->password, NULL);

    connection_connect(ctx->conn);
    if (ctx->connections == 0) {
        // failed to connect
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Wrong hostname, port, or password.");
        uiCallbackDisconnect(ctx);
    }

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
