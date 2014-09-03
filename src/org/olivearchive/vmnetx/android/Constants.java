/**
 * Copyright (C) 2014 Carnegie Mellon University
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2010 Michael A. MacDonald
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

package org.olivearchive.vmnetx.android;

/**
 * Keys for intent values
 */
public class Constants {
    // SPICE connection state transitions
    // no arguments
    public static final int SPICE_CONNECT_SUCCESS  = 1;
    // no arguments
    public static final int SPICE_CONNECT_FAILURE  = 2;

    // Base protocol events
    // no arguments
    public static final int PROTOCOL_CONNECTED = 101;
    // ARG_ERROR
    public static final int PROTOCOL_ERROR = 102;
    // no arguments
    public static final int PROTOCOL_CLOSED = 103;

    // Client protocol events
    // ARG_VM_STATE, ARG_VM_NAME, ARG_MAX_MOUSE_RATE
    public static final int CLIENT_PROTOCOL_AUTH_OK = 201;
    // ARG_ERROR
    public static final int CLIENT_PROTOCOL_AUTH_FAILED = 202;
    // no arguments
    public static final int CLIENT_PROTOCOL_ATTACHING_VIEWER = 203;
    // ARG_PROGRESS
    public static final int CLIENT_PROTOCOL_STARTUP_PROGRESS = 204;
    // no arguments
    public static final int CLIENT_PROTOCOL_STARTUP_REJECTED_MEMORY = 206;
    // ARG_ERROR
    public static final int CLIENT_PROTOCOL_STARTUP_FAILED = 207;
    // ARG_CHECK_DISPLAY
    public static final int CLIENT_PROTOCOL_VM_STARTED = 208;
    // no arguments
    public static final int CLIENT_PROTOCOL_VM_STOPPED = 209;
    // no arguments
    public static final int CLIENT_PROTOCOL_VM_DESTROYED = 210;
    // no arguments
    public static final int CLIENT_PROTOCOL_PONG = 211;

    // Arguments
    // String
    public static final String ARG_ERROR = "error";
    // double
    public static final String ARG_PROGRESS = "progress";
    // boolean
    public static final String ARG_CHECK_DISPLAY = "check-display";
    // VM_STATE_* int
    public static final String ARG_VM_STATE = "state";
    // String
    public static final String ARG_VM_NAME = "vm-name";
    // int
    public static final String ARG_MAX_MOUSE_RATE = "max-mouse-rate";

    // VM states
    public static final int VM_STATE_UNKNOWN = 0;
    public static final int VM_STATE_STOPPED = 1;
    public static final int VM_STATE_STARTING = 2;
    public static final int VM_STATE_RUNNING = 3;
    public static final int VM_STATE_STOPPING = 4;
}
