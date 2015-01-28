package org.olivearchive.vmnetx.android.input;

import android.view.MotionEvent;
import java.util.LinkedList;
import java.util.Queue;

import org.olivearchive.vmnetx.android.RemoteCanvas;
import org.olivearchive.vmnetx.android.RemoteCanvasActivity;

public class RelativeMouseHandler extends GestureHandler {
    static final String TAG = "RelativeMouseHandler";

    private final float sensitivity;
    private final boolean acceleration;

    // Queue which holds the last two MotionEvents which triggered onScroll
    private final Queue<Float> distXQueue = new LinkedList<Float>();
    private final Queue<Float> distYQueue = new LinkedList<Float>();

    /**
     * @param c
     */
    public RelativeMouseHandler(RemoteCanvasActivity va, RemoteCanvas v) {
        super(va, v);
        acceleration = activity.getAccelerationEnabled();
        sensitivity = activity.getSensitivity();
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.GestureDetector.SimpleOnGestureListener#onScroll(android.view.MotionEvent,
     *      android.view.MotionEvent, float, float)
     */
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // TODO: This is a workaround for Android 4.2
        boolean twoFingers = false;
        if (e1 != null)
            twoFingers = (e1.getPointerCount() > 1);
        if (e2 != null)
            twoFingers = twoFingers || (e2.getPointerCount() > 1);

        // onScroll called while scaling/swiping gesture is in effect. We ignore the event and pretend it was
        // consumed. This prevents the mouse pointer from flailing around while we are scaling.
        // Also, if one releases one finger slightly earlier than the other when scaling, it causes Android 
        // to stick a spiteful onScroll with a MASSIVE delta here. 
        // This would cause the mouse pointer to jump to another place suddenly.
        // Hence, we ignore onScroll after scaling until we lift all pointers up.
        if (twoFingers||inSwiping||inScaling||scalingJustFinished)
            return true;

        // If the gesture has just began, then don't allow a big delta to prevent
        // pointer jumps at the start of scrolling.
        if (!inScrolling) {
            inScrolling = true;
            distanceX = sign(distanceX);
            distanceY = sign(distanceY);
            distXQueue.clear();
            distYQueue.clear();
        }
        
        distXQueue.add(distanceX);
        distYQueue.add(distanceY);

        // Only after the first two events have arrived do we start using distanceX and Y
        // In order to effectively discard the last two events (which are typically unreliable
        // because of the finger lifting off).
        if (distXQueue.size() > 2) {
            distanceX = distXQueue.poll();
            distanceY = distYQueue.poll();
        } else {
            return true;
        }

        // Make distanceX/Y display density independent.
        distanceX = sensitivity * distanceX / displayDensity;
        distanceY = sensitivity * distanceY / displayDensity;
        
        // Submit the motion event.
        int dx = (int) getDelta(-distanceX);
        int dy = (int) getDelta(-distanceY);
        canvas.getPointer().processMotionEvent(dx, dy);
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.GestureDetector.SimpleOnGestureListener#onDown(android.view.MotionEvent)
     */
    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    protected boolean handleMouseActions(MotionEvent e) {
        // We can't generate motion events directly from the mouse because
        // Android presents the mouse as an absolute device whose motion
        // stops at the screen edge.  Respect scroll events, but otherwise
        // treat mouse events the same as touch events: the mouse button
        // must be down to engage the virtual touchpad.
        if (e.getActionMasked() == MotionEvent.ACTION_SCROLL)
            return super.handleMouseActions(e);
        return false;
    }

    @Override
    protected boolean updatePosition(int x, int y) {
        // no position to update
        return true;
    }

    private float getDelta(float distance) {
        // Compute the relative movement offset on the remote screen.
        float delta = (float) (distance *
                Math.cbrt(canvas.getViewport().getScale()));
        return fineCtrlScale(delta);
    }

    /**
     * Scale down delta when it is small. This will allow finer control
     * when user is making a small movement on touch screen.
     * Scale up delta when delta is big. This allows fast mouse movement when
     * user is flinging.
     * @param deltaX
     * @return
     */
    private float fineCtrlScale(float delta) {
        float sign = sign(delta);
        delta = Math.abs(delta);
        if (delta <= 15) {
            delta *= 0.75;
        } else if (acceleration && delta <= 70 ) {
            delta *= delta/20;
        } else if (acceleration) {
            delta *= 4.5;
        }
        return sign * delta;
    }
}
