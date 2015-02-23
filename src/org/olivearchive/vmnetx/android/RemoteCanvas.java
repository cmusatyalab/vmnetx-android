/**
 * Copyright (C) 2013-2015 Carnegie Mellon University
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2009-2010 Michael A. MacDonald
 * Copyright (C) 2004 Horizon Wimba.  All Rights Reserved.
 * Copyright (C) 2001-2003 HorizonLive.com, Inc.  All Rights Reserved.
 * Copyright (C) 2001,2002 Constantin Kaplinsky.  All Rights Reserved.
 * Copyright (C) 2000 Tridia Corporation.  All Rights Reserved.
 * Copyright (C) 1999 AT&T Laboratories Cambridge.  All Rights Reserved.
 * 
 * This is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License
 * as published by the Free Software Foundation.
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
// RemoteCanvas is a subclass of android.widget.ImageView which draws a SPICE
// desktop on it.
//

package org.olivearchive.vmnetx.android;

import java.io.IOException;
import java.text.NumberFormat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
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

import org.olivearchive.vmnetx.android.input.RemoteKeyboard;
import org.olivearchive.vmnetx.android.input.RemotePointer;
import org.olivearchive.vmnetx.android.protocol.ClientProtocolEndpoint;
import org.olivearchive.vmnetx.android.protocol.ControlConnectionProcessor;

public class RemoteCanvas extends ImageView {
    private final static String TAG = "RemoteCanvas";
    
    // Connection parameters
    private ConnectionInfo connection;

    // VMNetX control connection
    private ControlConnectionProcessor controlConn;
    private ClientProtocolEndpoint endpoint;
    private String vmName = null;
    private int vmState = Constants.VM_STATE_UNKNOWN;
    
    // SPICE protocol connection
    private SpiceCommunicator spice = null;
    
    private boolean maintainConnection = true;
    
    // The viewport
    private Viewport viewport;

    // The remote pointer and keyboard
    private RemotePointer pointer;
    private RemoteKeyboard keyboard;
    private boolean absoluteMouse;
    
    // Progress dialog shown at connection time.
    private ProgressDialog pd;
    
    private Runnable updateActivity;
    
    private final float displayDensity;
    
    private boolean spiceUpdateReceived = false;
    
    /**
     * Constructor used by the inflation apparatus
     * 
     * @param context
     */
    public RemoteCanvas(final Context context, AttributeSet attrs) {
        super(context, attrs);
        
        final Display display = ((Activity)context).getWindow().getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        displayDensity = metrics.density;
    }
    
    
    /**
     * Create a view showing a remote desktop connection
     * @param context Containing context (activity)
     * @param connection Connection settings
     * @param updateActivity Callback to run on UI thread after connection is set up
     */
    void initializeCanvas(ConnectionInfo connection,
            final Runnable updateActivity) {
        this.updateActivity = updateActivity;
        this.connection = connection;

        // Startup the connection thread with a progress dialog
        pd = new ProgressDialog(getContext());
        pd.setTitle(getContext().getString(R.string.info_progress_dialog_title));
        pd.setMessage(getContext().getString(R.string.info_progress_dialog_message));
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setProgressNumberFormat(null);
        pd.setProgressPercentFormat(null);
        pd.setMax(10000);
        pd.setIndeterminate(true);
        // Make this dialog cancellable only upon hitting the Back button and not touching outside.
        pd.setCanceledOnTouchOutside(false);
        pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
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
        pd.show();
        
        startControlConnection();
    }


    private void startControlConnection() {
        try {
            controlConn = new ControlConnectionProcessor(connection.getAddress(), connection.getPort());
            endpoint = new ClientProtocolEndpoint(controlConn, handler);
            new Thread(controlConn).start();
        } catch (IOException e) {
            Log.e(TAG, "Couldn't create ControlConnectionProcessor", e);
            showFatalMessageAndQuit(getContext().getString(R.string.error_connection_failed));
        }
    }


    /**
     * Starts a SPICE connection using libspice.
     */
    private void startSpiceConnection() {
        try {
            spice = new SpiceCommunicator(getContext(), this, handler, connection);
            viewport = new Viewport(spice, this);
            pointer = new RemotePointer(spice, this);
            keyboard = new RemoteKeyboard(spice);
            spice.connect();
        } catch (Throwable e) {
            if (maintainConnection) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                // Ensure we dismiss the progress dialog before we finish
                if (pd.isShowing())
                    pd.dismiss();

                if (e instanceof OutOfMemoryError) {
                    showFatalMessageAndQuit (getContext().getString(R.string.error_out_of_memory));
                } else {
                    String error = getContext().getString(R.string.error_connection_failed);
                    if (e.getMessage() != null) {
                        error = error + "<br>" + e.getLocalizedMessage();
                    }
                    showFatalMessageAndQuit(error);
                }
            }
        }
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
    
    public void restartVM() {
        endpoint.sendStopVM();
        // VM_STATE_STOPPED handler will restart it
    }
    
    /**
     * Method that disconnects from the remote server.
     */
    public void closeConnection() {
        maintainConnection = false;
        
        if (keyboard != null) {
            // Tell the server to release any meta keys.
            keyboard.clearOnScreenModifiers();
            keyboard.processLocalKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, 0));
        }
        // Close the SPICE connection.
        if (spice != null)
            spice.disconnect();
        // Close the control connection.
        if (controlConn != null) {
            try {
                wantVMState(Constants.VM_STATE_DESTROYED);
            } catch (IllegalStateException e) {}
            controlConn.close();
        }

        onDestroy();
    }
    
    
    /**
     * Cleans up resources after a disconnection.
     */
    private void onDestroy() {
        Log.v(TAG, "Cleaning up resources");
        
        handler.removeCallbacksAndMessages(null);

        updateActivity   = null;
        connection       = null;
        screenMessage    = null;
        viewport         = null;
        spice            = null;
        endpoint         = null;
        controlConn      = null;
    }
    
    /**
     * This runnable displays a message on the screen.
     */
    CharSequence screenMessage;
    private Runnable showMessage = new Runnable() {
            public void run() { Toast.makeText( getContext(), screenMessage, Toast.LENGTH_SHORT).show(); }
    };
    
    
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.actionLabel = null;
        outAttrs.inputType = InputType.TYPE_NULL;
        /* TODO: If people complain about kbd not working, this is a possible workaround to
         * test and add an option for.
        // Workaround for IME's that don't support InputType.TYPE_NULL.
        outAttrs.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_FULLSCREEN;
        */
        return new BaseInputConnection(this, false);
    }

    public Viewport getViewport() {
        return viewport;
    }

    public RemotePointer getPointer() {
        return pointer;
    }
    
    public RemoteKeyboard getKeyboard() {
        return keyboard;
    }
    
    public float getDisplayDensity() {
        return displayDensity;
    }
    
    public boolean getAbsoluteMouse() {
        return absoluteMouse;
    }

    public String getVMName() {
        return vmName;
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
            if (viewport != null) {
                // Ensure the view position is sane
                viewport.updateScale();
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////////////////
    //  Through the functions implemented below libspice communicates remote
    //  desktop size and updates.
    //////////////////////////////////////////////////////////////////////////////////
    
    void OnSettingsChanged(final int width, final int height) {
        android.util.Log.d(TAG, "onSettingsChanged called, wxh: " + width + "x" + height);
        
        // We need to initialize the communicator and remote keyboard and mouse now.
        waitUntilInflated();
        int remoteWidth  = getRemoteWidth(getWidth(), getHeight());
        int remoteHeight = getRemoteHeight(getWidth(), getHeight());
        if (width != remoteWidth || height != remoteHeight) {
            android.util.Log.d(TAG, "Requesting new res: " + remoteWidth + "x" + remoteHeight);
            spice.requestResolution(remoteWidth, remoteHeight);
        }

        // Notify viewport.
        if (viewport != null)
            viewport.OnSettingsChanged(width, height);

        // Update activity state.
        handler.post(updateActivity);

        // Initialize pointer to center of screen.
        if (!spiceUpdateReceived) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (absoluteMouse) {
                        pointer.processPointerEvent(width / 2, height / 2);
                    }
                }
            });
        }
        
        // Notify that we have a connection.
        spiceUpdateReceived = true;
        handler.sendEmptyMessage(Constants.SPICE_CONNECT_SUCCESS);
    }

    void OnMouseMode(final boolean absoluteMouse) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                RemoteCanvas.this.absoluteMouse = absoluteMouse;
            }
        });
        handler.post(updateActivity);
    }

    private void wantVMState(int wanted) {
        // Only handle transitions for which we can usefully issue a
        // command.  Other transitions will be handled after a
        // subsequent event.
        switch (wanted) {
        case Constants.VM_STATE_RUNNING:
            if (vmState == Constants.VM_STATE_STOPPED) {
                endpoint.sendStartVM();
                vmState = Constants.VM_STATE_STARTING;
            }
            break;

        case Constants.VM_STATE_STOPPED:
            if (vmState == Constants.VM_STATE_STARTING || vmState == Constants.VM_STATE_RUNNING) {
                endpoint.sendStopVM();
                vmState = Constants.VM_STATE_STOPPING;
            }
            break;

        case Constants.VM_STATE_DESTROYED:
            if (vmState != Constants.VM_STATE_DESTROYED) {
                endpoint.sendDestroyVM();
                vmState = Constants.VM_STATE_DESTROYED;
            }
            break;
        }
    }


    private class PingerRunnable implements Runnable {
        private static final int INTERVAL = 5000;
        private static final int COUNT = 5;

        private int outstanding = 0;

        public void start() {
            stop();
            schedule();
        }

        public void stop() {
            handler.removeCallbacks(this);
        }

        public void pong() {
            outstanding = 0;
        }

        private void schedule() {
            handler.postDelayed(this, INTERVAL);
        }

        @Override
        public void run() {
            if (outstanding < COUNT) {
                endpoint.sendPing();
                outstanding += 1;
                schedule();
            } else {
                Log.w(TAG, "Control connection timed out");
                controlConn.close();
            }
        }
    };
    private final PingerRunnable pinger = new PingerRunnable();


    /** 
     * Handler for connection events.
     */
    private final Handler handler = new Handler() {
        private void showProtocolErrorAndQuit(String error) {
            showFatalMessageAndQuit(getContext().getString(R.string.error_protocol) + " " + error);
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle args = msg.getData();
            String error;

            switch (msg.what) {
            case Constants.SPICE_CONNECT_SUCCESS:
                if (pd != null && pd.isShowing()) {
                    pd.dismiss();
                }
                break;

            case Constants.SPICE_CONNECT_FAILURE:
                // Data connection failed; retry
                if (maintainConnection) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startSpiceConnection();
                        }
                    }, 1000);
                }
                break;

            case Constants.PROTOCOL_CONNECTED:
                Log.d(TAG, "connected");
                endpoint.sendAuthenticate(connection.getToken());
                break;

            case Constants.PROTOCOL_ERROR:
                error = args.getString(Constants.ARG_ERROR);
                Log.d(TAG, "error " + error);
                showProtocolErrorAndQuit(error);
                break;

            case Constants.PROTOCOL_DISCONNECTED:
                Log.d(TAG, "disconnected");
                vmState = Constants.VM_STATE_UNKNOWN;
                pinger.stop();
                if (maintainConnection) {
                    if (pd != null && pd.isShowing()) {
                        pd.dismiss();
                    }
                    if (!spiceUpdateReceived) {
                        showFatalMessageAndQuit(getContext().getString(R.string.error_connection_failed));
                    } else {
                        showFatalMessageAndQuit(getContext().getString(R.string.error_connection_interrupted));
                    }
                }
                break;

            case Constants.CLIENT_PROTOCOL_AUTH_OK:
                vmName = args.getString(Constants.ARG_VM_NAME);
                vmState = args.getInt(Constants.ARG_VM_STATE);
                int maxMouseRate = args.getInt(Constants.ARG_MAX_MOUSE_RATE);
                Log.d(TAG, "auth ok " + vmName + " " + Integer.toString(vmState) + " " + Integer.toString(maxMouseRate));

                // Start pinging
                pinger.start();

                // Update window title
                post(updateActivity);

                // If the VM is in a stable state, synthesize a state transition.
                switch (vmState) {
                case Constants.VM_STATE_RUNNING:
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(Constants.ARG_CHECK_DISPLAY, false);
                    Message message = handler.obtainMessage(Constants.CLIENT_PROTOCOL_VM_STARTED);
                    message.setData(bundle);
                    handler.sendMessage(message);
                    break;
                case Constants.VM_STATE_STOPPED:
                    handler.sendEmptyMessage(Constants.CLIENT_PROTOCOL_VM_STOPPED);
                    break;
                }
                break;

            case Constants.CLIENT_PROTOCOL_AUTH_FAILED:
                error = args.getString(Constants.ARG_ERROR);
                Log.d(TAG, "auth failed " + error);
                showProtocolErrorAndQuit(error);
                break;

            case Constants.CLIENT_PROTOCOL_STARTUP_PROGRESS:
                double progress = args.getDouble(Constants.ARG_PROGRESS);
                if (pd.isIndeterminate() && progress > 0) {
                    pd.setIndeterminate(false);
                    pd.setProgressPercentFormat(NumberFormat.getPercentInstance());
                }
                pd.setProgress((int) (progress * pd.getMax()));
                break;

            case Constants.CLIENT_PROTOCOL_STARTUP_REJECTED_MEMORY:
                Log.d(TAG, "rejected memory");
                break;

            case Constants.CLIENT_PROTOCOL_STARTUP_FAILED:
                error = args.getString(Constants.ARG_ERROR);
                Log.d(TAG, "startup failed " + error);
                showProtocolErrorAndQuit(error);
                break;

            case Constants.CLIENT_PROTOCOL_VM_STARTED:
                boolean checkDisplay = args.getBoolean(Constants.ARG_CHECK_DISPLAY);
                Log.d(TAG, "VM started, check: " + Boolean.toString(checkDisplay));
                vmState = Constants.VM_STATE_RUNNING;
                if (spice == null)
                    startSpiceConnection();
                break;

            case Constants.CLIENT_PROTOCOL_VM_STOPPED:
                Log.d(TAG, "VM stopped");
                vmState = Constants.VM_STATE_STOPPED;
                wantVMState(Constants.VM_STATE_RUNNING);
                break;

            case Constants.CLIENT_PROTOCOL_VM_DESTROYED:
                Log.d(TAG, "VM destroyed");
                vmState = Constants.VM_STATE_DESTROYED;
                if (maintainConnection) {
                    showFatalMessageAndQuit(getContext().getString(R.string.error_vm_terminated));
                }
                break;

            case Constants.CLIENT_PROTOCOL_PONG:
                //Log.d(TAG, "pong!");
                pinger.pong();
                break;

            default:
                Log.w(TAG, "Handler received unknown message " + Integer.toString(msg.what));
                break;
            }
        }
    };
}
