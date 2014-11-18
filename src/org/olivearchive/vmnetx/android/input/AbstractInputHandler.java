/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2009 Michael A. MacDonald
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

package org.olivearchive.vmnetx.android.input;

import android.view.MotionEvent;

/**
 * The RemoteCanvasActivity has several different ways of handling input from the touchscreen,
 * keyboard, buttons and trackball.  These will be represented by different implementations
 * of this interface.  Putting the different modes in different classes
 * will keep the logic clean.  The relevant Activity callbacks in RemoteCanvasActivity
 * are forwarded to methods in AbstractInputHandler.
 * <p>
 * It is expected that the implementations will be contained within
 * RemoteCanvasActivity, so they can do things like super.RemoteCanvasActivity.onXXX to invoke
 * default behavior.
 * @author Michael A. MacDonald
 *
 */
public interface AbstractInputHandler {
    /* (non-Javadoc)
     * @see android.app.Activity#onTouchEvent(android.view.MotionEvent)
     */
    boolean onTouchEvent(MotionEvent evt);
}
