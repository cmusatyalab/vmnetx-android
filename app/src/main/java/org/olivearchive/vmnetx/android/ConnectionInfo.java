/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2009 Michael A. MacDonald
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

package org.olivearchive.vmnetx.android;

import java.lang.Comparable;
import java.io.Serializable;

/**
 * @author Iordan Iordanov
 * @author Michael A. MacDonald
 *
 */
public class ConnectionInfo implements Comparable<ConnectionInfo>,
        Serializable {
    // for Serializable
    private static final long serialVersionUID = 1;
    private static final int DEFAULT_PORT = 18923;

    private String address;
    private int port;
    private String token;

    ConnectionInfo(String address, int port, String token) {
        this.address = address != null ? address : "";
        this.port = port > 0 ? port : DEFAULT_PORT;
        this.token = token != null ? token : "";
    }
    
    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return getAddress() + ":" + getPort();
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(ConnectionInfo another) {
        int result = getAddress().compareTo(another.getAddress());
        if ( result == 0) {
            result = getPort() - another.getPort();
        }
        return result;
    }
}
