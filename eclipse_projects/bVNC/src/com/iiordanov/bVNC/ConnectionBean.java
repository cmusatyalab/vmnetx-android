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

package com.iiordanov.bVNC;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.widget.ImageView.ScaleType;

import com.antlersoft.android.db.FieldAccessor;
import com.antlersoft.android.dbimpl.NewInstance;
import com.iiordanov.bVNC.RemoteCanvasActivity;
import com.iiordanov.bVNC.input.TouchMouseDragPanInputHandler;

import java.lang.Comparable;
import android.content.Context;

/**
 * @author Iordan Iordanov
 * @author Michael A. MacDonald
 *
 */
public class ConnectionBean extends AbstractConnectionBean implements Comparable<ConnectionBean> {
    
    static Context c = null;
    
    static final NewInstance<ConnectionBean> newInstance=new NewInstance<ConnectionBean>() {
        public ConnectionBean get() { return new ConnectionBean(c); }
    };
    ConnectionBean(Context context)
    {
        set_Id(0);
        setAddress("");
        setPassword("");
        setKeepPassword(true);
        setNickname("");
        setUserName("");
        setRdpDomain("");
        setPort(5900);
        setScaleMode(ScaleType.MATRIX);
        setInputMode(TouchMouseDragPanInputHandler.TOUCH_ZOOM_MODE_DRAG_PAN);
        setUseDpadAsArrows(true);
        setRotateDpad(false);
        setUsePortrait(false);
        setUseLocalCursor(false);
        setRepeaterId("");
        setExtraKeysToggleType(1);
        setRdpColor(0);
        setRemoteFx(false);
        setDesktopBackground(false);
        setFontSmoothing(false);
        setDesktopComposition(false);
        setWindowContents(false);
        setMenuAnimation(false);
        setVisualStyles(false);
        setConsoleMode(false);
        setRedirectSdCard(false);
        setViewOnly(false);
        c = context;
    }
    
    boolean isNew()
    {
        return get_Id()== 0;
    }
    
    public synchronized void save(SQLiteDatabase database) {
        ContentValues values=Gen_getValues();
        values.remove(GEN_FIELD__ID);
        if ( ! getKeepPassword()) {
            values.put(GEN_FIELD_PASSWORD, "");
        }
        if ( isNew()) {
            set_Id(database.insert(GEN_TABLE_NAME, null, values));
        } else {
            database.update(GEN_TABLE_NAME, values, GEN_FIELD__ID + " = ?", new String[] { Long.toString(get_Id()) });
        }
    }
    
    ScaleType getScaleMode()
    {
        return ScaleType.valueOf(getScaleModeAsString());
    }
    
    void setScaleMode(ScaleType value)
    {
        setScaleModeAsString(value.toString());
    }
    
    @Override
    public String toString() {
        if (isNew())
        {
            return c.getString(R.string.new_connection);
        }
        String result = new String("");
        
        // Add the nickname if it has been set.
        if (!getNickname().equals(""))
            result += getNickname()+":";
        
        // Add the VNC server and port.
        result += getAddress()+":"+getPort();
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(ConnectionBean another) {
        int result = getNickname().compareTo(another.getNickname());
        if (result == 0) {
            result = getAddress().compareTo(another.getAddress());
        }
        if ( result == 0) {
            result = getPort() - another.getPort();
        }
        return result;
    }
}
