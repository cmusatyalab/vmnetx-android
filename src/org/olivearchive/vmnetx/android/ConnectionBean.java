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
public class ConnectionBean implements Comparable<ConnectionBean>, Serializable {
    // for Serializable
    private static final long serialVersionUID = 1;

    private String address;
    private int port;
    private String token;
    private boolean extraKeys;
    private boolean rotateDpad;

    ConnectionBean()
    {
        setAddress("");
        setPort(18923);
        setToken("");
        setRotateDpad(false);
        setExtraKeys(true);
    }
    
    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean getExtraKeys() {
        return extraKeys;
    }

    public void setExtraKeys(boolean extraKeys) {
        this.extraKeys = extraKeys;
    }

    public boolean getRotateDpad() {
        return rotateDpad;
    }

    public void setRotateDpad(boolean rotateDpad) {
        this.rotateDpad = rotateDpad;
    }

    @Override
    public String toString() {
        return getAddress() + ":" + getPort();
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(ConnectionBean another) {
        int result = getAddress().compareTo(another.getAddress());
        if ( result == 0) {
            result = getPort() - another.getPort();
        }
        return result;
    }
}
