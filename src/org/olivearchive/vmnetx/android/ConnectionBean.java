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

import android.widget.ImageView.ScaleType;

import org.olivearchive.vmnetx.android.input.TouchMouseDragPanInputHandler;

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
    private String password;
    private boolean extraKeys;
    private String inputMode;
    private String scaleMode;
    private boolean useDpadAsArrows;
    private boolean rotateDpad;
    private boolean followMouse;
    private boolean followPan;

    ConnectionBean()
    {
        setAddress("");
        setPassword("");
        setPort(5900);
        setScaleMode(ScaleType.MATRIX);
        setInputMode(TouchMouseDragPanInputHandler.TOUCH_ZOOM_MODE_DRAG_PAN);
        setUseDpadAsArrows(true);
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean getExtraKeys() {
        return extraKeys;
    }

    public void setExtraKeys(boolean extraKeys) {
        this.extraKeys = extraKeys;
    }

    public String getInputMode() {
        return inputMode;
    }

    public void setInputMode(String inputMode) {
        this.inputMode = inputMode;
    }

    ScaleType getScaleMode()
    {
        return ScaleType.valueOf(getScaleModeAsString());
    }
    
    void setScaleMode(ScaleType value)
    {
        setScaleModeAsString(value.toString());
    }
    
    public String getScaleModeAsString() {
        return scaleMode;
    }

    public void setScaleModeAsString(String scaleMode) {
        this.scaleMode = scaleMode;
    }

    public boolean getUseDpadAsArrows() {
        return useDpadAsArrows;
    }

    public void setUseDpadAsArrows(boolean useDpadAsArrows) {
        this.useDpadAsArrows = useDpadAsArrows;
    }

    public boolean getRotateDpad() {
        return rotateDpad;
    }

    public void setRotateDpad(boolean rotateDpad) {
        this.rotateDpad = rotateDpad;
    }

    public boolean getFollowMouse() {
        return followMouse;
    }

    public void setFollowMouse(boolean followMouse) {
        this.followMouse = followMouse;
    }

    public boolean getFollowPan() {
        return followPan;
    }

    public void setFollowPan(boolean followPan) {
        this.followPan = followPan;
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
