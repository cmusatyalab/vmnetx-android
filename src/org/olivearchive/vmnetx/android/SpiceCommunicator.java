package org.olivearchive.vmnetx.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;

import org.olivearchive.vmnetx.android.input.KeyboardMapper;
import org.olivearchive.vmnetx.android.input.RemoteKeyboard;
import org.olivearchive.vmnetx.android.input.RemoteSpicePointer;
import com.gstreamer.*;

public class SpiceCommunicator implements KeyboardMapper.KeyProcessingListener {
    private final static String TAG = "SpiceCommunicator";

    private native int  SpiceClientConnect (String password);
    private native void SpiceClientDisconnect ();
    private native void SpiceButtonEvent (int x, int y, int metaState, int pointerMask);
    private native void SpiceKeyEvent (boolean keyDown, int virtualKeyCode);
    private native void SpiceUpdateBitmap (Bitmap bitmap, int x, int y, int w, int h);
    private native void SpiceRequestResolution (int x, int y);
    private native void SpiceSetFd (long cookie, int fd);
    
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("spice");
    }
    
    final static int VK_CONTROL = 0x11;
    final static int VK_LCONTROL = 0xA2;
    final static int VK_RCONTROL = 0xA3;
    final static int VK_LMENU = 0xA4;
    final static int VK_RMENU = 0xA5;
    final static int VK_LSHIFT = 0xA0;
    final static int VK_RSHIFT = 0xA1;
    final static int VK_LWIN = 0x5B;
    final static int VK_RWIN = 0x5C;
    final static int VK_EXT_KEY = 0x00000100;

    private int metaState = 0;
    
    private RemoteCanvas canvas;
    private Handler handler;
    private ConnectionBean connection;

    private int width = 0;
    private int height = 0;
    
    private boolean wantAbsoluteMouse = false;

    private boolean isInNormalProtocol = false;
    
    private SpiceThread spicethread = null;

    public SpiceCommunicator (Context context, RemoteCanvas canvas, Handler handler, ConnectionBean connection) {
        this.canvas = canvas;
        this.handler = handler;
        this.connection = connection;
        try {
            GStreamer.init(context);
        } catch (Exception e) {
            e.printStackTrace();
            canvas.displayShortToastMessage(e.getMessage());
        }
    }

    public void connect() {
        spicethread = new SpiceThread();
        spicethread.start();
    }
    
    public void disconnect() {
        SpiceClientDisconnect();
        try {spicethread.join(3000);} catch (InterruptedException e) {}
    }

    private class SpiceThread extends Thread {
        public void run() {
            SpiceClientConnect (connection.getToken());
            android.util.Log.e(TAG, "SpiceClientConnect returned.");

            // If we've exited SpiceClientConnect, the connection was
            // interrupted or was never established.
            handler.sendEmptyMessage(Constants.SPICE_CONNECT_FAILURE);
        }
    }
    
    private class ConnectThread extends Thread {
        private long cookie;

        public ConnectThread(long cookie) {
            this.cookie = cookie;
        }

        public void run() {
            try {
                int fd = new ViewerConnectionProcessor(connection.getAddress(),
                                                       Integer.toString(connection.getPort()),
                                                       connection.getToken()).connect();
                SpiceSetFd(cookie, fd);
            } catch (ProtocolException e) {
                android.util.Log.e(TAG, "Get FD failed", e);
                disconnect();
            }
        }
    }

    private void sendMouseEvent (int x, int y, int metaState, int pointerMask) {
        SpiceButtonEvent(x, y, metaState, pointerMask);
    }

    private void sendKeyEvent (boolean keyDown, int virtualKeyCode) {
        SpiceKeyEvent(keyDown, virtualKeyCode);
    }
    
    public void updateBitmap (Bitmap bitmap, int x, int y, int w, int h) {
        SpiceUpdateBitmap(bitmap, x, y, w, h);
    }
    
    /* Callbacks from jni */
    private void OnGetFd(long cookie) {
        new ConnectThread(cookie).start();
    }

    private void OnSettingsChanged(int inst, int width, int height, int bpp) {
        this.width = width;
        this.height = height;
        canvas.OnSettingsChanged(width, height, bpp);
    }

    private void OnGraphicsUpdate(int inst, int x, int y, int width, int height) {
        canvas.OnGraphicsUpdate(x, y, width, height);
    }

    private void OnCursorConfig(boolean absoluteMouse) {
        synchronized (this) {
            wantAbsoluteMouse = absoluteMouse;
        }
        canvas.OnCursorConfig();
    }

    public int framebufferWidth() {
        return width;
    }

    public int framebufferHeight() {
        return height;
    }

    public void setIsInNormalProtocol(boolean state) {
        isInNormalProtocol = state;        
    }
    
    public boolean isInNormalProtocol() {
        return isInNormalProtocol;
    }

    public synchronized boolean getAbsoluteMouse() {
        return wantAbsoluteMouse;
    }

    public void writePointerEvent(int x, int y, int metaState, int pointerMask) {
        this.metaState = metaState; 
        if ((pointerMask & RemoteSpicePointer.PTRFLAGS_DOWN) != 0)
            sendModifierKeys(true);
        sendMouseEvent(x, y, metaState, pointerMask);
        if ((pointerMask & RemoteSpicePointer.PTRFLAGS_DOWN) == 0)
            sendModifierKeys(false);
    }

    private void sendModifierKeys (boolean keyDown) {        
        if ((metaState & RemoteKeyboard.CTRL_MASK) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending CTRL: " + VK_LCONTROL);
            sendKeyEvent(keyDown, VK_LCONTROL);
        }
        if ((metaState & RemoteKeyboard.ALT_MASK) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending ALT: " + VK_LMENU);
            sendKeyEvent(keyDown, VK_LMENU);
        }
        if ((metaState & RemoteKeyboard.SUPER_MASK) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending SUPER: " + VK_LWIN);
            sendKeyEvent(keyDown, VK_LWIN);
        }
        if ((metaState & RemoteKeyboard.SHIFT_MASK) != 0) {
            //android.util.Log.e("SpiceCommunicator", "Sending SHIFT: " + VK_LSHIFT);
            sendKeyEvent(keyDown, VK_LSHIFT);
        }
    }
    
    public void writeKeyEvent(int key, int metaState, boolean down) {
        // Not used for actually sending keyboard events, but rather to record the current metastate.
        // The key event is sent to the KeyboardMapper from RemoteKeyboard, and
        // when processed through the keyboard mapper, it ends up in one of the KeyProcessingListener
        // methods defined here.
        this.metaState = metaState;
    }

    // ****************************************************************************
    // KeyboardMapper.KeyProcessingListener implementation
    @Override
    public void processVirtualKey(int virtualKeyCode, boolean keyDown) {

        if (keyDown)
            sendModifierKeys (true);
        
        //android.util.Log.e("SpiceCommunicator", "Sending VK key: " + virtualKeyCode + ". Is it down: " + down);
        sendKeyEvent(keyDown, virtualKeyCode);
        
        if (!keyDown)
            sendModifierKeys (false);
        
    }

    @Override
    public void processUnicodeKey(int unicodeKey) {
        boolean addShift = false;
        int keyToSend = -1;
        int tempMeta = 0;
        
        // Workarounds for some pesky keys.
        if (unicodeKey == 64) {
            addShift = true;
            keyToSend = 0x32;
        } else if (unicodeKey == 42) {
                addShift = true;
                keyToSend = 0x38;
        } else if (unicodeKey == 47) {
            keyToSend = 0xBF;
        } else if (unicodeKey == 63) {
            addShift = true;            
            keyToSend = 0xBF;
        }
        
        if (keyToSend != -1) {
            tempMeta = metaState;
            if (addShift) {
                metaState = metaState |  RemoteKeyboard.SHIFT_MASK;
            }
            processVirtualKey(keyToSend, true);
            processVirtualKey(keyToSend, false);
            metaState = tempMeta;
        } else
            android.util.Log.e("SpiceCommunicator", "Unsupported unicode key that needs to be mapped: " + unicodeKey);
    }

    public void requestResolution(int x, int y) {
        SpiceRequestResolution (x, y);        
    }
}
