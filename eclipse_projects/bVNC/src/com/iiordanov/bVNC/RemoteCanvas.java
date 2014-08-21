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

package com.iiordanov.bVNC;

import java.io.IOException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Timer;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.util.AttributeSet;
import android.util.Base64;
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

import com.iiordanov.android.bc.BCFactory;
import com.iiordanov.bVNC.input.RemoteSpiceKeyboard;
import com.iiordanov.bVNC.input.RemoteSpicePointer;
import com.iiordanov.bVNC.input.RemoteKeyboard;
import com.iiordanov.bVNC.input.RemotePointer;

public class RemoteCanvas extends ImageView implements UIEventListener {
    private final static String TAG = "VncCanvas";
    
    public AbstractScaling scaling;
    
    // Variable indicating that we are currently scrolling in simulated touchpad mode.
    public boolean inScrolling = false;
    
    // Connection parameters
    ConnectionBean connection;
    Database database;
    private SSHConnection sshConnection = null;
    
    // VNC protocol connection
    public RfbConnectable rfbconn   = null;
    private SpiceCommunicator spicecomm = null;
    private Socket sock             = null;
    
    boolean maintainConnection = true;
    
    // The remote pointer and keyboard
    RemotePointer pointer;
    RemoteKeyboard keyboard;
    
    // Internal bitmap data
    private int capacity;
    public AbstractBitmapData bitmapData;
    boolean useFull = false;
    boolean compact = false;
    
    // Progress dialog shown at connection time.
    ProgressDialog pd;
    
    // Used to set the contents of the clipboard.
    ClipboardManager clipboard;
    Timer clipboardMonitorTimer;
    ClipboardMonitor clipboardMonitor;
    public boolean serverJustCutText = false;
    
    private Runnable setModes;
    
    // This variable indicates whether or not the user has accepted an untrusted
    // security certificate. Used to control progress while the dialog asking the user
    // to confirm the authenticity of a certificate is displayed.
    public boolean certificateAccepted = false;
    
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
    
    /*
     * This flag indicates whether this is the SPICE 'version' or not.
     */
    boolean isSpice = true;
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
        
        clipboard = (ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        
        isSpice = true;
        
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
    void initializeCanvas(ConnectionBean bean, Database db, final Runnable setModes) {
        this.setModes = setModes;
        connection = bean;
        database = db;

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
                    // Initialize SSH key if necessary
                    if (connection.getConnectionType() == Constants.CONN_TYPE_SSH &&
                        connection.getSshHostKey().equals("")) {
                        handler.sendEmptyMessage(Constants.DIALOG_SSH_CERT);
                        
                        // Block while user decides whether to accept certificate or not.
                        // The activity ends if the user taps "No", so we block indefinitely here.
                        synchronized (RemoteCanvas.this) {
                            while (connection.getSshHostKey().equals("")) {
                                try {
                                    RemoteCanvas.this.wait();
                                } catch (InterruptedException e) { e.printStackTrace(); }
                            }
                        }
                    }
                    
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

        clipboardMonitor = new ClipboardMonitor(getContext(), this);
        if (clipboardMonitor != null) {
            clipboardMonitorTimer = new Timer ();
            if (clipboardMonitorTimer != null) {
                try {
                    clipboardMonitorTimer.schedule(clipboardMonitor, 0, 500);
                } catch (NullPointerException e){}
            }
        }
    }
    
    
    /**
     * Starts a SPICE connection using libspice.
     * @throws Exception
     */
    private void startSpiceConnection() throws Exception {
        // Get the address and port (based on whether an SSH tunnel is being established or not).
        String address = getAddress();
        // To prevent an SSH tunnel being created when port or TLS port is not set, we only
        // getPort when port/tport are positive.
        int port = connection.getPort();
        if (port > 0)
            port = getPort(port);
        
        int tport = connection.getTlsPort();
        if (tport > 0)
            tport = getPort(tport);
        
        spicecomm = new SpiceCommunicator (getContext(), this, connection);
        rfbconn = spicecomm;
        pointer = new RemoteSpicePointer (rfbconn, RemoteCanvas.this, handler);
        keyboard = new RemoteSpiceKeyboard (rfbconn, RemoteCanvas.this, handler);
        spicecomm.setUIEventListener(RemoteCanvas.this);
        spicecomm.setHandler(handler);
        spicecomm.connect(address, Integer.toString(port), Integer.toString(tport),
                            connection.getPassword(), connection.getCaCertPath(),
                            connection.getCertSubject(), connection.getEnableSound());
    }
    
    
    /**
     * Retreives the requested remote width.
     */
    private int getRemoteWidth (int viewWidth, int viewHeight) {
        int remoteWidth = 0;
        int reqWidth  = connection.getRdpWidth();
        int reqHeight = connection.getRdpHeight();
        if (connection.getRdpResType() == Constants.RDP_GEOM_SELECT_CUSTOM &&
            reqWidth >= 2 && reqHeight >= 2) {
            remoteWidth  = reqWidth;
        } else if (connection.getRdpResType() == Constants.RDP_GEOM_SELECT_NATIVE_PORTRAIT) {
            remoteWidth  = Math.min(viewWidth, viewHeight);
        } else {
            remoteWidth  = Math.max(viewWidth, viewHeight);
        }
        // We make the resolution even if it is odd.
        if (remoteWidth % 2 == 1) remoteWidth--;
        return remoteWidth;
    }
    
    
    /**
     * Retreives the requested remote height.
     */
    private int getRemoteHeight (int viewWidth, int viewHeight) {
        int remoteHeight = 0;
        int reqWidth  = connection.getRdpWidth();
        int reqHeight = connection.getRdpHeight();
        if (connection.getRdpResType() == Constants.RDP_GEOM_SELECT_CUSTOM &&
            reqWidth >= 2 && reqHeight >= 2) {
            remoteHeight = reqHeight;
        } else if (connection.getRdpResType() == Constants.RDP_GEOM_SELECT_NATIVE_PORTRAIT) {
            remoteHeight = Math.max(viewWidth, viewHeight);
        } else {
            remoteHeight = Math.min(viewWidth, viewHeight);
        }
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
     * If necessary, initializes an SSH tunnel and returns local forwarded port, or
     * if SSH tunneling is not needed, returns the given port.
     * @return
     * @throws Exception
     */
    int getPort(int port) throws Exception {
        int result = 0;
        
        if (connection.getConnectionType() == Constants.CONN_TYPE_SSH) {
            if (sshConnection == null) {
                sshConnection = new SSHConnection(connection, getContext());
            }
            // TODO: Take the AutoX stuff out to a separate function.
            int newPort = sshConnection.initializeSSHTunnel ();
            if (newPort > 0)
                port = newPort;
            result = sshConnection.createLocalPortForward(port);
        } else {
            result = port;
        }
        return result;
    }
    
    
    /** 
     * Returns localhost if using SSH tunnel or saved VNC address.
     * @return
     * @throws Exception
     */
    String getAddress() {
        if (connection.getConnectionType() == Constants.CONN_TYPE_SSH) {
            return new String("127.0.0.1");
        } else
            return connection.getAddress();
    }
    
    
    /**
     * Initializes the drawable and bitmap into which the remote desktop is drawn.
     * @param dx
     * @param dy
     * @throws IOException
     */
    void initializeBitmap (int dx, int dy) throws IOException {
        Log.i(TAG, "Desktop name is " + rfbconn.desktopName());
        Log.i(TAG, "Desktop size is " + rfbconn.framebufferWidth() + " x " + rfbconn.framebufferHeight());
        int fbsize = rfbconn.framebufferWidth() * rfbconn.framebufferHeight();
        capacity = BCFactory.getInstance().getBCActivityManager().getMemoryClass(Utils.getActivityManager(getContext()));
        
        if (connection.getForceFull() == BitmapImplHint.AUTO) {
            if (fbsize * CompactBitmapData.CAPACITY_MULTIPLIER <= capacity*1024*1024) {
                useFull = true;
                compact = true;
            } else if (fbsize * FullBufferBitmapData.CAPACITY_MULTIPLIER <= capacity*1024*1024) {
                useFull = true;
            } else {
                useFull = false;
            }
        } else
            useFull = (connection.getForceFull() == BitmapImplHint.FULL);
        
        if (!useFull) {
            bitmapData=new LargeBitmapData(rfbconn, this, dx, dy, capacity);
            android.util.Log.i(TAG, "Using LargeBitmapData.");
        } else {
            try {
                // TODO: Remove this if Android 4.2 receives a fix for a bug which causes it to stop drawing
                // the bitmap in CompactBitmapData when under load (say playing a video over VNC).
                if (!compact) {
                    bitmapData=new FullBufferBitmapData(rfbconn, this, capacity);
                    android.util.Log.i(TAG, "Using FullBufferBitmapData.");
                } else {
                    bitmapData=new CompactBitmapData(rfbconn, this, isSpice);
                    android.util.Log.i(TAG, "Using CompactBufferBitmapData.");
                }
            } catch (Throwable e) { // If despite our efforts we fail to allocate memory, use LBBM.
                disposeDrawable ();
                
                useFull = false;
                bitmapData=new LargeBitmapData(rfbconn, this, dx, dy, capacity);
                android.util.Log.i(TAG, "Using LargeBitmapData.");
            }
        }
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
     * The remote desktop's size has changed and this method
     * reinitializes local data structures to match.
     */
    public void updateFBSize () {
        try {
            bitmapData.frameBufferSizeChanged ();
        } catch (Throwable e) {
            boolean useLBBM = false;
            
            // If we've run out of memory, try using another bitmapdata type.
            if (e instanceof OutOfMemoryError) {
                disposeDrawable ();
                
                // If we were using CompactBitmapData, try FullBufferBitmapData.
                if (compact == true) {
                    compact = false;
                    try {
                        bitmapData = new FullBufferBitmapData(rfbconn, this, capacity);
                    } catch (Throwable e2) {
                        useLBBM = true;
                    }
                } else
                    useLBBM = true;
                
                // Failing FullBufferBitmapData or if we weren't using CompactBitmapData, try LBBM.
                if (useLBBM) {
                    disposeDrawable ();
                    
                    useFull = false;
                    bitmapData = new LargeBitmapData(rfbconn, this, getWidth(), getHeight(), capacity);
                }
            }
        }
        handler.post(drawableSetter);
        handler.post(setModes);
        handler.post(desktopInfo);        
        bitmapData.syncScroll();
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
     * Indicates that RemoteCanvas's scroll position should be synchronized with the
     * drawable's scroll position (used only in LargeBitmapData)
     */
    public void syncScroll () {
        bitmapData.syncScroll();
    }
    
    
    /**
     * Requests a remote desktop update at the specified rectangle.
     */
    public void writeFramebufferUpdateRequest (int x, int y, int w, int h, boolean incremental) throws IOException {
        bitmapData.prepareFullUpdateRequest(incremental);
        rfbconn.writeFramebufferUpdateRequest(x, y, w, h, incremental);
    }
    
    
    /**
     * Requests an update of the entire remote desktop.
     */
    public void writeFullUpdateRequest (boolean incremental) {
        bitmapData.prepareFullUpdateRequest(incremental);
        rfbconn.writeFramebufferUpdateRequest(bitmapData.getXoffset(), bitmapData.getYoffset(),
                                              bitmapData.bmWidth(),    bitmapData.bmHeight(), incremental);
    }
    
    
    /**
     * Set the device clipboard text with the string parameter.
     * @param readServerCutText set the device clipboard to the text in this parameter.
     */
    public void setClipboardText(String s) {
        if (s != null && s.length() > 0) {
            clipboard.setText(s);
        }
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
        // Close the rfb connection.
        if (rfbconn != null)
            rfbconn.close();
        
        
        // Close the SSH tunnel.
        if (sshConnection != null) {
            sshConnection.terminateSSHTunnel();
            sshConnection = null;
        }
        onDestroy();
    }
    
    
    /**
     * Cleans up resources after a disconnection.
     */
    public void onDestroy() {
        Log.v(TAG, "Cleaning up resources");
        
        removeCallbacksAndMessages();
        if (clipboardMonitorTimer != null) {
            clipboardMonitorTimer.cancel();
            // Occasionally causes a NullPointerException
            //clipboardMonitorTimer.purge();
            clipboardMonitorTimer = null;
        }
        clipboardMonitor = null;
        clipboard        = null;
        setModes         = null;
        database         = null;
        connection       = null;
        scaling          = null;
        drawableSetter   = null;
        screenMessage    = null;
        desktopInfo      = null;
        
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
        shiftX = (rfbconn.framebufferWidth()  - getWidth())  / 2;
        shiftY = (rfbconn.framebufferHeight() - getHeight()) / 2;
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
        if (rfbconn == null)
            return;
        
        boolean panX = true;
        boolean panY = true;
        
        // Don't pan in a certain direction if dimension scaled is already less 
        // than the dimension of the visible part of the screen.
        if (rfbconn.framebufferWidth()  <= getVisibleWidth())
            panX = false;
        if (rfbconn.framebufferHeight() <= getVisibleHeight())
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
     * This runnable causes a toast with information about the current connection to be shown.
     */
    private Runnable desktopInfo = new Runnable() {
        public void run() {
            showConnectionInfo();
        }
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
     * Displays connection info in a toast message.
     */
    public void showConnectionInfo() {
        if (rfbconn == null)
            return;
        
        String msg = null;
        int idx = rfbconn.desktopName().indexOf("(");
        if (idx > 0) {
            // Breakup actual desktop name from IP addresses for improved
            // readability
            String dn = rfbconn.desktopName().substring(0, idx).trim();
            String ip = rfbconn.desktopName().substring(idx).trim();
            msg = dn + "\n" + ip;
        } else
            msg = rfbconn.desktopName();
        msg += "\n" + rfbconn.framebufferWidth() + "x" + rfbconn.framebufferHeight();
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
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
        return rfbconn.framebufferWidth();
    }
    
    public int getImageHeight() {
        return rfbconn.framebufferHeight();
    }
    
    public int getCenteredXOffset() {
        return (rfbconn.framebufferWidth() - getWidth()) / 2;
    }
    
    public int getCenteredYOffset() {
        return (rfbconn.framebufferHeight() - getHeight()) / 2;
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
    //  Implementation of UIEventListener. Through the functions implemented
    //  below libspice communicates remote desktop size and updates.
    //////////////////////////////////////////////////////////////////////////////////
    
    @Override
    public void OnSettingsChanged(int width, int height, int bpp) {
        android.util.Log.e(TAG, "onSettingsChanged called, wxh: " + width + "x" + height);
        
        // If this is aSPICE, we need to initialize the communicator and remote keyboard and mouse now.
        if (isSpice) {
            spicecomm.setFramebufferWidth(width);
            spicecomm.setFramebufferHeight(height);
            waitUntilInflated();
            int remoteWidth  = getRemoteWidth(getWidth(), getHeight());
            int remoteHeight = getRemoteHeight(getWidth(), getHeight());
            if (width != remoteWidth || height != remoteHeight) {
                android.util.Log.e(TAG, "Requesting new res: " + remoteWidth + "x" + remoteHeight);
                rfbconn.requestResolution(remoteWidth, remoteHeight);
            }
        }
        
        disposeDrawable ();
        try {
            // TODO: Use frameBufferSizeChanged instead.
            bitmapData = new CompactBitmapData(rfbconn, this, isSpice);
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
        handler.post(desktopInfo);
        
        // If this is aSPICE, set the new bitmap in the native layer.
        if (isSpice) {
            spiceUpdateReceived = true;
            rfbconn.setIsInNormalProtocol(true);
            handler.sendEmptyMessage(Constants.SPICE_CONNECT_SUCCESS);
        }
    }

    @Override
    public boolean OnAuthenticate(StringBuilder username, StringBuilder domain, StringBuilder password) {
        android.util.Log.e(TAG, "onAuthenticate called.");
        showFatalMessageAndQuit (getContext().getString(R.string.error_rdp_authentication_failed));
        return false;
    }

    @Override
    public boolean OnVerifiyCertificate(String subject, String issuer, String fingerprint) {
        android.util.Log.e(TAG, "OnVerifiyCertificate called.");
        
        // Send a message containing the certificate to our handler.
        Message m = new Message();
        m.setTarget(handler);
        m.what = Constants.DIALOG_RDP_CERT;
        Bundle strings = new Bundle();
        strings.putString("subject", subject);
        strings.putString("issuer", issuer);
        strings.putString("fingerprint", fingerprint);
        m.obj = strings;
        handler.sendMessage(m);

        // Block while user decides whether to accept certificate or not.
        // The activity ends if the user taps "No", so we block indefinitely here.
        synchronized (RemoteCanvas.this) {
            while (!certificateAccepted) {
                try {
                    RemoteCanvas.this.wait();
                } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }

        return true;
    }

    @Override
    public void OnGraphicsUpdate(int x, int y, int width, int height) {
        //android.util.Log.e(TAG, "OnGraphicsUpdate called: " + x +", " + y + " + " + width + "x" + height );
        synchronized (bitmapData.mbitmap) {
            spicecomm.UpdateBitmap(bitmapData.mbitmap, x, y, width, height);
        }
        
        reDraw(x, y, width, height);
    }

    @Override
    public void OnGraphicsResize(int width, int height, int bpp) {
        android.util.Log.e(TAG, "OnGraphicsResize called.");
        OnSettingsChanged(width, height, bpp);
    }
    
    @Override
    public void OnRemoteClipboardChanged(String data) {
        serverJustCutText = true;
        setClipboardText(data);
    }

    /** 
     * Handler for the dialogs that display the x509/RDP/SSH key signatures to the user.
     * Also shows the dialogs which show various connection failures.
     */
    public Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case Constants.DIALOG_X509_CERT:
                validateX509Cert ((X509Certificate)msg.obj);
                break;
            case Constants.DIALOG_SSH_CERT:
                initializeSshHostKey();
                break;
            case Constants.DIALOG_RDP_CERT:
                Bundle s = (Bundle)msg.obj;
                validateRdpCert (s.getString("subject"), s.getString("issuer"), s.getString("fingerprint"));
                break;
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
    
    /**
     * If there is a saved cert, checks the one given against it. Otherwise, presents the
     * given cert's signature to the user for approval.
     * @param cert the given cert.
     */
    private void validateX509Cert (final X509Certificate cert) {

        // If there has been no key approved by the user previously, ask for approval, else
        // check the saved key against the one we are presented with.
        if (connection.getSshHostKey().equals("")) {
            // Show a dialog with the key signature for approval.
            DialogInterface.OnClickListener signatureNo = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // We were told not to continue, so stop the activity
                    closeConnection();
                    ((Activity) getContext()).finish();
                }
            };
            DialogInterface.OnClickListener signatureYes = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // We were told to go ahead with the connection, so save the key into the database.
                    String certificate = null;
                    try {
                        certificate = Base64.encodeToString(cert.getEncoded(), Base64.DEFAULT);
                    } catch (CertificateEncodingException e) {
                        e.printStackTrace();
                        showFatalMessageAndQuit(getContext().getString(R.string.error_x509_could_not_generate_encoding));
                    }
                    connection.setSshHostKey(certificate);
                    connection.save(database.getWritableDatabase());
                    database.close();
                    // Indicate the certificate was accepted.
                    certificateAccepted = true;
                    synchronized (RemoteCanvas.this) {
                        RemoteCanvas.this.notifyAll();
                    }
                }
            };

            // Generate a sha1 signature of the certificate.
            MessageDigest sha1;
            MessageDigest md5;
            try {
                sha1 = MessageDigest.getInstance("SHA1");
                md5 = MessageDigest.getInstance("MD5");
                sha1.update(cert.getEncoded());
                Utils.showYesNoPrompt(getContext(), getContext().getString(R.string.info_continue_connecting) + connection.getAddress () + "?",
                                      getContext().getString(R.string.info_cert_signatures)   +
                                        "\nSHA1:  " + Utils.toHexString(sha1.digest()) +
                                        "\nMD5:  "  + Utils.toHexString(md5.digest())  + 
                                        getContext().getString(R.string.info_cert_signatures_identical),
                                        signatureYes, signatureNo);
            } catch (NoSuchAlgorithmException e2) {
                e2.printStackTrace();
                showFatalMessageAndQuit(getContext().getString(R.string.error_x509_could_not_generate_signature));
            } catch (CertificateEncodingException e) {
                e.printStackTrace();
                showFatalMessageAndQuit(getContext().getString(R.string.error_x509_could_not_generate_encoding));
            }
        } else {
            // Compare saved with obtained certificate and quit if they don't match.
            try {
                if (!connection.getSshHostKey().equals(Base64.encodeToString(cert.getEncoded(), Base64.DEFAULT))) {
                    showFatalMessageAndQuit(getContext().getString(R.string.error_cert_does_not_match));
                } else {
                    // In case we need to display information about the certificate, we can reconstruct it like this:
                    //CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    //ByteArrayInputStream in = new ByteArrayInputStream(Base64.decode(connection.getSshHostKey(), Base64.DEFAULT));
                    //X509Certificate c = (X509Certificate)certFactory.generateCertificate(in);
                    //android.util.Log.e("  Subject ", c.getSubjectDN().toString());
                    //android.util.Log.e("   Issuer  ", c.getIssuerDN().toString());
                    
                    // The certificate matches, so we proceed.
                    certificateAccepted = true;
                    synchronized (RemoteCanvas.this) {
                        RemoteCanvas.this.notifyAll();
                    }
                }
            } catch (CertificateEncodingException e) {
                e.printStackTrace();
                showFatalMessageAndQuit(getContext().getString(R.string.error_x509_could_not_generate_encoding));
            }
        }
    }
    
    
    /**
     * Permits the user to validate an RDP certificate.
     * @param subject
     * @param issuer
     * @param fingerprint
     */
    private void validateRdpCert (String subject, String issuer, final String fingerprint) {
        // Since LibFreeRDP handles saving accepted certificates, if we ever get here, we must
        // present the user with a query whether to accept the certificate or not.

        // Show a dialog with the key signature for approval.
        DialogInterface.OnClickListener signatureNo = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // We were told not to continue, so stop the activity
                closeConnection();
                ((Activity) getContext()).finish();
            }
        };
        DialogInterface.OnClickListener signatureYes = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Indicate the certificate was accepted.
                certificateAccepted = true;
                synchronized (RemoteCanvas.this) {
                    RemoteCanvas.this.notifyAll();
                }
            }
        };
        Utils.showYesNoPrompt(getContext(), getContext().getString(R.string.info_continue_connecting) + connection.getAddress () + "?",
                getContext().getString(R.string.info_cert_signatures) +
                        "\nSubject:      " + subject +
                        "\nIssuer:       " + issuer +
                        "\nFingerprint:  " + fingerprint + 
                        getContext().getString(R.string.info_cert_signatures_identical),
                        signatureYes, signatureNo);
    }
    
    
    /**
     * Function used to initialize an empty SSH HostKey for a new VNC over SSH connection.
     */
    private void initializeSshHostKey() {
        // If the SSH HostKey is empty, then we need to grab the HostKey from the server and save it.
        Log.d(TAG, "Attempting to initialize SSH HostKey.");
        
        displayShortToastMessage(getContext().getString(R.string.info_ssh_initializing_hostkey));
        
        sshConnection = new SSHConnection(connection, getContext());
        if (!sshConnection.connect()) {
            // Failed to connect, so show error message and quit activity.
            showFatalMessageAndQuit(getContext().getString(R.string.error_ssh_unable_to_connect));
        } else {
            // Show a dialog with the key signature.
            DialogInterface.OnClickListener signatureNo = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // We were told to not continue, so stop the activity
                    sshConnection.terminateSSHTunnel();
                    pd.dismiss();
                    ((Activity) getContext()).finish();
                }   
            };
            DialogInterface.OnClickListener signatureYes = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // We were told to go ahead with the connection.
                    connection.setSshHostKey(sshConnection.getServerHostKey());
                    connection.save(database.getWritableDatabase());
                    database.close();
                    sshConnection.terminateSSHTunnel();
                    sshConnection = null;
                    synchronized (RemoteCanvas.this) {
                        RemoteCanvas.this.notify();
                    }
                }
            };
            
            Utils.showYesNoPrompt(getContext(),
                    getContext().getString(R.string.info_continue_connecting) + connection.getSshServer() + "?",
                    getContext().getString(R.string.info_ssh_key_fingerprint) + sshConnection.getHostKeySignature() +
                    getContext().getString(R.string.info_ssh_key_fingerprint_identical),
                    signatureYes, signatureNo);
        }
    }
}
