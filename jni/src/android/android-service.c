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

#define ANDROID_SERVICE_C
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
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientDisconnect (JNIEnv * env, jobject  obj) {
    if (g_main_loop_is_running (mainloop))
        g_main_loop_quit (mainloop);
}


JNIEXPORT jint JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceClientConnect (JNIEnv *env, jobject obj, jstring pw)
{
    int result = 0;

    // Store JNIEnv and object for use in callbacks.
    jenv = env;
    jni_connector = obj;

    // Get method IDs for callback methods.
    jclass cls           = (*env)->GetObjectClass(env, obj);
    jni_get_fd           = (*env)->GetMethodID(env, cls, "OnGetFd", "(J)V");
    jni_settings_changed = (*env)->GetMethodID(env, cls, "OnSettingsChanged", "(IIII)V");
    jni_graphics_update  = (*env)->GetMethodID(env, cls, "OnGraphicsUpdate", "(IIIII)V");
    jni_cursor_config    = (*env)->GetMethodID(env, cls, "OnCursorConfig", "(Z)V");

    g_thread_init(NULL);
    bindtextdomain(GETTEXT_PACKAGE, SPICE_GTK_LOCALEDIR);
    bind_textdomain_codeset(GETTEXT_PACKAGE, "UTF-8");
    textdomain(GETTEXT_PACKAGE);

    g_type_init();
    mainloop = g_main_loop_new(NULL, false);

    spice_connection *conn;
    conn = connection_new();
    spice_session_setup(env, conn->session, pw);

    //watch_stdin();

    connection_connect(conn);
    if (connections > 0) {
        g_main_loop_run(mainloop);
        connection_disconnect(conn);
        g_object_unref(mainloop);
        __android_log_write(ANDROID_LOG_INFO, TAG, "Exiting main loop.");
    } else {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Wrong hostname, port, or password.");
        result = 2;
    }

    jni_get_fd           = NULL;
    jni_settings_changed = NULL;
    jni_graphics_update  = NULL;
    jni_cursor_config    = NULL;
    jni_connector        = NULL;
    jenv                 = NULL;
    return result;
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
