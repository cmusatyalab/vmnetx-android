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

#include <stdbool.h>
#include "android-spice.h"

void _assert_on_main_loop_thread(const char *caller);
#define assert_on_main_loop_thread() _assert_on_main_loop_thread(__func__)

/* These can only be called from the thread running the glib main loop. */
void uiCallbackGetFd (struct spice_context *ctx, SpiceChannel *channel);
void uiCallbackInvalidate (struct spice_context *ctx, gint x, gint y, gint w, gint h);
void uiCallbackSettingsChanged (struct spice_context *ctx, gint width, gint height);
void uiCallbackMouseMode (struct spice_context *ctx, bool absolute_mouse);
void uiCallbackCursorConfig (struct spice_context *ctx, bool shown, uint32_t *bitmap, int w, int h, int hot_x, int hot_y);
void uiCallbackDisconnect (struct spice_context *ctx);
