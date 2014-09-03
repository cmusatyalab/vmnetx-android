/*
 * Copyright (C) 2014 Carnegie Mellon University
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import net.asdfa.msgpack.MsgPack;

abstract class ProtocolEndpoint {
    static private final String MTYPE_KEY = "_";

    static protected final class RecvMessage {
        static private final String TAG = "RecvMessage";

        public final String mtype;
        private final Map<Object, Object> items;

        public RecvMessage(byte[] data) throws ProtocolException {
            Object o;
            try {
                o = MsgPack.unpack(data, MsgPack.UNPACK_RAW_AS_STRING);
            } catch (IOException e) {
                throw new ProtocolException("MessagePack decode failure", e);
            }
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> om = (Map<Object, Object>) o;
                items = om;

                Object mt = items.remove(MTYPE_KEY);
                if (mt != null && mt instanceof String)
                    mtype = (String) mt;
                else
                    throw new ProtocolException("Received message without a type");
                android.util.Log.d(TAG, "Received message: " + mtype);
            } else {
                throw new ProtocolException("Received message is not a map object");
            }
        }

        private Object get(Class<?> cls, String key) throws ProtocolException {
            Object o = get(cls, key, null);
            if (o != null)
                return o;
            else
                throw new ProtocolException("Missing required value " + key + " in message " + mtype);
        }

        private Object get(Class<?> cls, String key, Object defaul) throws ProtocolException {
            if (defaul != null && !cls.isInstance(defaul))
                throw new IllegalArgumentException("defaul is not an instance of cls");
            Object o = items.get(key);
            if (o != null) {
                if (cls.isInstance(o))
                    return o;
                else
                    throw new ProtocolException("Invalid type for field " + key + " in message " + mtype);
            } else {
                return defaul;
            }
        }

        public String getString(String key) throws ProtocolException {
            return (String) get(String.class, key);
        }

        public String getString(String key, String defaul) throws ProtocolException {
            return (String) get(String.class, key, defaul);
        }

        public int getVmState(String key) throws ProtocolException {
            String vmState = getString(key);
            if (vmState.equals("stopped"))
                return Constants.VM_STATE_STOPPED;
            else if (vmState.equals("starting"))
                return Constants.VM_STATE_STARTING;
            else if (vmState.equals("running"))
                return Constants.VM_STATE_RUNNING;
            else if (vmState.equals("stopping"))
                return Constants.VM_STATE_STOPPING;
            else
                return Constants.VM_STATE_UNKNOWN;
        }

        public int getInt(String key) throws ProtocolException {
            return (Integer) get(Integer.class, key);
        }

        public int getInt(String key, int defaul) throws ProtocolException {
            return (Integer) get(Integer.class, key, defaul);
        }

        public double getDouble(String key) throws ProtocolException {
            return (Double) get(Double.class, key);
        }

        public double getDouble(String key, double defaul) throws ProtocolException {
            return (Double) get(Double.class, key, defaul);
        }

        public boolean getBoolean(String key) throws ProtocolException {
            return (Boolean) get(Boolean.class, key);
        }

        public boolean getBoolean(String key, boolean defaul) throws ProtocolException {
            return (Boolean) get(Boolean.class, key, defaul);
        }
    }

    // The object that receives decoded messages from us.
    public interface MessageProcessor {
        void processMessage(int what, Bundle args);
    }

    private static class HandlerMessageProcessor implements MessageProcessor {
        private final Handler handler;

        public HandlerMessageProcessor(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void processMessage(int what, Bundle args) {
            Message message = handler.obtainMessage(what);
            if (args != null)
                message.setData(args);
            handler.sendMessage(message);
        }
    }

    private final ConnectionProcessor conn;
    private final MessageProcessor target;

    ProtocolEndpoint(ConnectionProcessor conn, Handler handler) {
        this(conn, new HandlerMessageProcessor(handler));
    }

    ProtocolEndpoint(ConnectionProcessor conn, MessageProcessor target) {
        this.conn = conn;
        this.target = target;
        conn.setEndpoint(this);
    }

    void connected() {
        emit(Constants.PROTOCOL_CONNECTED);
    }

    void dispatch(byte[] data) throws ProtocolException {
        dispatch(new RecvMessage(data));
    }

    protected void dispatch(RecvMessage msg) throws ProtocolException {
        if (msg.mtype.equals("error")) {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ARG_ERROR, msg.getString("message"));
            emit(Constants.PROTOCOL_ERROR, bundle);

        } else {
            throw new ProtocolException("Received unknown message of type " + msg.mtype);
        }
    }

    protected void transmit(String mtype) {
        transmit(mtype, null);
    }

    protected void transmit(String mtype, Map<String, Object> args) {
        Map<String, Object> o = new HashMap<String, Object>();
        if (args != null)
            o.putAll(args);
        o.put(MTYPE_KEY, mtype);
        byte[] data = MsgPack.pack(o);
        conn.send(data);
    }

    protected void emit(int what) {
        target.processMessage(what, null);
    }

    protected void emit(int what, Bundle args) {
        target.processMessage(what, args);
    }
}
