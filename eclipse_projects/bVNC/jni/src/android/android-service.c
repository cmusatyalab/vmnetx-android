/**
 * Copyright (C) 2013 Iordan Iordanov
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
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

#include <libxml/uri.h>
#include <govirt/govirt.h>
#include <jni.h>
#include <android/bitmap.h>
#include <string.h>

#define ANDROID_SERVICE_C
#include "android-service.h"

#include "android-spice-widget.h"
#include "android-spicy.h"
#include "virt-viewer-file.h"

static gboolean disconnect(gpointer user_data);

inline gboolean attachThreadToJvm(JNIEnv** env) {
	gboolean attached = FALSE;
    int rs2 = 0;
    int rs1 = (*jvm)->GetEnv(jvm, (void**)env, JNI_VERSION_1_6);
    switch (rs1) {
    case JNI_OK:
    	break;
    case JNI_EDETACHED:
    	rs2 = (*jvm)->AttachCurrentThread(jvm, env, NULL);
    	if (rs2 != JNI_OK) {
    		__android_log_write(6, "android-io", "ERROR: Could not attach current thread to jvm.");
    	} else {
    		attached = TRUE;
    	}
    	break;
    }

    return attached;
}

inline void detachThreadFromJvm() {
	(*jvm)->DetachCurrentThread(jvm);
}

void spice_session_setup(SpiceSession *session, const char *host, const char *port,
                            const char *tls_port, const char *password, const char *ca_file,
                            GByteArray *ca_cert, const char *cert_subj) {

    g_return_if_fail(SPICE_IS_SESSION(session));

    if (host)
        g_object_set(session, "host", host, NULL);
    // If we receive "-1" for a port, we assume the port is not set.
    if (port && strcmp (port, "-1") != 0)
       g_object_set(session, "port", port, NULL);
    if (tls_port && strcmp (tls_port, "-1") != 0)
        g_object_set(session, "tls-port", tls_port, NULL);
    if (password)
        g_object_set(session, "password", password, NULL);
    if (ca_file)
        g_object_set(session, "ca-file", ca_file, NULL);
    if (ca_cert)
        g_object_set(session, "ca", ca_cert, NULL);
    if (cert_subj)
        g_object_set(session, "cert-subject", cert_subj, NULL);
}

static void signal_handler(int signal, siginfo_t *info, void *reserved) {
	kill(getpid(), SIGKILL);
}

/**
 * Implementation of the function used to trigger a disconnect.
 */
static gboolean disconnect(gpointer user_data) {
    __android_log_write(6, "disconnect", "Disconnecting the session.");
    connection_disconnect(global_conn);
    return FALSE;
}

/**
 * Called from the JVM, this function causes the SPICE client to disconnect from the server
 */
JNIEXPORT void JNICALL
Java_com_iiordanov_bVNC_SpiceCommunicator_SpiceClientDisconnect (JNIEnv * env, jobject  obj) {
    __android_log_write(6, "spiceDisconnect", "Disconnecting.");
    g_main_context_invoke (NULL, disconnect, NULL);
}

gboolean getJvmAndMethodReferences (JNIEnv *env) {
	// Get a reference to the JVM to get JNIEnv from in (other) threads.
    jint rs = (*env)->GetJavaVM(env, &jvm);
    if (rs != JNI_OK) {
    	__android_log_write(6, "getJvmAndMethodReferences", "ERROR: Could not obtain jvm reference.");
    	return FALSE;
    }

    // Find the jclass reference and get a Global reference for it for use in other threads.
    jclass local_class  = (*env)->FindClass (env, "com/iiordanov/bVNC/SpiceCommunicator");
	jni_connector_class = (jclass)((*env)->NewGlobalRef(env, local_class));

	// Get global method IDs for callback methods.
	jni_settings_changed = (*env)->GetStaticMethodID (env, jni_connector_class, "OnSettingsChanged", "(IIII)V");
	jni_graphics_update  = (*env)->GetStaticMethodID (env, jni_connector_class, "OnGraphicsUpdate", "(IIIII)V");
	return TRUE;
}

JNIEXPORT jint JNICALL
Java_com_iiordanov_bVNC_SpiceCommunicator_SpiceClientConnect (JNIEnv *env, jobject obj, jstring h, jstring p,
                                                                       jstring tp, jstring pw, jstring cf, jstring cs)
{
	const gchar *host = NULL;
	const gchar *port = NULL;
	const gchar *tls_port = NULL;
	const gchar *password = NULL;
	const gchar *ca_file = NULL;
	const gchar *cert_subj = NULL;
	int result = 0;

    if (!getJvmAndMethodReferences (env)) {
    	return -1;
    }

	host = (*env)->GetStringUTFChars(env, h, NULL);
	port = (*env)->GetStringUTFChars(env, p, NULL);
	tls_port  = (*env)->GetStringUTFChars(env, tp, NULL);
	password  = (*env)->GetStringUTFChars(env, pw, NULL);
	ca_file   = (*env)->GetStringUTFChars(env, cf, NULL);
	cert_subj = (*env)->GetStringUTFChars(env, cs, NULL);

	result = spiceClientConnect (host, port, tls_port, password, ca_file, NULL, cert_subj);

	jvm                  = NULL;
	jni_connector_class  = NULL;
	jni_settings_changed = NULL;
	jni_graphics_update  = NULL;
	return result;
}


int spiceClientConnect (const gchar *h, const gchar *p, const gchar *tp,
		                   const gchar *pw, const gchar *cf, GByteArray *cc,
                           const gchar *cs)
{
	spice_connection *conn;

    conn = connection_new();
	spice_session_setup(conn->session, h, p, tp, pw, cf, cc, cs);
	return connectSession(conn);
}

int connectSession (spice_connection *conn)
{
    int result = 0;

    __android_log_write(6, "connectSession", "Starting.");
    g_thread_init(NULL);
    g_type_init();
    mainloop = g_main_loop_new(NULL, FALSE);

    connection_connect(conn);
    if (connections > 0) {
        global_conn = conn;
        g_main_loop_run(mainloop);
        g_object_unref(mainloop);
	    __android_log_write(6, "connectSession", "Exiting main loop.");
    } else {
        __android_log_write(6, "connectSession", "Wrong hostname, port, or password.");
        result = 1;
    }

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
