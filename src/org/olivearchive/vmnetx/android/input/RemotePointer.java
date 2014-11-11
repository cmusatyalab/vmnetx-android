package org.olivearchive.vmnetx.android.input;

import org.olivearchive.vmnetx.android.RemoteCanvas;
import org.olivearchive.vmnetx.android.SpiceCommunicator;

import android.view.MotionEvent;

public abstract class RemotePointer {
    
    /**
     * Current and previous state of "mouse" buttons
     */
    protected int pointerMask = 0;
    protected int prevPointerMask = 0;

    protected RemoteCanvas vncCanvas;
    protected SpiceCommunicator spice;

    /**
     * Indicates where the mouse pointer is located.
     */
    protected int mouseX, mouseY;

    public RemotePointer (SpiceCommunicator s, RemoteCanvas v) {
        spice = s;
        mouseX = spice.framebufferWidth()/2;
        mouseY = spice.framebufferHeight()/2;
        vncCanvas = v;
    }
    
    abstract public int getX();
    abstract public int getY();
    abstract public void setX(int newX);
    abstract public void setY(int newY);
    abstract public void warpMouse(int x, int y);
    abstract public void mouseFollowPan();
    abstract public boolean processPointerEvent(MotionEvent evt, boolean downEvent);
    abstract public boolean processPointerEvent(MotionEvent evt, boolean downEvent, 
                                       boolean useRightButton, boolean useMiddleButton, boolean useScrollButton, int direction);
    abstract public boolean processPointerEvent(MotionEvent evt, boolean downEvent, 
                                       boolean useRightButton, boolean useMiddleButton);
    abstract public boolean processPointerEvent(MotionEvent evt, boolean downEvent, boolean useRightButton);
    abstract public boolean processPointerEvent(int x, int y, int action, int modifiers, boolean mouseIsDown, boolean useRightButton);
    
    abstract public boolean processPointerEvent(int x, int y, int action, int modifiers, boolean mouseIsDown, boolean useRightButton,
                                        boolean useMiddleButton, boolean useScrollButton, int direction);
}
