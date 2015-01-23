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

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.widget.ImageView;

/**
 * Interface between the RemoteCanvas and the bitmap data buffers that actually contain
 * the data.
 * @author Michael A. MacDonald
 */
class BitmapData {
    static private final Bitmap.Config cfg = Bitmap.Config.ARGB_8888;

    Bitmap mbitmap;
    private final BitmapDrawable drawable = new BitmapDrawable(this);

    void moveCursor(int x, int y) {
        drawable.moveCursor(x, y);
    }

    void setSoftCursor(int[] newSoftCursorPixels, int w, int h, int hX, int hY) {
        drawable.setSoftCursor(newSoftCursorPixels, w, h, hX, hY);
    }

    void clearSoftCursor() {
        drawable.clearSoftCursor();
    }

    Rect getCursorRect() {
        return drawable.getCursorRect();
    }

    /**
     * Sets the canvas's drawable
     * @param v ImageView displaying bitmap data
     */
    void setImageDrawable(ImageView v)
    {
        v.setImageDrawable(drawable);
    }

    void setFilteringEnabled(boolean enabled) {
        drawable.setFilteringEnabled(enabled);
    }

    /**
     * Remote framebuffer size has changed.
     * <p>
     * This method is called when the framebuffer has changed size and reinitializes the
     * necessary data structures to support that change.
     */
    void setDimensions(int width, int height) {
        if (mbitmap != null) {
            try {
                mbitmap.reconfigure(width, height, cfg);
            } catch (IllegalArgumentException e) {
                mbitmap = null;
            }
        }
        if (mbitmap == null) {
            mbitmap = Bitmap.createBitmap(width, height, cfg);
            mbitmap.setHasAlpha(false);
        }
    }
    
    int getWidth() {
        if (mbitmap != null)
            return mbitmap.getWidth();
        else
            return 1;
    }

    int getHeight() {
        if (mbitmap != null)
            return mbitmap.getHeight();
        else
            return 1;
    }
}
