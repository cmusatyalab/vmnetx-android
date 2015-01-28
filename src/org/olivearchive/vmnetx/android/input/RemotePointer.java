package org.olivearchive.vmnetx.android.input;

import org.olivearchive.vmnetx.android.RemoteCanvas;
import org.olivearchive.vmnetx.android.SpiceCommunicator;
import org.olivearchive.vmnetx.android.Viewport;

public class RemotePointer {
    //private static final String TAG = "RemotePointer";

    public static final int BUTTON_SCROLL_UP	= 4;
    public static final int BUTTON_SCROLL_DOWN	= 5;

    private final RemoteCanvas canvas;
    private final SpiceCommunicator spice;
    private final ModifierState buttons;

    /**
     * Indicates where the mouse pointer is located.
     */
    private int mouseX, mouseY;

    public RemotePointer (SpiceCommunicator s, RemoteCanvas c) {
        spice = s;
        mouseX = spice.framebufferWidth()/2;
        mouseY = spice.framebufferHeight()/2;
        canvas = c;
        buttons = new ModifierState();
    }
    
    public int getX() {
        return mouseX;
    }

    public int getY() {
        return mouseY;
    }

    public boolean processPointerEvent(int x, int y) {
        if (spice.isInNormalProtocol()) {
            Viewport viewport = canvas.getViewport();
            viewport.invalidateMousePosition();
            mouseX = x;
            mouseY = y;
            if ( mouseX < 0) mouseX=0;
            else if ( mouseX >= spice.framebufferWidth())  mouseX = spice.framebufferWidth()  - 1;
            if ( mouseY < 0) mouseY=0;
            else if ( mouseY >= spice.framebufferHeight()) mouseY = spice.framebufferHeight() - 1;
            viewport.invalidateMousePosition();

            spice.writePointerEvent(mouseX, mouseY);
            return true;
        }
        return false;
    }

    public boolean processMotionEvent(int dx, int dy) {
        if (spice.isInNormalProtocol()) {
            spice.writeMotionEvent(dx, dy);
            return true;
        }
        return false;
    }

    public boolean processButtonEvent(int deviceID, int buttonState) {
        buttons.getDeviceState(deviceID).set(buttonState);
        if (spice.isInNormalProtocol()) {
            spice.updateButtons(buttons.getModifiers());
            return true;
        }
        return false;
    }

    public boolean processScrollEvent(int button, int count) {
        if (spice.isInNormalProtocol()) {
            spice.writeScrollEvent(button, count);
            return true;
        }
        return false;
    }
}
