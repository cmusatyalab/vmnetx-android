/**
 * Copyright (C) 2014-2015 Carnegie Mellon University
 * Copyright (C) 2012-2014 Iordan Iordanov
 * Copyright (C) 2010 Michael A. MacDonald
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

import org.olivearchive.vmnetx.android.RemoteCanvas;
import org.olivearchive.vmnetx.android.RemoteCanvasActivity;
import org.olivearchive.vmnetx.android.Utils;

public class AbsoluteMouseHandler extends GestureHandler {
    static final String TAG = "AbsoluteMouseHandler";

    /**
     * Divide stated fling velocity by this amount to get initial velocity
     * per pan interval
     */
    static final float FLING_FACTOR = 8;
    
    /**
     * @param c
     */
    public AbsoluteMouseHandler(RemoteCanvasActivity va, RemoteCanvas v) {
        super(va, v);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.GestureDetector.SimpleOnGestureListener#onLongPress(android.view.MotionEvent)
     */
    @Override
    public void onLongPress(MotionEvent e) {

        // If we've performed a right/middle-click and the gesture is not over yet, do not start drag mode.
        if (numPointersSeen > 1)
            return;
        
        Utils.performLongPressHaptic(canvas);
        canvas.displayShortToastMessage("Panning");
        endDragModeAndScrolling();
        panMode = true;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.view.GestureDetector.SimpleOnGestureListener#onScroll(android.view.MotionEvent,
     *      android.view.MotionEvent, float, float)
     */
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        RemotePointer p = canvas.getPointer();

        // onScroll called while scaling/swiping gesture is in effect. We ignore the event and pretend it was
        // consumed. This prevents the mouse pointer from flailing around while we are scaling.
        // Also, if one releases one finger slightly earlier than the other when scaling, it causes Android 
        // to stick a spiteful onScroll with a MASSIVE delta here. 
        // This would cause the mouse pointer to jump to another place suddenly.
        // Hence, we ignore onScroll after scaling until we lift all pointers up.
        boolean twoFingers = false;
        if (e1 != null)
            twoFingers = (e1.getPointerCount() > 1);
        if (e2 != null)
            twoFingers = twoFingers || (e2.getPointerCount() > 1);

        if (twoFingers||inSwiping||inScaling||scalingJustFinished)
            return true;

        if (dragModeButton == 0) {
            dragModeButton = MotionEvent.BUTTON_PRIMARY;
            updatePosition(e1);
            p.processButtonEvent(e1.getDeviceId(), MotionEvent.BUTTON_PRIMARY);
        } else {
            updatePosition(e2);
            p.processButtonEvent(e2.getDeviceId(), MotionEvent.BUTTON_PRIMARY);
        }
        return true;
    }
}

