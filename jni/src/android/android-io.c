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

#include <endian.h>
#include <jni.h>
#include <android/bitmap.h>
#include <android/keycodes.h>
#include <android/log.h>

#include "android-spice-widget.h"
#include "android-spice-widget-priv.h"
#include "android-io.h"
#include "android-service.h"
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

static void updatePixels (uint32_t *dest, uint32_t *source, int x, int y, int width, int height, int buffwidth, int buffheight) {
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Drawing x: %d, y: %d, w: %d, h: %d, wBuf: %d, hBuf: %d", x, y, width, height, wBuf, hBuf);
    uint32_t *sourcepix = &source[(buffwidth * y) + x];
    uint32_t *destpix   = &dest[(buffwidth * y) + x];

    for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j++) {
            // ARGB -> R G B X
            uint32_t value = sourcepix[j] << 8;
#if BYTE_ORDER == LITTLE_ENDIAN
            value = __builtin_bswap32(value);
#endif
            destpix[j] = value;
        }
        sourcepix = sourcepix + buffwidth;
        destpix   = destpix + buffwidth;
    }
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceUpdateBitmap (JNIEnv* env, jobject obj, jlong context, jobject bitmap, gint x, gint y, gint width, gint height) {
    struct spice_context *ctx = (struct spice_context *) context;
    void* pixels;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(ctx->display);

    assert_on_main_loop_thread();
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "AndroidBitmap_lockPixels() failed!");
        return;
    }
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Copying new data into pixels.");
    updatePixels (pixels, d->data, x, y, width, height, d->width, d->height);
    AndroidBitmap_unlockPixels(env, bitmap);
}

static int update_mask (SpiceDisplayPrivate *d, int button, gboolean down) {
    int update = 0;
    if (button == SPICE_MOUSE_BUTTON_LEFT)
        update = SPICE_MOUSE_BUTTON_MASK_LEFT;
    else if (button == SPICE_MOUSE_BUTTON_MIDDLE)
        update = SPICE_MOUSE_BUTTON_MASK_MIDDLE;
    else if (button == SPICE_MOUSE_BUTTON_RIGHT)
        update = SPICE_MOUSE_BUTTON_MASK_RIGHT;
    if (down) {
        d->mouse_button_mask |= update;
    } else {
        d->mouse_button_mask &= ~update;
    }
    return d->mouse_button_mask;
}


/* JNI functions related to input (keyboard, mouse), and output (display). */
/***************************************************************************/


JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceRequestResolution(JNIEnv* env, jobject obj, jlong context, jint x, jint y) {
    struct spice_context *ctx = (struct spice_context *) context;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(ctx->display);

    assert_on_main_loop_thread();
    spice_main_update_display(d->main, get_display_id(ctx->display), 0, 0, x, y, TRUE);
    spice_main_set_display_enabled(d->main, -1, TRUE);
    // TODO: Sending the monitor config right away may be causing guest OS to shut down.
    /*
    if (spice_main_send_monitor_config(d->main)) {
        __android_log_write(ANDROID_LOG_DEBUG, TAG, "Successfully sent monitor config");
    } else {
        __android_log_write(ANDROID_LOG_WARN, TAG, "Failed to send monitor config");
    }*/
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
    SpiceDisplayPrivate* d = SPICE_DISPLAY_GET_PRIVATE(ctx->display);

    SPICE_DEBUG("%s %s: keycode: %d", __FUNCTION__, "Key", keycode);

    if (!d->inputs)
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
            send_key(ctx->display, shift, 1);
        send_key(ctx->display, scancode, 1);
    } else {
        send_key(ctx->display, scancode, 0);
        if (shift)
            send_key(ctx->display, shift, 0);
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

struct button_event_args {
    struct spice_context *ctx;
    int x;
    int y;
    int type;
};

static gboolean do_button_event(void *data) {
    struct button_event_args *args = data;
    int x = args->x;
    int y = args->y;

    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(args->ctx->display);
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Pointer event: %d at x: %d, y: %d", args->type, x, y);

    if (!d->inputs || (x >= 0 && x < d->width && y >= 0 && y < d->height)) {

        gboolean down = (args->type & PTRFLAGS_DOWN) != 0;
        int mouseButton = args->type &~ PTRFLAGS_DOWN;
        int newMask = update_mask(d, mouseButton, down);

        gint dx;
        gint dy;
        switch (d->mouse_mode) {
        case SPICE_MOUSE_MODE_CLIENT:
            //__android_log_write(ANDROID_LOG_DEBUG, TAG, "spice mouse mode client");
            spice_inputs_position(d->inputs, x, y, d->channel_id, newMask);
            break;
        case SPICE_MOUSE_MODE_SERVER:
            //__android_log_write(ANDROID_LOG_DEBUG, TAG, "spice mouse mode server");
            dx = d->mouse_last_x != -1 ? x - d->mouse_last_x : 0;
            dy = d->mouse_last_y != -1 ? y - d->mouse_last_y : 0;
            spice_inputs_motion(d->inputs, dx, dy, newMask);
            d->mouse_last_x = x;
            d->mouse_last_y = y;
            break;
        default:
            g_warn_if_reached();
            break;
        }

        if (mouseButton != SPICE_MOUSE_BUTTON_INVALID) {
            if (down) {
                //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Button press");
                spice_inputs_button_press(d->inputs, mouseButton, newMask);
            } else {
                //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Button release");
                // This sleep is an ugly hack to prevent stuck buttons after a drag/drop gesture.
                usleep(50000);
                spice_inputs_button_release(d->inputs, mouseButton, newMask);
            }
        }
    }
    g_slice_free(struct button_event_args, args);
    return false;
}

JNIEXPORT void JNICALL
Java_org_olivearchive_vmnetx_android_SpiceCommunicator_SpiceButtonEvent(JNIEnv * env, jobject  obj, jlong context, jint x, jint y, jint type) {
    struct button_event_args *args = g_slice_new(struct button_event_args);
    args->ctx = (struct spice_context *) context;
    args->x = x;
    args->y = y;
    args->type = type;
    g_idle_add_full(G_PRIORITY_DEFAULT, do_button_event, args, NULL);
}

/* Callbacks to the UI layer to draw screen updates and invalidate part of the screen,
 * or to request a new bitmap. */

void uiCallbackGetFd (struct spice_context *ctx, SpiceChannel *channel) {
    // Ask the UI to connect a file descriptor for us.
    //assert_on_main_loop_thread();
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_get_fd, (jlong) channel);
}

void uiCallbackInvalidate (struct spice_context *ctx, gint x, gint y, gint w, gint h) {
    // Tell the UI that it needs to send in the bitmap to be updated and to redraw.
    assert_on_main_loop_thread();
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_graphics_update, 0, x, y, w, h);
}

void uiCallbackSettingsChanged (struct spice_context *ctx, gint instance, gint width, gint height, gint bpp) {
    // Ask for a new bitmap from the UI.
    assert_on_main_loop_thread();
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_settings_changed, instance, width, height, bpp);
}

void uiCallbackCursorConfig (struct spice_context *ctx, bool absolute_mouse) {
    assert_on_main_loop_thread();
    (*ctx->thr->jenv)->CallVoidMethod(ctx->thr->jenv, ctx->jni_connector, ctx->thr->jni_cursor_config, absolute_mouse);
}
