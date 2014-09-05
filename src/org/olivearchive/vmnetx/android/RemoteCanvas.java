/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2010 Michael A. MacDonald
 * Copyright (C) 2004 Horizon Wimba.  All Rights Reserved.
 * Copyright (C) 2001-2003 HorizonLive.com, Inc.  All Rights Reserved.
 * Copyright (C) 2001,2002 Constantin Kaplinsky.  All Rights Reserved.
 * Copyright (C) 2000 Tridia Corporation.  All Rights Reserved.
 * Copyright (C) 1999 AT&T Laboratories Cambridge.  All Rights Reserved.
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

//
// RemoteCanvas is a subclass of android.view.SurfaceView which draws a VNC
// desktop on it.
//

package org.olivearchive.vmnetx.android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.ImageView;
import android.widget.Toast;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.olivearchive.vmnetx.android.input.RemoteSpiceKeyboard;
import org.olivearchive.vmnetx.android.input.RemoteSpicePointer;
import org.olivearchive.vmnetx.android.input.RemoteKeyboard;
import org.olivearchive.vmnetx.android.input.RemotePointer;

public class RemoteCanvas extends ImageView {
    private final static String TAG = "VncCanvas";
    
    public AbstractScaling scaling;
    
    // Variable indicating that we are currently scrolling in simulated touchpad mode.
    public boolean inScrolling = false;
    
    // Connection parameters
    ConnectionBean connection;
    
    // SPICE protocol connection
    private SpiceCommunicator spice = null;
    
    boolean maintainConnection = true;
    
    // The remote pointer and keyboard
    RemotePointer pointer;
    RemoteKeyboard keyboard;
    
    // Internal bitmap data
    public AbstractBitmapData bitmapData;
    boolean useFull = false;
    boolean compact = false;
    
    // Progress dialog shown at connection time.
    ProgressDialog pd;
    
    private Runnable setModes;
    
    /*
     * Position of the top left portion of the <i>visible</i> part of the screen, in
     * full-frame coordinates
     */
    int absoluteXPosition = 0, absoluteYPosition = 0;
    
    /*
     * How much to shift coordinates over when converting from full to view coordinates.
     */
    float shiftX = 0, shiftY = 0;

    /*
     * This variable holds the height of the visible rectangle of the screen. It is used to keep track
     * of how much of the screen is hidden by the soft keyboard if any.
     */
    int visibleHeight = -1;

    /*
     * These variables contain the width and height of the display in pixels
     */
    int displayWidth = 0;
    int displayHeight = 0;
    float displayDensity = 0;
    
    boolean spiceUpdateReceived = false;
    
    /*
     * Variable used for BB workarounds.
     */
    boolean bb = false;
    
    /**
     * Constructor used by the inflation apparatus
     * 
     * @param context
     */
    public RemoteCanvas(final Context context, AttributeSet attrs) {
        super(context, attrs);
        
        final Display display = ((Activity)context).getWindow().getWindowManager().getDefaultDisplay();
        displayWidth  = display.getWidth();
        displayHeight = display.getHeight();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        displayDensity = metrics.density;
        
        if (android.os.Build.MODEL.contains("BlackBerry") ||
            android.os.Build.BRAND.contains("BlackBerry") || 
            android.os.Build.MANUFACTURER.contains("BlackBerry")) {
            bb = true;
        }
    }
    
    
    /**
     * Create a view showing a remote desktop connection
     * @param context Containing context (activity)
     * @param bean Connection settings
     * @param setModes Callback to run on UI thread after connection is set up
     */
    void initializeCanvas(ConnectionBean bean, final Runnable setModes) {
        this.setModes = setModes;
        connection = bean;

        // Startup the connection thread with a progress dialog
        pd = ProgressDialog.show(getContext(), getContext().getString(R.string.info_progress_dialog_connecting),
                                                getContext().getString(R.string.info_progress_dialog_establishing),
                                                true, true, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                closeConnection();
                handler.post(new Runnable() {
                    public void run() {
                        Utils.showFatalErrorMessage(getContext(), getContext().getString(R.string.info_progress_dialog_aborted));
                    }
                });
            }
        });
        
        // Make this dialog cancellable only upon hitting the Back button and not touching outside.
        pd.setCanceledOnTouchOutside(false);
        
        Thread t = new Thread () {
            public void run() {
                try {
                    startSpiceConnection();
                } catch (Throwable e) {
                    if (maintainConnection) {
                        Log.e(TAG, e.toString());
                        e.printStackTrace();
                        // Ensure we dismiss the progress dialog before we finish
                        if (pd.isShowing())
                            pd.dismiss();
                        
                        if (e instanceof OutOfMemoryError) {
                            disposeDrawable ();
                            showFatalMessageAndQuit (getContext().getString(R.string.error_out_of_memory));
                        } else {
                            String error = getContext().getString(R.string.error_connection_failed);
                            if (e.getMessage() != null) {
                                if (e.getMessage().indexOf("authentication") > -1 ||
                                        e.getMessage().indexOf("Unknown security result") > -1 ||
                                        e.getMessage().indexOf("password check failed") > -1) {
                                    error = getContext().getString(R.string.error_vnc_authentication);
                                }
                                error = error + "<br>" + e.getLocalizedMessage();
                            }
                            showFatalMessageAndQuit(error);
                        }
                    }
                }
            }
        };
        t.start();
    }
    
    
    /**
     * Starts a SPICE connection using libspice.
     * @throws Exception
     */
    private void startSpiceConnection() throws Exception {
        spice = new SpiceCommunicator (getContext(), this, handler, connection);
        pointer = new RemoteSpicePointer (spice, RemoteCanvas.this);
        keyboard = new RemoteSpiceKeyboard (spice, RemoteCanvas.this, handler);
        spice.connect();
    }
    
    
    /**
     * Retreives the requested remote width.
     */
    private int getRemoteWidth (int viewWidth, int viewHeight) {
        int remoteWidth = Math.max(viewWidth, viewHeight);
        // We make the resolution even if it is odd.
        if (remoteWidth % 2 == 1) remoteWidth--;
        return remoteWidth;
    }
    
    
    /**
     * Retreives the requested remote height.
     */
    private int getRemoteHeight (int viewWidth, int viewHeight) {
        int remoteHeight = Math.min(viewWidth, viewHeight);
        // We make the resolution even if it is odd.
        if (remoteHeight % 2 == 1) remoteHeight--;
        return remoteHeight;
    }
    
    
    /**
     * Closes the connection and shows a fatal message which ends the activity.
     * @param error
     */
    void showFatalMessageAndQuit (final String error) {
        closeConnection();
        handler.post(new Runnable() {
            public void run() {
                Utils.showFatalErrorMessage(getContext(), error);
            }
        });
    }
    
    
    /**
     * Disposes of the old drawable which holds the remote desktop data.
     */
    private void disposeDrawable () {
        if (bitmapData != null)
            bitmapData.dispose();
        bitmapData = null;
        System.gc();
    }
    
    
    /**
     * Displays a short toast message on the screen.
     * @param message
     */
    public void displayShortToastMessage (final CharSequence message) {
        screenMessage = message;
        handler.removeCallbacks(showMessage);
        handler.post(showMessage);
    }
    
    
    /**
     * Displays a short toast message on the screen.
     * @param messageID
     */
    public void displayShortToastMessage (final int messageID) {
        screenMessage = getResources().getText(messageID);
        handler.removeCallbacks(showMessage);
        handler.post(showMessage);
    }
    
    
    /**
     * Lets the drawable know that an update from the remote server has arrived.
     */
    public void doneWaiting () {
        bitmapData.doneWaiting();
    }
    
    
    /**
     * Method that disconnects from the remote server.
     */
    public void closeConnection() {
        maintainConnection = false;
        
        if (keyboard != null) {
            // Tell the server to release any meta keys.
            keyboard.clearMetaState();
            keyboard.processLocalKeyEvent(0, new KeyEvent(KeyEvent.ACTION_UP, 0));
        }
        // Close the SPICE connection.
        if (spice != null)
            spice.disconnect();
        
        onDestroy();
    }
    
    
    /**
     * Cleans up resources after a disconnection.
     */
    public void onDestroy() {
        Log.v(TAG, "Cleaning up resources");
        
        removeCallbacksAndMessages();
        setModes         = null;
        connection       = null;
        scaling          = null;
        drawableSetter   = null;
        screenMessage    = null;
        
        disposeDrawable ();
    }
    
    
    public void removeCallbacksAndMessages() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
    
    /*
     * f(x,s) is a function that returns the coordinate in screen/scroll space corresponding
     * to the coordinate x in full-frame space with scaling s.
     * 
     * This function returns the difference between f(x,s1) and f(x,s2)
     * 
     * f(x,s) = (x - i/2) * s + ((i - w)/2)) * s
     *        = s (x - i/2 + i/2 + w/2)
     *        = s (x + w/2)
     * 
     * 
     * f(x,s) = (x - ((i - w)/2)) * s
     * @param oldscaling
     * @param scaling
     * @param imageDim
     * @param windowDim
     * @param offset
     * @return
     */
    
    /**
     * Computes the X and Y offset for converting coordinates from full-frame coordinates to view coordinates.
     */
    public void computeShiftFromFullToView () {
        shiftX = (spice.framebufferWidth()  - getWidth())  / 2;
        shiftY = (spice.framebufferHeight() - getHeight()) / 2;
    }
    
    /**
     * Change to Canvas's scroll position to match the absoluteXPosition
     */
    void scrollToAbsolute()    {
        float scale = getScale();
        scrollTo((int)((absoluteXPosition - shiftX) * scale),
                 (int)((absoluteYPosition - shiftY) * scale));
    }
    
    
    /**
     * Make sure mouse is visible on displayable part of screen
     */
    public void panToMouse() {
        if (spice == null)
            return;
        
        boolean panX = true;
        boolean panY = true;
        
        // Don't pan in a certain direction if dimension scaled is already less 
        // than the dimension of the visible part of the screen.
        if (spice.framebufferWidth()  <= getVisibleWidth())
            panX = false;
        if (spice.framebufferHeight() <= getVisibleHeight())
            panY = false;
        
        // We only pan if the current scaling is able to pan.
        if (scaling != null && ! scaling.isAbleToPan())
            return;
        
        int x = pointer.getX();
        int y = pointer.getY();
        boolean panned = false;
        int w = getVisibleWidth();
        int h = getVisibleHeight();
        int iw = getImageWidth();
        int ih = getImageHeight();
        int wthresh = 30;
        int hthresh = 30;
        
        int newX = absoluteXPosition;
        int newY = absoluteYPosition;
        
        if (x - absoluteXPosition >= w - wthresh) {
            newX = x - (w - wthresh);
            if (newX + w > iw)
                newX = iw - w;
        } else if (x < absoluteXPosition + wthresh) {
            newX = x - wthresh;
            if (newX < 0)
                newX = 0;
        }
        if ( panX && newX != absoluteXPosition ) {
            absoluteXPosition = newX;
            panned = true;
        }
        
        if (y - absoluteYPosition >= h - hthresh) {
            newY = y - (h - hthresh);
            if (newY + h > ih)
                newY = ih - h;
        } else if (y < absoluteYPosition + hthresh) {
            newY = y - hthresh;
            if (newY < 0)
                newY = 0;
        }
        if ( panY && newY != absoluteYPosition ) {
            absoluteYPosition = newY;
            panned = true;
        }
        
        if (panned) {
            //scrollBy(newX - absoluteXPosition, newY - absoluteYPosition);
            scrollToAbsolute();
        }
    }
    
    /**
     * Pan by a number of pixels (relative pan)
     * @param dX
     * @param dY
     * @return True if the pan changed the view (did not move view out of bounds); false otherwise
     */
    public boolean pan(int dX, int dY) {
        
        // We only pan if the current scaling is able to pan.
        if (scaling != null && ! scaling.isAbleToPan())
            return false;
        
        double scale = getScale();
        
        double sX = (double)dX / scale;
        double sY = (double)dY / scale;
        
        if (absoluteXPosition + sX < 0)
            // dX = diff to 0
            sX = -absoluteXPosition;
        if (absoluteYPosition + sY < 0)
            sY = -absoluteYPosition;
        
        // Prevent panning right or below desktop image
        if (absoluteXPosition + getVisibleWidth() + sX > getImageWidth())
            sX = getImageWidth() - getVisibleWidth() - absoluteXPosition;
        if (absoluteYPosition + getVisibleHeight() + sY > getImageHeight())
            sY = getImageHeight() - getVisibleHeight() - absoluteYPosition;
        
        absoluteXPosition += sX;
        absoluteYPosition += sY;
        if (sX != 0.0 || sY != 0.0) {
            //scrollBy((int)sX, (int)sY);
            scrollToAbsolute();
            return true;
        }
        return false;
    }

    /* (non-Javadoc)
     * @see android.view.View#onScrollChanged(int, int, int, int)
     */
    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (bitmapData != null) {
            bitmapData.scrollChanged(absoluteXPosition, absoluteYPosition);
            pointer.mouseFollowPan();
        }
    }
    
    
    /**
     * This runnable sets the drawable (contained in bitmapData) for the VncCanvas (ImageView).
     */
    private Runnable drawableSetter = new Runnable() {
        public void run() {
            if (bitmapData != null)
                bitmapData.setImageDrawable(RemoteCanvas.this);
            }
    };
    
    
    /**
     * This runnable displays a message on the screen.
     */
    CharSequence screenMessage;
    private Runnable showMessage = new Runnable() {
            public void run() { Toast.makeText( getContext(), screenMessage, Toast.LENGTH_SHORT).show(); }
    };
    
    
    /**
     * Causes a redraw of the bitmapData to happen at the indicated coordinates.
     */
    public void reDraw(int x, int y, int w, int h) {
        float scale = getScale();
        float shiftedX = x-shiftX;
        float shiftedY = y-shiftY;
        // Make the box slightly larger to avoid artifacts due to truncation errors.
        postInvalidate ((int)((shiftedX-1)*scale),   (int)((shiftedY-1)*scale),
                        (int)((shiftedX+w+1)*scale), (int)((shiftedY+h+1)*scale));
    }
    
    
    /**
     * This is a float-accepting version of reDraw().
     * Causes a redraw of the bitmapData to happen at the indicated coordinates.
     */
    public void reDraw(float x, float y, float w, float h) {
        float scale = getScale();
        float shiftedX = x-shiftX;
        float shiftedY = y-shiftY;
        // Make the box slightly larger to avoid artifacts due to truncation errors.
        postInvalidate ((int)((shiftedX-1.f)*scale),   (int)((shiftedY-1.f)*scale),
                        (int)((shiftedX+w+1.f)*scale), (int)((shiftedY+h+1.f)*scale));
    }
    
    /**
     * Invalidates (to redraw) the location of the remote pointer.
     */
    public void invalidateMousePosition() {
        if (bitmapData != null) {
            bitmapData.moveCursorRect(pointer.getX(), pointer.getY());
            RectF r = bitmapData.getCursorRect();
            reDraw(r.left, r.top, r.width(), r.height());
        }
    }
    
    
    /**
     * Moves soft cursor into a particular location.
     * @param x
     * @param y
     */
    synchronized void softCursorMove(int x, int y) {
        if (bitmapData.isNotInitSoftCursor()) {
            initializeSoftCursor();
        }
        
        if (!inScrolling) {
            pointer.setX(x);
            pointer.setY(y);
            RectF prevR = new RectF(bitmapData.getCursorRect());
            // Move the cursor.
            bitmapData.moveCursorRect(x, y);
            // Show the cursor.
            RectF r = bitmapData.getCursorRect();
            reDraw(r.left, r.top, r.width(), r.height());
            reDraw(prevR.left, prevR.top, prevR.width(), prevR.height());
        }
    }
    
    
    /**
     * Initializes the data structure which holds the remote pointer data.
     */
    void initializeSoftCursor () {
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.cursor);
        int w = bm.getWidth();
        int h = bm.getHeight();
        int [] tempPixels = new int[w*h];
        bm.getPixels(tempPixels, 0, w, 0, 0, w, h);
        // Set cursor rectangle as well.
        bitmapData.setCursorRect(pointer.getX(), pointer.getY(), w, h, 0, 0);
        // Set softCursor to whatever the resource is.
        bitmapData.setSoftCursor (tempPixels);
        bm.recycle();
    }
    
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        android.util.Log.d(TAG, "onCreateInputConnection called");
        int version = android.os.Build.VERSION.SDK_INT;
        BaseInputConnection bic = null;
        if (!bb && version >= Build.VERSION_CODES.JELLY_BEAN) {
            bic = new BaseInputConnection(this, false) {
                final static String junk_unit = "%%%%%%%%%%";
                final static int multiple = 1000;
                Editable e;
                @Override
                public Editable getEditable() {
                    if (e == null) {
                        int numTotalChars = junk_unit.length()*multiple;
                        String junk = new String();
                        for (int i = 0; i < multiple ; i++) {
                            junk += junk_unit;
                        }
                        e = Editable.Factory.getInstance().newEditable(junk);
                        Selection.setSelection(e, numTotalChars);
                        if (RemoteCanvas.this.keyboard != null) {
                            RemoteCanvas.this.keyboard.skippedJunkChars = false;
                        }
                    }
                    return e;
                }
            };
        } else {
            bic = new BaseInputConnection(this, false);
        }
        
        outAttrs.actionLabel = null;
        outAttrs.inputType = InputType.TYPE_NULL;
        /* TODO: If people complain about kbd not working, this is a possible workaround to
         * test and add an option for.
        // Workaround for IME's that don't support InputType.TYPE_NULL.
        if (version >= 11) {
            outAttrs.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_FULLSCREEN;
        }*/
        return bic;
    }
    
    public RemotePointer getPointer() {
        return pointer;
    }
    
    public RemoteKeyboard getKeyboard() {
        return keyboard;
    }
    
    public float getScale() {
        if (scaling == null)
            return 1;
        return scaling.getScale();
    }
    
    public int getVisibleWidth() {
        return (int)((double)getWidth() / getScale() + 0.5);
    }
    
    public void setVisibleHeight(int newHeight) {
        visibleHeight = newHeight;
    }
    
    public int getVisibleHeight() {
        if (visibleHeight > 0)
            return (int)((double)visibleHeight / getScale() + 0.5);
        else
            return (int)((double)getHeight() / getScale() + 0.5);
    }
    
    public int getImageWidth() {
        return spice.framebufferWidth();
    }
    
    public int getImageHeight() {
        return spice.framebufferHeight();
    }
    
    public int getCenteredXOffset() {
        return (spice.framebufferWidth() - getWidth()) / 2;
    }
    
    public int getCenteredYOffset() {
        return (spice.framebufferHeight() - getHeight()) / 2;
    }
    
    public float getMinimumScale() {
        if (bitmapData != null) {
            return bitmapData.getMinimumScale();
        } else
            return 1.f;
    }
    
    public float getDisplayDensity() {
        return displayDensity;
    }
    
    public boolean getMouseFollowPan() {
        return connection.getFollowPan();
    }
    
    public int getAbsoluteX () {
        return absoluteXPosition;
    }
    
    public int getAbsoluteY () {
        return absoluteYPosition;
    }
    
    /**
     * Used to wait until getWidth and getHeight return sane values.
     */
    private void waitUntilInflated() {
        synchronized (this) {
            while (getWidth() == 0 || getHeight() == 0) {
                try {
                    this.wait();
                } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }
    }
    
    /**
     * Used to detect when the view is inflated to a sane size other than 0x0.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w > 0 && h > 0) {
            synchronized (this) {
                this.notify();
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////////////////
    //  Through the functions implemented below libspice communicates remote
    //  desktop size and updates.
    //////////////////////////////////////////////////////////////////////////////////
    
    public void OnSettingsChanged(int width, int height, int bpp) {
        android.util.Log.e(TAG, "onSettingsChanged called, wxh: " + width + "x" + height);
        
        // We need to initialize the communicator and remote keyboard and mouse now.
        spice.setFramebufferWidth(width);
        spice.setFramebufferHeight(height);
        waitUntilInflated();
        int remoteWidth  = getRemoteWidth(getWidth(), getHeight());
        int remoteHeight = getRemoteHeight(getWidth(), getHeight());
        if (width != remoteWidth || height != remoteHeight) {
            android.util.Log.e(TAG, "Requesting new res: " + remoteWidth + "x" + remoteHeight);
            spice.requestResolution(remoteWidth, remoteHeight);
        }
        
        disposeDrawable ();
        try {
            // TODO: Use frameBufferSizeChanged instead.
            bitmapData = new CompactBitmapData(spice, this);
        } catch (Throwable e) {
            showFatalMessageAndQuit (getContext().getString(R.string.error_out_of_memory));
            return;
        }
        android.util.Log.i(TAG, "Using CompactBufferBitmapData.");
        
        // TODO: In RDP mode, pointer is not visible, so we use a soft cursor.
        initializeSoftCursor();
        
        // Set the drawable for the canvas, now that we have it (re)initialized.
        handler.post(drawableSetter);
        handler.post(setModes);
        
        // Set the new bitmap in the native layer.
        spiceUpdateReceived = true;
        spice.setIsInNormalProtocol(true);
        handler.sendEmptyMessage(Constants.SPICE_CONNECT_SUCCESS);
    }

    public void OnGraphicsUpdate(int x, int y, int width, int height) {
        //android.util.Log.e(TAG, "OnGraphicsUpdate called: " + x +", " + y + " + " + width + "x" + height );
        synchronized (bitmapData.mbitmap) {
            spice.updateBitmap(bitmapData.mbitmap, x, y, width, height);
        }
        
        reDraw(x, y, width, height);
    }

    /** 
     * Handler for the dialogs that display the x509/RDP/SSH key signatures to the user.
     * Also shows the dialogs which show various connection failures.
     */
    public Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case Constants.SPICE_CONNECT_SUCCESS:
                if (pd != null && pd.isShowing()) {
                    pd.dismiss();
                }
                break;
            case Constants.SPICE_CONNECT_FAILURE:
                if (maintainConnection) {
                    if (pd != null && pd.isShowing()) {
                        pd.dismiss();
                    }
                    if (!spiceUpdateReceived) {
                        showFatalMessageAndQuit(getContext().getString(R.string.error_spice_unable_to_connect));
                    } else {
                        showFatalMessageAndQuit(getContext().getString(R.string.error_connection_interrupted));
                    }
                }
                break;
            }
        }
    };
}
