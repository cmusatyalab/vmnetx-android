/*
   Copyright (C) 2014 Carnegie Mellon University
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
#include <glib/gi18n.h>
#include <android/log.h>

#include <sys/stat.h>
#include "android-spice-widget.h"
#include "spice-audio.h"
#include "android-io.h"
#include "android-spicy.h"
#include "android-service.h"

#define TAG "vmnetx-spicy"

G_DEFINE_TYPE (SpiceWindow, spice_window, G_TYPE_OBJECT);

static void connection_destroy(spice_connection *conn);

void spice_window_class_init (SpiceWindowClass *klass) {}

void spice_window_init (SpiceWindow *self) {}

/* ------------------------------------------------------------------ */

static SpiceWindow *create_spice_window(spice_connection *conn, SpiceChannel *channel, int id)
{
    SpiceWindow *win;

    win = g_new0 (SpiceWindow, 1);
    win->conn = conn;

    win->spice = spice_display_new(conn->ctx, conn->session, id);
    conn->ctx->display = win->spice;
    return win;
}

static void destroy_spice_window(SpiceWindow *win)
{
    if (win == NULL)
        return;

    SPICE_DEBUG("destroy window");
    g_object_unref(win);
}

/* ------------------------------------------------------------------ */

static void channel_open_fd(SpiceChannel *channel, gint with_tls,
                            gpointer data)
{
    spice_connection *conn = data;
    uiCallbackGetFd(conn->ctx, channel);
}

static void main_channel_event(SpiceChannel *channel, SpiceChannelEvent event,
                               gpointer data)
{
    spice_connection *conn = data;

    switch (event) {
    case SPICE_CHANNEL_CLOSED:
        /* this event is only sent if the channel was succesfully opened before */
        g_message("main channel: closed");
        connection_disconnect(conn);
        break;
    case SPICE_CHANNEL_ERROR_IO:
        connection_disconnect(conn);
        break;
    case SPICE_CHANNEL_ERROR_TLS:
    case SPICE_CHANNEL_ERROR_LINK:
    case SPICE_CHANNEL_ERROR_CONNECT:
        g_message("main channel: failed to connect");
        connection_disconnect(conn);
        break;
    case SPICE_CHANNEL_ERROR_AUTH:
        g_warning("main channel: auth failure (wrong password?)");
        connection_disconnect(conn);
        break;
    default:
        break;
    }
}

static void channel_new(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    spice_connection *conn = data;
    int id;

    g_object_get(channel, "channel-id", &id, NULL);
    conn->channels++;
    SPICE_DEBUG("new channel (#%d)", id);

    g_signal_connect(channel, "open-fd",
                     G_CALLBACK(channel_open_fd), conn);

    if (SPICE_IS_MAIN_CHANNEL(channel)) {
        SPICE_DEBUG("new main channel");
        conn->main = SPICE_MAIN_CHANNEL(channel);
        g_signal_connect(channel, "channel-event",
                         G_CALLBACK(main_channel_event), conn);
    }

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (id >= G_N_ELEMENTS(conn->wins))
            return;
        if (conn->wins[id] != NULL)
            return;
        SPICE_DEBUG("new display channel (#%d)", id);
        conn->wins[id] = create_spice_window(conn, channel, id);
    }

    if (SPICE_IS_PLAYBACK_CHANNEL(channel)) {
        SPICE_DEBUG("new audio channel");
        conn->audio = spice_audio_get(s, NULL);
    }
}

static void channel_destroy(SpiceSession *s, SpiceChannel *channel, gpointer data)
{
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "channel_destroy called");

    spice_connection *conn = data;
    int id;

    g_object_get(channel, "channel-id", &id, NULL);
    if (SPICE_IS_MAIN_CHANNEL(channel)) {
        SPICE_DEBUG("zap main channel");
        conn->main = NULL;
    }

    if (SPICE_IS_DISPLAY_CHANNEL(channel)) {
        if (id >= G_N_ELEMENTS(conn->wins))
            return;
        if (conn->wins[id] == NULL)
            return;
        SPICE_DEBUG("zap display channel (#%d)", id);
        destroy_spice_window(conn->wins[id]);
        conn->wins[id] = NULL;
    }

    if (SPICE_IS_PLAYBACK_CHANNEL(channel)) {
        SPICE_DEBUG("zap audio channel");
    }

    conn->channels--;
    //__android_log_print(ANDROID_LOG_DEBUG, TAG, "Number of channels: %d", conn->channels);
    if (conn->channels > 0) {
        return;
    }

    connection_destroy(conn);
}

spice_connection *connection_new(struct spice_context *ctx)
{
    spice_connection *conn;

    conn = g_new0(spice_connection, 1);
    conn->ctx = ctx;
    conn->session = spice_session_new();
    g_signal_connect(conn->session, "channel-new",
                     G_CALLBACK(channel_new), conn);
    g_signal_connect(conn->session, "channel-destroy",
                     G_CALLBACK(channel_destroy), conn);

    ctx->connections++;
    SPICE_DEBUG("%s (%d)", __FUNCTION__, ctx->connections);
    return conn;
}

void connection_connect(spice_connection *conn)
{
    conn->disconnecting = false;
    spice_session_open_fd(conn->session, -1);
}

void connection_disconnect(spice_connection *conn)
{
    if (conn->disconnecting)
        return;
    conn->disconnecting = true;
    spice_session_disconnect(conn->session);
}

static void connection_destroy(spice_connection *conn)
{
    struct spice_context *ctx = conn->ctx;
    //__android_log_write(ANDROID_LOG_DEBUG, TAG, "connection_destroy called");
    g_object_unref(conn->session);
    free(conn);

    ctx->connections--;
    SPICE_DEBUG("%s (%d)", __FUNCTION__, ctx->connections);
    if (!ctx->connections) {
        uiCallbackDisconnect(ctx);
    }
}
