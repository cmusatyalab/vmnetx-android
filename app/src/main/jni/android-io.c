/**
 * Copyright (C) 2014-2015 Carnegie Mellon University
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

/* JNI functions related to input (keyboard, mouse), and output (display). */
/***************************************************************************/

#include <endian.h>
#include <jni.h>
#include <android/bitmap.h>
#include <android/input.h>
#include <android/keycodes.h>
#include <android/log.h>
#include <stdlib.h>

#include "android-spice-display.h"
#include "android-io.h"
#include "android-spice.h"
#include "keymap.h"

#define TAG "vmnetx-io"

void _assert_on_main_loop_thread(const char *caller) {
    if (G_UNLIKELY(!g_main_context_is_owner(g_main_context_default()))) {
        __android_log_print(ANDROID_LOG_FATAL, TAG, "%s called outside main loop thread", caller);
        abort();
    }
}

struct set_fd_args {
    SpiceChannel *channel;
    int fd;
};

static gboolean do_set_fd(void *data) {
    struct set_fd_args *args = data;

    if (!spice_channel_open_fd(args->channel, args->fd)) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to open FD");
    }
    g_slice_free(struct set_fd_args, args);
    return false;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceSetFd(JNIEnv *env, jobject obj, jlong cookie, jint fd) {
    struct set_fd_args *args = g_slice_new(struct set_fd_args);
    args->channel = (SpiceChannel *) cookie;
    args->fd = fd;
    g_idle_add_full(G_PRIORITY_DEFAULT, do_set_fd, args, NULL);
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceUpdateBitmap (JNIEnv* env, jobject obj, jlong context, jobject bitmap, gint x, gint y, gint width, gint height) {
    struct spice_context *ctx = (struct spice_context *) context;
    void* pixels;

    assert_on_main_loop_thread();
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "AndroidBitmap_lockPixels() failed!");
        return;
    }
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Copying new data into pixels.");
    if (ctx->display)
        spice_display_copy_pixels(ctx->display, pixels, x, y, width, height);
    else
        memset(pixels, 0, width * height * 4);
    AndroidBitmap_unlockPixels(env, bitmap);
}

struct redraw_args {
    struct spice_context *ctx;
    // what we really want is a semaphore or completion
    GMutex lock;
    GCond cond;
    bool done;
};

static gboolean do_redraw(void *data) {
    struct redraw_args *args = data;

    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Forcing full invalidate");
    if (args->ctx->display)
        spice_display_invalidate(args->ctx->display);

    g_mutex_lock(&args->lock);
    args->done = true;
    g_cond_broadcast(&args->cond);
    g_mutex_unlock(&args->lock);
    // do not free struct!
    return false;
}

// Invoke an OnGraphicsUpdate callback from the main loop thread and block
// until it has completed.
JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceForceRedraw (JNIEnv* env, jobject obj, jlong context) {
    struct redraw_args args;
    args.ctx = (struct spice_context *) context;
    g_mutex_init(&args.lock);
    g_cond_init(&args.cond);
    args.done = false;
    g_idle_add_full(G_PRIORITY_DEFAULT, do_redraw, &args, NULL);

    g_mutex_lock(&args.lock);
    while (!args.done)
        g_cond_wait(&args.cond, &args.lock);
    g_mutex_unlock(&args.lock);

    g_cond_clear(&args.cond);
    g_mutex_clear(&args.lock);
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceRequestResolution(JNIEnv* env, jobject obj, jlong context, jint w, jint h) {
    struct spice_context *ctx = (struct spice_context *) context;

    assert_on_main_loop_thread();
    if (ctx->display)
        spice_display_request_resolution(ctx->display, w, h);
}

struct key_event_args {
    struct spice_context *ctx;
    bool down;
    int keycode;
};

static gboolean do_key_event(void *data) {
    struct key_event_args *args = data;
    struct spice_context *ctx = args->ctx;
    int keycode = args->keycode;

    SPICE_DEBUG("%s %s: keycode: %d", __FUNCTION__, "Key", keycode);

    if (!ctx->display)
        goto OUT;

    // The lookup table doesn't include mappings that require multiple
    // keypresses, so translate them here
    int shift = keymap_android2xtkbd[AKEYCODE_SHIFT_LEFT];
    switch (keycode) {
    case AKEYCODE_AT:
        keycode = AKEYCODE_2;
        break;
    case AKEYCODE_POUND:
        keycode = AKEYCODE_3;
        break;
    case AKEYCODE_STAR:
        keycode = AKEYCODE_8;
        break;
    default:
        shift = 0;
        break;
    }

    int scancode = 0;
    if (keycode > 0 && keycode < G_N_ELEMENTS(keymap_android2xtkbd)) {
        scancode = keymap_android2xtkbd[keycode];
    }
    if (!scancode)
        goto OUT;
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Converted Android key %d to scancode %d", keycode, scancode);

    if (args->down) {
        if (shift)
            spice_display_send_key(ctx->display, shift, true);
        spice_display_send_key(ctx->display, scancode, true);
    } else {
        spice_display_send_key(ctx->display, scancode, false);
        if (shift)
            spice_display_send_key(ctx->display, shift, false);
    }

OUT:
    g_slice_free(struct key_event_args, args);
    return false;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceKeyEvent(JNIEnv * env, jobject  obj, jlong context, jboolean down, jint keycode) {
    struct key_event_args *args = g_slice_new(struct key_event_args);
    args->ctx = (struct spice_context *) context;
    args->down = down;
    args->keycode = keycode;
    g_idle_add_full(G_PRIORITY_DEFAULT, do_key_event, args, NULL);
}

struct pointer_event_args {
    struct spice_context *ctx;
    bool absolute;
    int x;
    int y;
};

static gboolean do_pointer_event(void *data) {
    struct pointer_event_args *args = data;

    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Pointer event: %d at x: %d, y: %d", args->type, x, y);
    if (args->ctx->display)
        spice_display_send_pointer(args->ctx->display, args->absolute,
                args->x, args->y);

    g_slice_free(struct pointer_event_args, args);
    return false;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpicePointerEvent(JNIEnv * env, jobject obj, jlong context, jboolean absolute, jint x, jint y) {
    struct pointer_event_args *args = g_slice_new(struct pointer_event_args);
    args->ctx = (struct spice_context *) context;
    args->absolute = absolute;
    args->x = x;
    args->y = y;
    g_idle_add_full(G_PRIORITY_DEFAULT, do_pointer_event, args, NULL);
}

struct button_event_args {
    struct spice_context *ctx;
    int button;
    bool down;
};

static gboolean do_button_event(void *data) {
    struct button_event_args *args = data;

    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Button event: button %d, down %d", args->button, args->down);

    int spice_button;
    switch (args->button) {
    case AMOTION_EVENT_BUTTON_PRIMARY:
        spice_button = SPICE_MOUSE_BUTTON_LEFT;
        break;
    case AMOTION_EVENT_BUTTON_SECONDARY:
        spice_button = SPICE_MOUSE_BUTTON_RIGHT;
        break;
    case AMOTION_EVENT_BUTTON_TERTIARY:
        spice_button = SPICE_MOUSE_BUTTON_MIDDLE;
        break;
    default:
        return false;
    }

    if (args->ctx->display)
        spice_display_send_button(args->ctx->display, spice_button,
                args->down);

    g_slice_free(struct button_event_args, args);
    return false;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceButtonEvent(JNIEnv * env, jobject obj, jlong context, jboolean down, jint button) {
    struct button_event_args *args = g_slice_new(struct button_event_args);
    args->ctx = (struct spice_context *) context;
    args->down = down;
    args->button = button;
    g_idle_add_full(G_PRIORITY_DEFAULT, do_button_event, args, NULL);
}

struct scroll_event_args {
    struct spice_context *ctx;
    int button;
    int count;
};

static gboolean do_scroll_event(void *data) {
    struct scroll_event_args *args = data;

    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Scroll event: button %d, count %d", args->button, args->count);
    if (args->ctx->display)
        spice_display_send_scroll(args->ctx->display, args->button,
                args->count);

    g_slice_free(struct scroll_event_args, args);
    return false;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceScrollEvent(JNIEnv * env, jobject  obj, jlong context, jint button, jint count) {
    struct scroll_event_args *args = g_slice_new(struct scroll_event_args);
    args->ctx = (struct spice_context *) context;
    args->button = button;
    args->count = count;
    g_idle_add_full(G_PRIORITY_DEFAULT, do_scroll_event, args, NULL);
}

/* Callbacks to the UI layer to draw screen updates and invalidate part of the screen,
 * or to request a new bitmap. */

void uiCallbackGetFd (struct spice_context *ctx, SpiceChannel *channel) {
    // Ask the UI to connect a file descriptor for us.
    assert_on_main_loop_thread();
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_get_fd, (jlong) channel);
}

void uiCallbackInvalidate (struct spice_context *ctx, gint x, gint y, gint w, gint h) {
    // Tell the UI that it needs to send in the bitmap to be updated and to redraw.
    assert_on_main_loop_thread();
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_graphics_update, x, y, w, h);
}

void uiCallbackSettingsChanged (struct spice_context *ctx, gint width, gint height) {
    // Ask for a new bitmap from the UI.
    assert_on_main_loop_thread();
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_settings_changed, width, height);
}

void uiCallbackMouseMode (struct spice_context *ctx, bool absolute_mouse) {
    assert_on_main_loop_thread();
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_mouse_mode, absolute_mouse);
}

void uiCallbackCursorConfig (struct spice_context *ctx, bool shown, uint32_t *data, int w, int h, int hot_x, int hot_y) {
    assert_on_main_loop_thread();
    jintArray *bitmap = NULL;
    if (data) {
        // make Java array
        bitmap = (*ctx->thr->jenv)->NewIntArray(ctx->thr->jenv, w * h);
        if (!bitmap) {
            __android_log_write(ANDROID_LOG_FATAL, TAG, "Couldn't allocate array for cursor data");
            abort();
        }
        // convert R G B A -> ARGB and copy
        int *elems = (*ctx->thr->jenv)->GetPrimitiveArrayCritical(ctx->thr->jenv, bitmap, NULL);
        if (!elems) {
            __android_log_write(ANDROID_LOG_FATAL, TAG, "Couldn't access array for cursor data");
            abort();
        }
        for (int64_t i = 0; i < w * h; i++) {
            uint32_t val = GUINT32_FROM_BE(data[i]);
            elems[i] = (val << 24) | (val >> 8);
        }
        (*ctx->thr->jenv)->ReleasePrimitiveArrayCritical(ctx->thr->jenv, bitmap, elems, 0);
    }
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_cursor_config, shown, bitmap, w, h, hot_x, hot_y);
    // Main loop thread never frees local references
    if (bitmap)
        (*ctx->thr->jenv)->DeleteLocalRef(ctx->thr->jenv, bitmap);
}

void uiCallbackDisconnect (struct spice_context *ctx) {
    assert_on_main_loop_thread();
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_disconnect);
}
