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

package org.olivearchive.vmnetx.android.protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.util.Log;

public class ControlConnectionProcessor extends ConnectionProcessor
        implements Runnable {
    static private final String TAG = "ControlConnectionProcessor";

    static private final int HEADER_SIZE = 4;
    static private final int MAX_MESSAGE_SIZE = 1 << 20;
    static private final int DEFAULT_OPS = SelectionKey.OP_READ;

    private final String host;
    private final int port;
    private final Selector selector;
    private final ConcurrentLinkedQueue<ByteBuffer> sendQueue = new ConcurrentLinkedQueue<ByteBuffer>();

    private ProtocolEndpoint endpoint;
    private volatile boolean exit = false;

    // I/O thread private state
    private SocketChannel channel;
    private SelectionKey key;
    private ByteBuffer sendBuf = null;
    private ByteBuffer recvBuf = ByteBuffer.allocate(HEADER_SIZE);
    private boolean recvInLength = true;

    public ControlConnectionProcessor(String host, int port)
            throws IOException {
        this.host = host;
        this.port = port;
        selector = Selector.open();
    }

    @Override
    void setEndpoint(ProtocolEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    void send(byte[] data) {
        ByteBuffer buf = ByteBuffer.allocate(data.length + HEADER_SIZE);
        buf.putInt(data.length);
        buf.put(data);
        buf.rewind();
        sendQueue.add(buf);
        wakeup();
    }

    public void close() {
        exit = true;
        wakeup();
    }

    private void connect() throws IOException {
        // Connect synchronously
        InetAddress address = InetAddress.getByName(host);
        channel = SocketChannel.open(new InetSocketAddress(address, port));
        channel.configureBlocking(false);
        channel.socket().setTcpNoDelay(true);
        key = channel.register(selector, DEFAULT_OPS, null);
        endpoint.connected();
    }

    private void trySend() throws IOException {
        while (true) {
            if (sendBuf == null)
                sendBuf = sendQueue.poll();
            if (sendBuf == null) {
                key.interestOps(DEFAULT_OPS);
                return;
            }
            channel.write(sendBuf);
            if (sendBuf.hasRemaining()) {
                key.interestOps(DEFAULT_OPS | SelectionKey.OP_WRITE);
                return;
            }
            sendBuf = null;
        }
    }

    private void tryRecv() throws IOException {
        while (true) {
            // Read bytes
            if (channel.read(recvBuf) == -1) {
                // Connection closed
                close();
                return;
            }
            if (recvBuf.hasRemaining())
                return;
            recvBuf.rewind();

            // Process bytes
            if (recvInLength) {
                // Set up for data
                int length = recvBuf.getInt();
                if (length > MAX_MESSAGE_SIZE)
                    throw new ProtocolException("Received oversize message of length " + Integer.toString(length));
                if (length > recvBuf.capacity()) {
                    // Resize to next larger power of 2
                    recvBuf = ByteBuffer.allocate(Integer.highestOneBit(length) << 1);
                }
                recvBuf.rewind();
                recvBuf.limit(length);
                recvInLength = false;
            } else {
                // Process data
                byte[] data = new byte[recvBuf.limit()];
                recvBuf.get(data);
                endpoint.dispatch(data);

                // Set up for length
                recvBuf.rewind();
                recvBuf.limit(HEADER_SIZE);
                recvInLength = true;
            }
        }
    }

    private void wakeup() {
        try {
            selector.wakeup();
        } catch (Exception e) {
            // Should be impossible to get IOExceptions, but Lollipop
            // throws them
            // https://code.google.com/p/android/issues/detail?id=80785
            if (!(e instanceof IOException))
                throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        // Dedicated I/O thread for the connection.  We use non-blocking
        // I/O so send and receive won't interfere with each other.
        try {
            connect();
            while (!exit) {
                trySend();
                tryRecv();
                selector.select();
            }
        } catch (IOException e) {
            Log.e(TAG, "Control connection error", e);
        } finally {
            try {
                if (channel != null)
                    channel.close();
                selector.close();
            } catch (IOException e) {}
            endpoint.disconnected();
        }
    }
}
