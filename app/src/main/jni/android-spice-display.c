/*
   Copyright (C) 2014-2015 Carnegie Mellon University
   Copyright (C) 2013 Iordan Iordanov
   Copyright (C) 2010 Red Hat, Inc.

   This library is free software; you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public
   License as published by the Free Software Foundation; either
   version 2.1 of the License, or (at your option) any later version.

   This library is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public
   License along with this library; if not, see <http://www.gnu.org/licenses/>.
*/
#include <math.h>
#include <android/log.h>

#include "android-spice-display.h"
#include "android-spice-display-priv.h"
#include "android-io.h"
#include "android-spice.h"

#define TAG "vmnetx-spice-display"

G_DEFINE_TYPE(SpiceDisplay, spice_display, SPICE_TYPE_CHANNEL);

static void disconnect_main(SpiceDisplay *display);
static void disconnect_display(SpiceDisplay *display);
static void disconnect_cursor(SpiceDisplay *display);
static void channel_new(SpiceSession *s, SpiceChannel *channel, gpointer data);
static void channel_destroy(SpiceSession *s, SpiceChannel *channel, gpointer data);

/* ---------------------------------------------------------------- */

static void spice_display_dispose(GObject *obj)
{
    SpiceDisplay *display = SPICE_DISPLAY(obj);
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    SPICE_DEBUG("spice display dispose");

    disconnect_main(display);
    disconnect_display(display);
    disconnect_cursor(display);

    if (d->session) {
        g_signal_handlers_disconnect_by_func(d->session, G_CALLBACK(channel_new),
                                             display);
        g_signal_handlers_disconnect_by_func(d->session, G_CALLBACK(channel_destroy),
                                             display);
        g_object_unref(d->session);
        d->session = NULL;
    }

    G_OBJECT_CLASS(spice_display_parent_class)->dispose(obj);
}

static void spice_display_class_init(SpiceDisplayClass *klass)
{
    GObjectClass *gobject_class = G_OBJECT_CLASS(klass);
    gobject_class->dispose = spice_display_dispose;
    g_type_class_add_private(klass, sizeof(SpiceDisplayPrivate));
}

static void spice_display_init(SpiceDisplay *display)
{
    SpiceDisplayPrivate *d;

    d = display->priv = SPICE_DISPLAY_GET_PRIVATE(display);
    memset(d, 0, sizeof(*d));
}

static int get_display_id(SpiceDisplay *display)
{
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    /* supported monitor_id only with display channel #0 */
    if (d->channel_id == 0 && d->monitor_id >= 0)
        return d->monitor_id;

    g_return_val_if_fail(d->monitor_id <= 0, -1);

    return d->channel_id;
}

/* ---------------------------------------------------------------- */

static void update_mouse_mode(SpiceChannel *channel, gpointer data)
{
    SpiceDisplay *display = data;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    int mode;
    g_object_get(channel, "mouse-mode", &mode, NULL);
    uiCallbackMouseMode(d->ctx, mode == SPICE_MOUSE_MODE_CLIENT);
}

static void cursor_set(SpiceCursorChannel *cursor,
                       int w, int h,
                       int hot_x, int hot_y,
                       void *bitmap, void *data) {
    SpiceDisplay *display = data;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    uiCallbackCursorConfig(d->ctx, true, bitmap, w, h, hot_x, hot_y);
}

static void cursor_hide(SpiceCursorChannel *cursor, void *data) {
    SpiceDisplay *display = data;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    uiCallbackCursorConfig(d->ctx, false, NULL, 0, 0, 0, 0);
}

static void cursor_reset(SpiceCursorChannel *cursor, void *data) {
    SpiceDisplay *display = data;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    uiCallbackCursorConfig(d->ctx, true, NULL, 0, 0, 0, 0);
}

/* ---------------------------------------------------------------- */

#define CONVERT_0565_TO_0888(s)                                         \
    (((((s) << 3) & 0xf8) | (((s) >> 2) & 0x7)) |                       \
     ((((s) << 5) & 0xfc00) | (((s) >> 1) & 0x300)) |                   \
     ((((s) << 8) & 0xf80000) | (((s) << 3) & 0x70000)))

#define CONVERT_0565_TO_8888(s) (CONVERT_0565_TO_0888(s) | 0xff000000)

#define CONVERT_0555_TO_0888(s)                                         \
    (((((s) & 0x001f) << 3) | (((s) & 0x001c) >> 2)) |                  \
     ((((s) & 0x03e0) << 6) | (((s) & 0x0380) << 1)) |                  \
     ((((s) & 0x7c00) << 9) | ((((s) & 0x7000)) << 4)))

#define CONVERT_0555_TO_8888(s) (CONVERT_0555_TO_0888(s) | 0xff000000)

static gboolean do_color_convert(SpiceDisplay *display,
                                 gint x, gint y, gint w, gint h)
{
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);
    int i, j, maxy, maxx, miny, minx;
    guint32 *dest = d->data;
    guint16 *src = d->data_origin;

    if (!d->convert)
        return TRUE;

    g_return_val_if_fail(d->format == SPICE_SURFACE_FMT_16_555 ||
                         d->format == SPICE_SURFACE_FMT_16_565, FALSE);

    miny = MAX(y, 0);
    minx = MAX(x, 0);
    maxy = MIN(y + h, d->height);
    maxx = MIN(x + w, d->width);

    dest +=  (d->stride / 4) * miny;
    src += (d->stride / 2) * miny;

    if (d->format == SPICE_SURFACE_FMT_16_555) {
        for (j = miny; j < maxy; j++) {
            for (i = minx; i < maxx; i++) {
                dest[i] = CONVERT_0555_TO_0888(src[i]);
            }

            dest += d->stride / 4;
            src += d->stride / 2;
        }
    } else if (d->format == SPICE_SURFACE_FMT_16_565) {
        for (j = miny; j < maxy; j++) {
            for (i = minx; i < maxx; i++) {
                dest[i] = CONVERT_0565_TO_0888(src[i]);
            }

            dest += d->stride / 4;
            src += d->stride / 2;
        }
    }

    return TRUE;
}

/* ---------------------------------------------------------------- */

void spice_display_copy_pixels(SpiceDisplay *display, uint32_t *dest,
                               int x, int y, int width, int height) {
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);
    uint32_t *source = d->data;
    uint32_t *sourcepix = &source[(d->width * y) + x];
    uint32_t *destpix   = &dest[(d->width * y) + x];

    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Drawing x: %d, y: %d, w: %d, h: %d, wBuf: %d, hBuf: %d", x, y, width, height, d->width, d->height);
    for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j++) {
            // ARGB -> R G B X
            uint32_t value = sourcepix[j] << 8;
#if BYTE_ORDER == LITTLE_ENDIAN
            value = __builtin_bswap32(value);
#endif
            destpix[j] = value;
        }
        sourcepix = sourcepix + d->width;
        destpix   = destpix + d->width;
    }
}

void spice_display_invalidate(SpiceDisplay *display) {
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);
    uiCallbackInvalidate(d->ctx, 0, 0, d->width, d->height);
}

void spice_display_request_resolution(SpiceDisplay *display, int w, int h) {
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    spice_main_update_display(d->main, get_display_id(display), 0, 0,
                              w, h, TRUE);
    spice_main_set_display_enabled(d->main, -1, TRUE);
    // TODO: Sending the monitor config right away may be causing guest OS to shut down.
    /*
    if (spice_main_send_monitor_config(d->main)) {
        __android_log_write(ANDROID_LOG_DEBUG, TAG, "Successfully sent monitor config");
    } else {
        __android_log_write(ANDROID_LOG_WARN, TAG, "Failed to send monitor config");
    }*/
}

void spice_display_send_key(SpiceDisplay *display, int scancode, bool down) {
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);
    uint32_t i, b, m;

    if (!d->inputs)
        return;

    i = scancode / 32;
    b = scancode % 32;
    m = (1 << b);
    g_return_if_fail(i < G_N_ELEMENTS(d->key_state));

    if (down) {
        spice_inputs_key_press(d->inputs, scancode);
        d->key_state[i] |= m;
    } else {
        if (!(d->key_state[i] & m)) {
            return;
        }
        spice_inputs_key_release(d->inputs, scancode);
        d->key_state[i] &= ~m;
    }
}

void spice_display_send_pointer(SpiceDisplay *display, bool absolute,
                                int x, int y) {
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    if (d->inputs) {
        if (absolute)
            spice_inputs_position(d->inputs, x, y, d->channel_id,
                    d->mouse_button_mask);
        else
            spice_inputs_motion(d->inputs, x, y, d->mouse_button_mask);
    }
}

void spice_display_send_button(SpiceDisplay *display, int button, bool down) {
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);
    int button_mask;

    switch (button) {
    case SPICE_MOUSE_BUTTON_LEFT:
        button_mask = SPICE_MOUSE_BUTTON_MASK_LEFT;
        break;
    case SPICE_MOUSE_BUTTON_MIDDLE:
        button_mask = SPICE_MOUSE_BUTTON_MASK_MIDDLE;
        break;
    case SPICE_MOUSE_BUTTON_RIGHT:
        button_mask = SPICE_MOUSE_BUTTON_MASK_RIGHT;
        break;
    default:
        return;
    }

    if (down) {
        d->mouse_button_mask |= button_mask;
        if (d->inputs)
            spice_inputs_button_press(d->inputs, button, d->mouse_button_mask);
    } else {
        d->mouse_button_mask &= ~button_mask;
        if (d->inputs)
            spice_inputs_button_release(d->inputs, button,
                    d->mouse_button_mask);
    }
}

void spice_display_send_scroll(SpiceDisplay *display, int button, int count) {
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    if (d->inputs) {
        for (int i = 0; i < count; i++) {
            spice_inputs_button_press(d->inputs, button, d->mouse_button_mask);
            spice_inputs_button_release(d->inputs, button,
                    d->mouse_button_mask);
        }
    }
}

/* ---------------------------------------------------------------- */

static void disable_secondary_displays(SpiceMainChannel *channel, gpointer data) {
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "disable_secondary_displays");

    SpiceDisplay *display = data;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    spice_main_set_display_enabled(d->main, -1, FALSE);
    spice_main_set_display_enabled(d->main, 0, FALSE);
    spice_main_send_monitor_config(d->main);
}

static void primary_create(SpiceChannel *channel, gint format, gint width, gint height, gint stride, gint shmid, gpointer imgdata, gpointer data) {
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "primary_create");

    SpiceDisplay *display = data;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    // TODO: For now, don't do anything for secondary monitors
    if (get_display_id(display) > 0) {
        return;
    }

    d->format = format;
    d->stride = stride;
    d->width = width;
    d->height = height;
    d->data_origin = d->data = imgdata;
    d->convert = (format == SPICE_SURFACE_FMT_16_555 ||
            format == SPICE_SURFACE_FMT_16_565);
    if (d->convert)
        d->data = g_malloc0(height * stride);

    uiCallbackSettingsChanged(d->ctx, width, height);
}

static void primary_destroy(SpiceChannel *channel, gpointer data) {
    SpiceDisplay *display = SPICE_DISPLAY(data);
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    if (d->convert)
        g_free(d->data);
    d->format = 0;
    d->width  = 0;
    d->height = 0;
    d->stride = 0;
    d->data   = 0;
    d->data_origin = 0;
}

static void invalidate(SpiceChannel *channel,
                       gint x, gint y, gint w, gint h, gpointer data) {
    SpiceDisplay *display = data;

    if (!do_color_convert(display, x, y, w, h))
        return;

    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    if (x + w > d->width || y + h > d->height) {
        //__android_log_write(ANDROID_LOG_DEBUG, TAG, "Not drawing.");
    } else {
        uiCallbackInvalidate(d->ctx, x, y, w, h);
    }
}

static void disconnect_main(SpiceDisplay *display)
{
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    if (d->main == NULL)
        return;
    g_signal_handlers_disconnect_by_func(d->main, G_CALLBACK(update_mouse_mode),
                                         display);
    g_signal_handlers_disconnect_by_func(d->main, G_CALLBACK(disable_secondary_displays),
                                         display);
    d->main = NULL;
}

static void disconnect_display(SpiceDisplay *display)
{
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    if (d->display == NULL)
        return;
    g_signal_handlers_disconnect_by_func(d->display, G_CALLBACK(primary_create),
                                         display);
    g_signal_handlers_disconnect_by_func(d->display, G_CALLBACK(primary_destroy),
                                         display);
    g_signal_handlers_disconnect_by_func(d->display, G_CALLBACK(invalidate),
                                         display);
    d->display = NULL;
}

static void disconnect_cursor(SpiceDisplay *display)
{
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);

    if (d->cursor == NULL)
        return;
    g_signal_handlers_disconnect_by_func(d->cursor, G_CALLBACK(cursor_set),
                                         display);
    g_signal_handlers_disconnect_by_func(d->cursor, G_CALLBACK(cursor_hide),
                                         display);
    g_signal_handlers_disconnect_by_func(d->cursor, G_CALLBACK(cursor_reset),
                                         display);
    d->cursor = NULL;
}

static void channel_new(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "channel_new");

    SpiceDisplay *display = data;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);
    int id;

    g_object_get(channel, "channel-id", &id, NULL);
    if (SPICE_IS_MAIN_CHANNEL(channel)) {
        d->main = SPICE_MAIN_CHANNEL(channel);
        spice_g_signal_connect_object(channel, "main-mouse-update",
                                      G_CALLBACK(update_mouse_mode), display, 0);
        update_mouse_mode(channel, display);
        // TODO: For now, connect to this signal with a callback that disables
        // any secondary displays that crop up.
        g_signal_connect(channel, "main-agent-update",
                         G_CALLBACK(disable_secondary_displays), display);
        return;
    }

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (id != d->channel_id)
            return;
        d->display = channel;
        g_signal_connect(channel, "display-primary-create",
                         G_CALLBACK(primary_create), display);
        g_signal_connect(channel, "display-primary-destroy",
                         G_CALLBACK(primary_destroy), display);
        g_signal_connect(channel, "display-invalidate",
                         G_CALLBACK(invalidate), display);
        spice_channel_connect(channel);
        return;
    }

    if (SPICE_IS_CURSOR_CHANNEL(channel)) {
        if (id != d->channel_id)
            return;
        d->cursor = SPICE_CURSOR_CHANNEL(channel);
        g_signal_connect(channel, "cursor-set",
                         G_CALLBACK(cursor_set), display);
        g_signal_connect(channel, "cursor-hide",
                         G_CALLBACK(cursor_hide), display);
        g_signal_connect(channel, "cursor-reset",
                         G_CALLBACK(cursor_reset), display);
        spice_channel_connect(channel);
        return;
    }

    if (SPICE_IS_INPUTS_CHANNEL(channel)) {
        d->inputs = SPICE_INPUTS_CHANNEL(channel);
        spice_channel_connect(channel);
        return;
    }
}

static void channel_destroy(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    SpiceDisplay *display = data;
    SpiceDisplayPrivate *d = SPICE_DISPLAY_GET_PRIVATE(display);
    int id;

    g_object_get(channel, "channel-id", &id, NULL);
    SPICE_DEBUG("channel_destroy %d", id);

    if (SPICE_IS_MAIN_CHANNEL(channel)) {
        disconnect_main(display);
        return;
    }

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (id != d->channel_id)
            return;
        disconnect_display(display);
        return;
    }

    if (SPICE_IS_CURSOR_CHANNEL(channel)) {
        if (id != d->channel_id)
            return;
        disconnect_cursor(display);
        return;
    }

    if (SPICE_IS_INPUTS_CHANNEL(channel)) {
        d->inputs = NULL;
        return;
    }
}

/**
 * spice_display_new:
 * @session: a #SpiceSession
 * @id: the display channel ID to associate with #SpiceDisplay
 *
 * Returns: a new #SpiceDisplay.
 **/
SpiceDisplay *spice_display_new(struct spice_context *ctx, int id)
{
    SpiceDisplay *display;
    SpiceDisplayPrivate *d;
    GList *list;
    GList *it;

    display = g_object_new(SPICE_TYPE_DISPLAY, NULL);
    d = SPICE_DISPLAY_GET_PRIVATE(display);
    d->ctx = ctx;
    d->session = g_object_ref(ctx->session);
    d->channel_id = id;
    SPICE_DEBUG("channel_id:%d",d->channel_id);

    g_signal_connect(d->session, "channel-new",
                     G_CALLBACK(channel_new), display);
    g_signal_connect(d->session, "channel-destroy",
                     G_CALLBACK(channel_destroy), display);
    list = spice_session_get_channels(d->session);
    for (it = g_list_first(list); it != NULL; it = g_list_next(it)) {
        channel_new(d->session, it->data, (gpointer*)display);
    }
    g_list_free(list);

    return display;
}
