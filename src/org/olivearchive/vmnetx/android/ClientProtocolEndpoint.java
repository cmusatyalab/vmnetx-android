/*
 * Copyright (C) 2013-2014 Carnegie Mellon University
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of version 2 of the GNU General Public License as published
 * by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.olivearchive.vmnetx.android;

import java.util.HashMap;
import java.util.Map;

import android.os.Bundle;
import android.os.Handler;

class ClientProtocolEndpoint extends ProtocolEndpoint {
    private static final String TAG = "ClientProtocolEndpoint";

    protected static final int STATE_UNAUTHENTICATED = 0;
    protected static final int STATE_AUTHENTICATING = 1;
    protected static final int STATE_RUNNING = 2;
    protected static final int STATE_ATTACHING_VIEWER = 3;
    protected static final int STATE_VIEWER = 4;

    protected int state = STATE_UNAUTHENTICATED;

    ClientProtocolEndpoint(ConnectionProcessor conn, Handler handler) {
        super(conn, handler);
    }

    ClientProtocolEndpoint(ConnectionProcessor conn, MessageProcessor target) {
        super(conn, target);
    }

    protected void needDispatchState(int state) throws ProtocolException {
        if (state != this.state)
            throw new ProtocolException("Invalid state for operation");
    }

    protected void needSendState(int state) {
        if (state != this.state)
            throw new IllegalStateException("Invalid state for operation");
    }

    @Override
    protected void dispatch(RecvMessage msg) throws ProtocolException {
        if (msg.mtype.equals("auth-ok")) {
            needDispatchState(STATE_AUTHENTICATING);
            state = STATE_RUNNING;
            Bundle bundle = new Bundle();
            bundle.putInt(Constants.ARG_VM_STATE, msg.getVmState("state"));
            bundle.putString(Constants.ARG_VM_NAME, msg.getString("name"));
            bundle.putInt(Constants.ARG_MAX_MOUSE_RATE, msg.getInt("limit_mouse_rate", 0));
            emit(Constants.CLIENT_PROTOCOL_AUTH_OK, bundle);

        } else if (msg.mtype.equals("auth-failed")) {
            needDispatchState(STATE_AUTHENTICATING);
            state = STATE_UNAUTHENTICATED;
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ARG_ERROR, msg.getString("error"));
            emit(Constants.CLIENT_PROTOCOL_AUTH_FAILED, bundle);

        } else if (msg.mtype.equals("attaching-viewer")) {
            needDispatchState(STATE_ATTACHING_VIEWER);
            state = STATE_VIEWER;
            emit(Constants.CLIENT_PROTOCOL_ATTACHING_VIEWER);

        } else if (msg.mtype.equals("startup-progress")) {
            needDispatchState(STATE_RUNNING);
            Bundle bundle = new Bundle();
            bundle.putDouble(Constants.ARG_PROGRESS, msg.getDouble("fraction"));
            emit(Constants.CLIENT_PROTOCOL_STARTUP_PROGRESS, bundle);

        } else if (msg.mtype.equals("startup-rejected-memory")) {
            needDispatchState(STATE_RUNNING);
            emit(Constants.CLIENT_PROTOCOL_STARTUP_REJECTED_MEMORY);

        } else if (msg.mtype.equals("startup-failed")) {
            needDispatchState(STATE_RUNNING);
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ARG_ERROR, msg.getString("message"));
            emit(Constants.CLIENT_PROTOCOL_STARTUP_FAILED, bundle);

        } else if (msg.mtype.equals("vm-started")) {
            needDispatchState(STATE_RUNNING);
            Bundle bundle = new Bundle();
            bundle.putBoolean(Constants.ARG_CHECK_DISPLAY, msg.getBoolean("check_display"));
            emit(Constants.CLIENT_PROTOCOL_VM_STARTED, bundle);

        } else if (msg.mtype.equals("vm-stopped")) {
            if (state == STATE_ATTACHING_VIEWER) {
                // Could happen on viewer connections while the setup
                // handshake is running
                return;
            }
            needDispatchState(STATE_RUNNING);
            emit(Constants.CLIENT_PROTOCOL_VM_STOPPED);

        } else if (msg.mtype.equals("vm-destroyed")) {
            if (state == STATE_ATTACHING_VIEWER) {
                // Could happen on viewer connections while the setup
                // handshake is running
                return;
            }
            needDispatchState(STATE_RUNNING);
            emit(Constants.CLIENT_PROTOCOL_VM_DESTROYED);

        } else if (msg.mtype.equals("pong")) {
            emit(Constants.CLIENT_PROTOCOL_PONG);

        } else {
            super.dispatch(msg);
        }
    }

    public void sendAuthenticate(String token) {
        needSendState(STATE_UNAUTHENTICATED);
        state = STATE_AUTHENTICATING;
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("token", token);
        transmit("authenticate", args);
    }

    public void sendAttachViewer() {
        needSendState(STATE_RUNNING);
        state = STATE_ATTACHING_VIEWER;
        transmit("attach-viewer");
    }

    public void sendStartVM() {
        needSendState(STATE_RUNNING);
        transmit("start-vm");
    }

    public void sendStopVM() {
        needSendState(STATE_RUNNING);
        transmit("stop-vm");
    }

    public void sendDestroyVM() {
        needSendState(STATE_RUNNING);
        transmit("destroy-vm");
    }

    public void sendPing() {
        needSendState(STATE_RUNNING);
        transmit("ping");
    }
}
