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

import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;

import org.olivearchive.vmnetx.android.RemoteCanvas;
import org.olivearchive.vmnetx.android.RemoteCanvasActivity;
import org.olivearchive.vmnetx.android.Utils;

/**
 * An input handler that uses GestureDetector to detect standard gestures
 * in touch events
 * 
 * @author Michael A. MacDonald
 */
abstract public class GestureHandler
        extends GestureDetector.SimpleOnGestureListener
        implements OnScaleGestureListener {
    @SuppressWarnings("unused")
    private static final String TAG = "GestureHandler";

    protected final GestureDetector gestures;
    protected final ScaleGestureDetector scaleGestures;

    /**
     * Handles to the RemoteCanvas view and RemoteCanvasActivity activity.
     */
    protected final RemoteCanvas canvas;
    protected final RemoteCanvasActivity activity;
    
    // This is the initial "focal point" of the gesture (between the two fingers).
    private float xInitialFocus;
    private float yInitialFocus;
    
    // This is the final "focal point" of the gesture (between the two fingers).
    private float xCurrentFocus;
    private float yCurrentFocus;
    private float yPreviousFocus;
    
    // These variables record whether there was a two-finger swipe performed up or down.
    protected boolean inSwiping         = false;
    private boolean twoFingerSwipeUp    = false;
    private boolean twoFingerSwipeDown  = false;
    
    // The variables which indicates how many scroll events to send per swipe 
    // event and the maximum number to send at one time.
    private long swipeSpeed = 1;
    private final int maxSwipeSpeed = 20;
    // If swipe events are registered once every baseSwipeTime miliseconds, then
    // swipeSpeed will be one. If more often, swipe-speed goes up, if less, down.
    private final long baseSwipeTime = 600;
    // This is how far the swipe has to travel before a swipe event is generated.
    private final float baseSwipeDist = 40.f;
    
    protected boolean inScrolling         = false;
    protected boolean inScaling           = false;
    protected boolean scalingJustFinished = false;
    // The minimum distance a scale event has to traverse the FIRST time before scaling starts.
    private final double minScaleFactor = 0.1;
    
    // What the display density is.
    protected final float displayDensity;
    
    /**
     * In the drag modes, we process mouse events without sending them through
     * the gesture detector.
     */
    protected boolean panMode        = false;
    protected boolean dragMode       = false;
    protected boolean rightDragMode  = false;
    protected boolean middleDragMode = false;
    protected float   dragX, dragY;

    // How many pointers have seen ACTION_DOWN events.
    protected int numPointersSeen;
    
    GestureHandler(RemoteCanvasActivity c, RemoteCanvas v)
    {
        activity = c;
        canvas = v;
        gestures = new GestureDetector(c, this);
        gestures.setOnDoubleTapListener(this);
        scaleGestures = new ScaleGestureDetector(c, this);
        displayDensity = canvas.getDisplayDensity();
    }

    protected int getCanvasTop() {
        int[] location = new int[2];
        canvas.getLocationOnScreen(location);
        return location[1];
    }

    /**
     * Function to get appropriate X coordinate from motion event for this input handler.
     * @return the appropriate X coordinate.
     */
    protected int getX (MotionEvent e) {
        float scale = canvas.getScale();
        return (int)(canvas.getAbsoluteX() + e.getX() / scale);
    }

    /**
     * Function to get appropriate Y coordinate from motion event for this input handler.
     * @return the appropriate Y coordinate.
     */
    protected int getY (MotionEvent e) {
        float scale = canvas.getScale();
        return (int)(canvas.getAbsoluteY() + (e.getY() - 1.f * getCanvasTop()) / scale);
    }

    /**
     * Handles actions performed by a mouse.
     * @param e touch or generic motion event
     * @return
     */
    protected boolean handleMouseActions (MotionEvent e) {
        final int action = e.getActionMasked();
        final int bstate = e.getButtonState();
        RemotePointer p  = canvas.getPointer();
        float scale = canvas.getScale();
        int x = (int)(canvas.getAbsoluteX() +  e.getX()                         / scale);
        int y = (int)(canvas.getAbsoluteY() + (e.getY() - 1.f * getCanvasTop()) / scale);

        switch (action) {
        // If a mouse button was pressed or mouse was moved.
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE:
            switch (bstate) {
            case MotionEvent.BUTTON_PRIMARY:
                canvas.panToMouse();
                return p.processPointerEvent(x, y, action, true, false, false);
            case MotionEvent.BUTTON_SECONDARY:
                canvas.panToMouse();
                return p.processPointerEvent(x, y, action, true, true, false);
            case MotionEvent.BUTTON_TERTIARY:
                canvas.panToMouse();
                return p.processPointerEvent(x, y, action, true, false, true);
            }
            break;
        // If a mouse button was released.
        case MotionEvent.ACTION_UP:
            switch (bstate) {
            case 0:
                if (e.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE) {
                    break;
                }
            case MotionEvent.BUTTON_PRIMARY:
            case MotionEvent.BUTTON_SECONDARY:
            case MotionEvent.BUTTON_TERTIARY:
                canvas.panToMouse();
                return p.processPointerEvent(x, y, action, false, false, false);
            }
            break;
        // If the mouse wheel was scrolled.
        case MotionEvent.ACTION_SCROLL:
            float vscroll = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (vscroll < 0) {
                p.processScrollEvent(RemotePointer.BUTTON_SCROLL_DOWN, (int) -vscroll);
            } else if (vscroll > 0) {
                p.processScrollEvent(RemotePointer.BUTTON_SCROLL_UP, (int) vscroll);
            } else
                return false;
            break;
        // If the mouse was moved OR as reported, some external mice trigger this when a
        // mouse button is pressed as well, so we check bstate here too.
        case MotionEvent.ACTION_HOVER_MOVE:
            canvas.panToMouse();
            switch (bstate) {
            case MotionEvent.BUTTON_PRIMARY:
                return p.processPointerEvent(x, y, action, true, false, false);
            case MotionEvent.BUTTON_SECONDARY:
                return p.processPointerEvent(x, y, action, true, true, false);
            case MotionEvent.BUTTON_TERTIARY:
                return p.processPointerEvent(x, y, action, true, false, true);
            default:
                return p.processPointerEvent(x, y, action, false, false, false);
            }
        }
        
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.GestureDetector.SimpleOnGestureListener#onSingleTapConfirmed(android.view.MotionEvent)
     */
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        RemotePointer p  = canvas.getPointer();
        final int action = e.getActionMasked();
        p.processPointerEvent(getX(e), getY(e), action, true, false, false);
        SystemClock.sleep(50);
        p.processPointerEvent(getX(e), getY(e), action, false, false, false);
        canvas.panToMouse();
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.GestureDetector.SimpleOnGestureListener#onDoubleTap(android.view.MotionEvent)
     */
    @Override
    public boolean onDoubleTap (MotionEvent e) {
        RemotePointer p  = canvas.getPointer();
        final int action = e.getActionMasked();
        
        p.processPointerEvent(getX(e), getY(e), action, true, false, false);
        SystemClock.sleep(50);
        p.processPointerEvent(getX(e), getY(e), action, false, false, false);
        SystemClock.sleep(50);
        p.processPointerEvent(getX(e), getY(e), action, true, false, false);
        SystemClock.sleep(50);
        p.processPointerEvent(getX(e), getY(e), action, false, false, false);
        canvas.panToMouse();
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.GestureDetector.SimpleOnGestureListener#onLongPress(android.view.MotionEvent)
     */
    @Override
    public void onLongPress(MotionEvent e) {
        RemotePointer p = canvas.getPointer();

        // If we've performed a right/middle-click and the gesture is not over yet, do not start drag mode.
        if (numPointersSeen > 1)
            return;
        
        Utils.performLongPressHaptic(canvas);
        dragMode = true;
        p.processPointerEvent(getX(e), getY(e), e.getActionMasked(), true, false, false);
    }

    protected boolean endDragModesAndScrolling () {
        canvas.inScrolling = false;
        panMode               = false;
        inScaling             = false;
        inSwiping             = false;
        inScrolling           = false;
        if (dragMode || rightDragMode || middleDragMode) {
            dragMode          = false;
            rightDragMode     = false;
            middleDragMode    = false;
            return true;
        } else
            return false;
    }

    /**
     * Modify the event so that the mouse goes where we specify.
     * @param e event to be modified.
     * @param x new x coordinate.
     * @param y new y coordinate.
     */
    private void setEventCoordinates(MotionEvent e, float x, float y) {
        e.setLocation(x, y);
    }

    public boolean onTouchEvent(MotionEvent e) {
        final int action     = e.getActionMasked();
        final int index      = e.getActionIndex();
        final int pointerID  = e.getPointerId(index);
        RemotePointer p = canvas.getPointer();
        
        // Handle and consume actions performed by a (e.g. USB or bluetooth) mouse.
        if (handleMouseActions (e))
            return true;

        if (action == MotionEvent.ACTION_UP) {
            // Turn filtering back on and invalidate to make things pretty.
            canvas.bitmapData.drawable._defaultPaint.setFilterBitmap(true);
            canvas.invalidate();
        }

        switch (pointerID) {

        case 0:
            switch (action) {
            case MotionEvent.ACTION_DOWN:
                // We have put down first pointer on the screen, so we can reset the state of all click-state variables.
                // Permit right-clicking and sending mouse-down event on
                // long-tap again.
                numPointersSeen = 1;
                // Cancel any effect of scaling having "just finished" (e.g. ignoring scrolling).
                scalingJustFinished = false;
                // Cancel drag modes and scrolling.
                endDragModesAndScrolling();
                canvas.inScrolling = true;
                // If we are manipulating the desktop, turn off bitmap filtering for faster response.
                canvas.bitmapData.drawable._defaultPaint.setFilterBitmap(false);
                dragX = e.getX();
                dragY = e.getY();
                break;
            case MotionEvent.ACTION_UP:
                // If any drag modes were going on, end them and send a mouse up event.
                if (endDragModesAndScrolling())
                    return p.processPointerEvent(getX(e), getY(e), action, false, false, false);
                break;
            case MotionEvent.ACTION_MOVE:
                // Send scroll up/down events if swiping is happening.
                if (panMode) {
                    float scale = canvas.getScale();
                    canvas.pan(-(int)((e.getX() - dragX)*scale), -(int)((e.getY() - dragY)*scale));
                    dragX = e.getX();
                    dragY = e.getY();
                    return true;
                } else if (dragMode) {
                    canvas.panToMouse();
                    return p.processPointerEvent(getX(e), getY(e), action, true, false, false);
                } else if (rightDragMode) {
                    canvas.panToMouse();
                    return p.processPointerEvent(getX(e), getY(e), action, true, true, false);
                } else if (middleDragMode) {
                    canvas.panToMouse();
                    return p.processPointerEvent(getX(e), getY(e), action, true, false, true);
                } else if (inSwiping) {
                    // Save the coordinates and restore them afterward.
                    float x = e.getX();
                    float y = e.getY();
                    // Set the coordinates to where the swipe began (i.e. where scaling started).
                    setEventCoordinates(e, xInitialFocus, yInitialFocus);
                    if (twoFingerSwipeUp) {
                        p.processScrollEvent(RemotePointer.BUTTON_SCROLL_UP, (int) Math.min(swipeSpeed, maxSwipeSpeed));
                    } else if (twoFingerSwipeDown) {
                        p.processScrollEvent(RemotePointer.BUTTON_SCROLL_DOWN, (int) Math.min(swipeSpeed, maxSwipeSpeed));
                    }
                    // Restore the coordinates so that onScale doesn't get all muddled up.
                    setEventCoordinates(e, x, y);
                }
            }
            break;

        case 1:
            switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                // Here we only prepare for the second click, which we perform on ACTION_POINTER_UP for pointerID==1.
                endDragModesAndScrolling();
                // Prohibit sending mouse-down event on long-tap, and permit
                // right-clicking.
                numPointersSeen = 2;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (!inSwiping && !inScaling && numPointersSeen <= 2) {
                    // If user taps with a second finger while first finger is down, then we treat this as
                    // a right mouse click, but we only effect the click when the second pointer goes up.
                    // If the user taps with a second and third finger while the first 
                    // finger is down, we treat it as a middle mouse click. We ignore the lifting of the
                    // second index when the third index has gone down
                    // to prevent inadvertent right-clicks when a middle click has been performed.
                    p.processPointerEvent(getX(e), getY(e), action, true, true, false);
                    // Enter right-drag mode.
                    rightDragMode = true;
                    // Now the event must be passed on to the parent class in order to 
                    // end scaling as it was certainly started when the second pointer went down.
                }
                break;
            }
            break;

        case 2:
            switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                // Prevent the right-click from firing simultaneously as a middle button click.
                numPointersSeen = 3;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (!inSwiping && !inScaling && numPointersSeen <= 3) {
                    p.processPointerEvent(getX(e), getY(e), action, true, false, true);
                    // Enter middle-drag mode.
                    middleDragMode      = true;
                }
                break;
            }
            break;

        case 3:
            switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                // Prevent the gesture from firing as a middle button click.
                numPointersSeen = 4;
                if (!inSwiping && !inScaling) {
                    activity.setFullScreen(false);
                }
            }
            break;
        }
        
        scaleGestures.onTouchEvent(e);
        return gestures.onTouchEvent(e);
    }

    /* (non-Javadoc)
     * @see android.view.ScaleGestureDetector.OnScaleGestureListener#onScale(android.view.ScaleGestureDetector)
     */
    @Override
    public boolean onScale(ScaleGestureDetector detector) {

        boolean consumed = true;

        // Get the current focus.
        xCurrentFocus = detector.getFocusX();
        yCurrentFocus = detector.getFocusY();
        
        // If we haven't started scaling yet, we check whether a swipe is being performed.
        // The arbitrary fudge factor may not be the best way to set a tolerance...
        if (!inScaling) {
            
            // Start swiping mode only after we've moved away from the initial focal point some distance.
            if (!inSwiping) {
                if ( (yCurrentFocus < (yInitialFocus - baseSwipeDist)) ||
                     (yCurrentFocus > (yInitialFocus + baseSwipeDist)) ) {
                    inSwiping      = true;
                    yPreviousFocus = yInitialFocus;
                }
            }
            
            // If in swiping mode, indicate a swipe at regular intervals.
            if (inSwiping) {
                twoFingerSwipeUp    = false;                    
                twoFingerSwipeDown  = false;
                if (yCurrentFocus < (yPreviousFocus - baseSwipeDist)) {
                    twoFingerSwipeDown   = true;
                    yPreviousFocus = yCurrentFocus;
                } else if (yCurrentFocus > (yPreviousFocus + baseSwipeDist)) {
                    twoFingerSwipeUp     = true;
                    yPreviousFocus = yCurrentFocus;
                } else {
                    consumed           = false;
                }
                // The faster we swipe, the faster we traverse the screen, and hence, the 
                // smaller the time-delta between consumed events. We take the reciprocal
                // obtain swipeSpeed. If it goes to zero, we set it to at least one.
                long elapsedTime = detector.getTimeDelta();
                if (elapsedTime < 10) elapsedTime = 10;
                
                swipeSpeed = baseSwipeTime/elapsedTime;
                if (swipeSpeed == 0)  swipeSpeed = 1;
                //if (consumed)        android.util.Log.d(TAG,"Current swipe speed: " + swipeSpeed);
            }
        }
        
        if (!inSwiping) {
            if ( !inScaling && Math.abs(1.0 - detector.getScaleFactor()) < minScaleFactor ) {
                //android.util.Log.i(TAG,"Not scaling due to small scale factor.");
                consumed = false;
            }

            if (consumed) {
                inScaling = true;
                //android.util.Log.i(TAG,"Adjust scaling " + detector.getScaleFactor());
                if (canvas != null && canvas.scaling != null)
                    canvas.scaling.adjust(activity, detector.getScaleFactor(), xCurrentFocus, yCurrentFocus);
            }
        }
        return consumed;
    }

    /* (non-Javadoc)
     * @see android.view.ScaleGestureDetector.OnScaleGestureListener#onScaleBegin(android.view.ScaleGestureDetector)
     */
    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {

        xInitialFocus = detector.getFocusX();
        yInitialFocus = detector.getFocusY();
        inScaling           = false;
        scalingJustFinished = false;
        // Cancel any swipes that may have been registered last time.
        inSwiping           = false;
        twoFingerSwipeUp    = false;                    
        twoFingerSwipeDown  = false;
        //android.util.Log.i(TAG,"scale begin ("+xInitialFocus+","+yInitialFocus+")");
        return true;
    }

    /* (non-Javadoc)
     * @see android.view.ScaleGestureDetector.OnScaleGestureListener#onScaleEnd(android.view.ScaleGestureDetector)
     */
    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        //android.util.Log.i(TAG,"scale end");
        inScaling = false;
        inSwiping = false;
        scalingJustFinished = true;
    }
    
    /**
     * Returns the sign of the given number.
     * @param number the given number
     * @return -1 for negative and 1 for positive.
     */
    protected float sign (float number) {
        return (number > 0) ? 1 : -1;
    }
}