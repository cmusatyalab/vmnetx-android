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
                                                                       jstring tp, jstring pw, jstring cf, jstring cs, jboolean sound)
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

	result = spiceClientConnect (host, port, tls_port, password, ca_file, NULL, cert_subj, sound);

	jvm                  = NULL;
	jni_connector_class  = NULL;
	jni_settings_changed = NULL;
	jni_graphics_update  = NULL;
	return result;
}


int spiceClientConnect (const gchar *h, const gchar *p, const gchar *tp,
		                   const gchar *pw, const gchar *cf, GByteArray *cc,
                           const gchar *cs, const gboolean sound)
{
	spice_connection *conn;

    soundEnabled = sound;
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

static gboolean
parse_ovirt_uri(const gchar *uri_str, char **rest_uri, char **name)
{
    char *vm_name = NULL;
    char *rel_path;
    xmlURIPtr uri;
    gchar **path_elements;
    guint element_count;

    g_return_val_if_fail(uri_str != NULL, FALSE);
    g_return_val_if_fail(rest_uri != NULL, FALSE);
    g_return_val_if_fail(name != NULL, FALSE);

    uri = xmlParseURI(uri_str);
    if (uri == NULL)
        return FALSE;

    if (g_strcmp0(uri->scheme, "ovirt") != 0) {
        xmlFreeURI(uri);
        return FALSE;
    }

    if (uri->path == NULL) {
        xmlFreeURI(uri);
        return FALSE;
    }

    /* extract VM name from path */
    path_elements = g_strsplit(uri->path, "/", -1);

    element_count = g_strv_length(path_elements);
    if (element_count == 0) {
        g_strfreev(path_elements);
        xmlFreeURI(uri);
        return FALSE;
    }
    vm_name = path_elements[element_count-1];
    path_elements[element_count-1] = NULL;

    /* build final URI */
    rel_path = g_strjoinv("/", path_elements);
    /* FIXME: how to decide between http and https? */
    *rest_uri = g_strdup_printf("https://%s/%s/api/", uri->server, rel_path);
    *name = vm_name;
    g_free(rel_path);
    g_strfreev(path_elements);
    xmlFreeURI(uri);

    g_debug("oVirt base URI: %s", *rest_uri);
    g_debug("oVirt VM name: %s", *name);

    return TRUE;
}


void static sendMessage (JNIEnv* env, const int messageID) {
    gboolean attached = FALSE;
	if (env == NULL) {
	    attached = attachThreadToJvm (&env);
	}
    jclass class  = (*env)->FindClass (env, "com/iiordanov/bVNC/SpiceCommunicator");
	jmethodID sendMessage = (*env)->GetStaticMethodID (env, class, "sendMessage", "(I)V");
	(*env)->CallStaticVoidMethod(env, class, sendMessage, messageID);

    if (attached) {
    	detachThreadFromJvm ();
    }
}

gboolean reportIfSslError (JNIEnv* env, const gchar *message) {
	gboolean ssl_failed = ( g_strcmp0 (message, "SSL handshake failed") == 0 );
	if (ssl_failed) {
		sendMessage (env, 7); /* Constants.OVIRT_SSL_HANDSHAKE_FAILURE */
	}
	return ssl_failed;
}

static gboolean
authenticationCallback(RestProxy *proxy, G_GNUC_UNUSED RestProxyAuth *auth,
                gboolean retrying, gpointer user_data) {
	__android_log_write(6, "authenticationCallback", "authenticationCallback called.");
	if (retrying) {
		__android_log_write(6, "authenticationCallback", "Authentication has failed.");
		sendMessage (NULL, 6); /* Constants.OVIRT_AUTH_FAILURE */
	} else {
        g_object_set(G_OBJECT(proxy), "username", oVirtUser, "password", oVirtPassword, NULL);
	}
    return !retrying;
}


JNIEXPORT jint JNICALL
Java_com_iiordanov_bVNC_SpiceCommunicator_CreateOvirtSession(JNIEnv *env,
                                                                     jobject obj,
                                                                     jstring URI, jstring user, jstring pass,
                                                                     jstring sslCaFile,
                                                                     jboolean sound, jboolean sslStrict) {
    __android_log_write(6, "CreateOvirtSession", "Starting.");

    const gchar *uri = NULL;
    const gchar *username = NULL;
    const gchar *password = NULL;
    const gchar *ovirt_ca_file = NULL;

    uri                = (*env)->GetStringUTFChars(env, URI, NULL);
    username           = (*env)->GetStringUTFChars(env, user, NULL);
    password           = (*env)->GetStringUTFChars(env, pass, NULL);
    ovirt_ca_file      = (*env)->GetStringUTFChars(env, sslCaFile, NULL);

    return CreateOvirtSession(env, obj, uri, username, password, ovirt_ca_file, sound, sslStrict);
}


int CreateOvirtSession(JNIEnv *env, jobject obj, const gchar *uri, const gchar *user, const gchar *password,
                          const gchar *ovirt_ca_file, const gboolean sound, const gboolean sslStrict) {

    OvirtApi *api = NULL;
    OvirtCollection *vms = NULL;
    OvirtProxy *proxy = NULL;
    OvirtVm *vm = NULL;
    OvirtVmDisplay *display = NULL;
    OvirtVmState state;
    GError *error = NULL;
    char *rest_uri = NULL;
    char *vm_name = NULL;
    int success = 0;
    guint port;
    guint secure_port;
    OvirtVmDisplayType type;

    gchar *gport = NULL;
    gchar *gtlsport = NULL;
    gchar *ghost = NULL;
    gchar *ticket = NULL;
    gchar *spice_host_subject = NULL;

    if (!getJvmAndMethodReferences (env)) {
    	success = -1;
    	goto error;
    }

    // Set the global variables oVirtUser and oVirtPassword to be used in the callback later.
    oVirtUser     = user;
    oVirtPassword = password;

    if (!parse_ovirt_uri(uri, &rest_uri, &vm_name)) {
    	__android_log_write(6, "CreateOvirtSession", "ERROR: Could not parse oVirt URI");
        goto error;
    }

	__android_log_write(6, "CreateOvirtSession", rest_uri);
	__android_log_write(6, "CreateOvirtSession", vm_name);

    proxy = ovirt_proxy_new(rest_uri);
    if (proxy == NULL) {
    	__android_log_write(6, "CreateOvirtSession", "ERROR: Could not instantiate oVirt proxy");
        goto error;
    }
    g_signal_connect(G_OBJECT(proxy), "authenticate",
                     G_CALLBACK(authenticationCallback), NULL);

    __android_log_write(6, "CreateOvirtSession", "Setting ssl-ca-file in ovirt proxy object");
    g_object_set(proxy, "ssl-ca-file", ovirt_ca_file, NULL);

    // If we've been instructed to not be strict about SSL checks, then set the
    // requisite property.
    if (!sslStrict) {
    	g_object_set(G_OBJECT(proxy), "ssl-strict", FALSE, NULL);
    }

    __android_log_write(6, "CreateOvirtSession", "Attempting to fetch API");
    api = ovirt_proxy_fetch_api(proxy, &error);
    if (error != NULL || api == NULL) {
    	// If this is not an SSL error, report a general connection error.
    	if (!reportIfSslError (env, error->message)) {
    		sendMessage (env, 5); /* Constants.SPICE_CONNECT_FAILURE */
    	}
    	__android_log_write(6, "CreateOvirtSession", "Failed to fetch API, error:");
        __android_log_write(6, "CreateOvirtSession", error->message);
        g_debug("failed to fetch api: %s", error->message);
        goto error;
    }

    g_assert(api != NULL);
    __android_log_write(6, "CreateOvirtSession", "Attempting to fetch VMs");
    vms = ovirt_api_get_vms(api);
    g_assert(vms != NULL);
    ovirt_collection_fetch(vms, proxy, &error);
    if (error != NULL) {
        __android_log_write(6, "CreateOvirtSession", "Failed to fetch VMs, error:");
        __android_log_write(6, "CreateOvirtSession", error->message);
        g_debug("failed to fetch VMs: %s", error->message);
        goto error;
    }

    // Try to look up the VM.
    __android_log_write(6, "CreateOvirtSession", "Looking up VM");
    vm = OVIRT_VM(ovirt_collection_lookup_resource(vms, vm_name));

    if (vm == NULL) {
        __android_log_write(6, "CreateOvirtSession", "VM returned was null");
        sendMessage (env, 8); /* Constants.VM_LOOKUP_FAILED */
    	goto error;
    }

    __android_log_write(6, "CreateOvirtSession", "Checking the state of the VM");
    g_object_get(G_OBJECT(vm), "state", &state, NULL);
    if (state != OVIRT_VM_STATE_UP) {
		ovirt_vm_start(vm, proxy, &error);
		if (error != NULL) {
			__android_log_write(6, "CreateOvirtSession", "Failed to start VM, error:");
			__android_log_write(6, "CreateOvirtSession", error->message);
			g_debug("failed to start %s: %s", vm_name, error->message);
			goto error;
		}

        // Get a reference to the static void method used to add VM names to SpiceCommunicator.
		sendMessage (env, 3); /* Constants.VM_LAUNCHED */
    	goto error;
    	// TODO: The code below does not work because state == OVIRT_VM_STATE_UP even though
    	// the machine is not accepting connections for a while. Needs a deeper look.
/*
    	int tries = 0;
        while (tries <= 180 && state != OVIRT_VM_STATE_UP) {
			__android_log_write(6, "CreateOvirtSession", "Waiting for VM to start");
            sleep (1);
            vms = ovirt_api_get_vms(api);
            vm = OVIRT_VM(ovirt_collection_lookup_resource(vms, vm_name));
            g_object_get(G_OBJECT(vm), "state", &state, NULL);
            ++tries;
        }
*/
    }

    if (!ovirt_vm_get_ticket(vm, proxy, &error)) {
    	__android_log_write(6, "CreateOvirtSession", "ERROR: Failed to get ticket for requested oVirt VM.");
    	__android_log_write(6, "CreateOvirtSession", vm_name);
    	__android_log_write(6, "CreateOvirtSession", error->message);
        g_debug("failed to get ticket for %s: %s", vm_name, error->message);
        goto error;
    }

    g_object_get(G_OBJECT(vm), "display", &display, NULL);
    if (display == NULL) {
    	__android_log_write(6, "CreateOvirtSession", "ERROR: Failed to obtain requested oVirt VM display.");
        goto error;
    }

    g_object_get(G_OBJECT(display),
                 "type", &type,
                 "address", &ghost,
                 "port", &port,
                 "secure-port", &secure_port,
                 "ticket", &ticket,
                 "host-subject", &spice_host_subject,
                 NULL);

    gport = g_strdup_printf("%d", port);
    gtlsport = g_strdup_printf("%d", secure_port);

    if (type != OVIRT_VM_DISPLAY_SPICE) {
    	__android_log_write(6, "CreateOvirtSession", "VNC display type, trying to launch external app.");

        // Get a reference to the static void method used to add VM names to SpiceCommunicator.
        jclass class  = (*env)->FindClass (env, "com/iiordanov/bVNC/SpiceCommunicator");
    	jmethodID launchVncViewer = (*env)->GetStaticMethodID (env, class, "LaunchVncViewer", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
       	jstring vncAddress = (*env)->NewStringUTF(env, ghost);
       	jstring vncPort    = (*env)->NewStringUTF(env, gport);
       	jstring vncPassword  = (*env)->NewStringUTF(env, ticket);
        if (vncAddress != NULL && vncPort != NULL && vncPassword != NULL) {
        	// Call back into SpiceCommunicator to start external app
        	(*env)->CallStaticVoidMethod(env, class, launchVncViewer, vncAddress, vncPort, vncPassword);
        } else {
        	__android_log_write(6, "CreateOvirtSession", "One of the parameters was null");
        }
    } else {
    	GByteArray *ca_cert;
    	g_object_get(G_OBJECT(proxy), "ca-cert", &ca_cert, NULL);

    	// We are ready to start the SPICE connection.
    	success = spiceClientConnect (ghost, gport, gtlsport, ticket, NULL, ca_cert, spice_host_subject, sound);
    }

error:
    g_free(rest_uri);
    g_free(vm_name);
    g_free(ticket);
    g_free(gport);
    g_free(gtlsport);
    g_free(ghost);

    if (error != NULL)
        g_error_free(error);
    if (display != NULL)
        g_object_unref(display);
    if (vm != NULL)
        g_object_unref(vm);

    if (proxy != NULL)
        g_object_unref(proxy);

    return success;
}

JNIEXPORT jint JNICALL
Java_com_iiordanov_bVNC_SpiceCommunicator_FetchVmNames(JNIEnv *env,
		                                                         jobject obj,
		                                                         jstring URI, jstring user, jstring password,
		                                                         jstring sslCaFile, jboolean sslStrict) {
	__android_log_write(6, "FetchVmNames", "Starting.");

    OvirtApi *api = NULL;
    OvirtCollection *vms = NULL;
    OvirtProxy *proxy = NULL;
    GHashTable *name_to_vm_map = NULL;
    GError *error = NULL;
    const char *rest_uri = NULL;
    int success = 0;

    // Convert the uri, and ca_file's name from jstring to const char.
/*  gchar *uri = NULL;
 */
    const gchar *ovirt_ca_file = NULL;

    rest_uri     = (*env)->GetStringUTFChars(env, URI, NULL);
    ovirt_ca_file = (*env)->GetStringUTFChars(env, sslCaFile, NULL);

    // Set the global variables oVirtUser and oVirtPassword to be used in the callback later.
    oVirtUser     = (*env)->GetStringUTFChars(env, user, NULL);
    oVirtPassword = (*env)->GetStringUTFChars(env, password, NULL);

    if (!getJvmAndMethodReferences (env)) {
    	success = -1;
    	goto error;
    }

/*
    if (!parse_ovirt_uri(uri, &rest_uri, &vm_name)) {
    	__android_log_write(6, "FetchVmNames", "ERROR: Could not parse oVirt URI");
    	success = -1;
        goto error;
    }
	__android_log_write(6, "FetchVmNames", rest_uri);
*/
    proxy = ovirt_proxy_new(rest_uri);
    if (proxy == NULL) {
    	__android_log_write(6, "FetchVmNames", "ERROR: Could not instantiate oVirt proxy");
    	success = -1;
        goto error;
    }
    g_signal_connect(G_OBJECT(proxy), "authenticate",
                     G_CALLBACK(authenticationCallback), NULL);

    __android_log_write(6, "FetchVmNames", "Setting ssl-ca-file in ovirt proxy object");
    g_object_set(proxy, "ssl-ca-file", ovirt_ca_file, NULL);

    // If we've been instructed to not be strict about SSL checks, then set the
    // requisite property.
    if (!sslStrict) {
    	g_object_set(G_OBJECT(proxy), "ssl-strict", FALSE, NULL);
    }

    __android_log_write(6, "FetchVmNames", "Attempting to fetch API");
    api = ovirt_proxy_fetch_api(proxy, &error);
    if (error != NULL) {
    	// If this is not an SSL error, report a general connection error.
    	if (!reportIfSslError (env, error->message)) {
    		sendMessage (env, 5); /* Constants.SPICE_CONNECT_FAILURE */
    	}
    	__android_log_write(6, "FetchVmNames", "Failed to fetch API, error:");
        __android_log_write(6, "FetchVmNames", error->message);
        g_debug("failed to fetch api: %s", error->message);
    	success = -1;
        goto error;
    }

    g_assert(api != NULL);
    __android_log_write(6, "FetchVmNames", "Attempting to fetch VMs");
    vms = ovirt_api_get_vms(api);
    g_assert(vms != NULL);
    ovirt_collection_fetch(vms, proxy, &error);
    if (error != NULL) {
        __android_log_write(6, "FetchVmNames", "Failed to fetch VMs, error:");
        __android_log_write(6, "FetchVmNames", error->message);
        g_debug("failed to fetch VMs: %s", error->message);
    	success = -1;
        goto error;
    }

    name_to_vm_map = ovirt_collection_get_resources(vms);
    if (g_hash_table_size(name_to_vm_map) == 0) {
    	sendMessage (env, 9); /* Constants.NO_VM_FOUND_FOR_USER */
    }

    GHashTableIter iter;
    gpointer vmname, vm;
    g_hash_table_iter_init (&iter, name_to_vm_map);

    // Get a reference to the static void method used to add VM names to SpiceCommunicator.
    jclass class  = (*env)->FindClass (env, "com/iiordanov/bVNC/SpiceCommunicator");
	jmethodID jniAddVm = (*env)->GetStaticMethodID (env, class, "AddVm", "(Ljava/lang/String;)V");

    while (g_hash_table_iter_next (&iter, &vmname, &vm)) {
        __android_log_write(6, "FetchVmNames", (char*)vmname);

    	jstring vmName = (*env)->NewStringUTF(env, vmname);
    	if (vmName != NULL) {
    		// Call back into SpiceCommunicator to add this VM to the list
    		(*env)->CallStaticVoidMethod(env, class, jniAddVm, vmName);
    	}
    }

error:
    return success;
}
