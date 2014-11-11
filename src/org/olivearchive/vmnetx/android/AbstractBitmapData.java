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
import android.graphics.Canvas;
import android.graphics.RectF;
import android.widget.ImageView;
import android.util.Log;

/**
 * Abstract interface between the VncCanvas and the bitmap and pixel data buffers that actually contain
 * the data.
 * This allows for implementations that use smaller bitmaps or buffers to save memory. 
 * @author Michael A. MacDonald
 *
 */
abstract public class AbstractBitmapData {
    int framebufferwidth;
    int framebufferheight;
    int bitmapwidth;
    int bitmapheight;
    Bitmap mbitmap;
    protected SpiceCommunicator spice;
    protected int bitmapPixels[];
    protected Canvas memGraphics;
    private RemoteCanvas vncCanvas;
    public AbstractBitmapDrawable drawable;

    AbstractBitmapData(SpiceCommunicator s, RemoteCanvas c)
    {
        spice = s;
        vncCanvas = c;
        framebufferwidth  = spice.framebufferWidth();
        framebufferheight = spice.framebufferHeight();
        drawable = createDrawable();
    }

    void setCursorRect(int x, int y, int w, int h, int hX, int hY) {
        if (drawable != null)
            drawable.setCursorRect(x, y, w, h, hX, hY);
    }

    void moveCursorRect(int x, int y) {
        if (drawable != null)
            drawable.moveCursorRect(x, y);
    }

    void setSoftCursor (int[] newSoftCursorPixels) {
        if (drawable != null)
            drawable.setSoftCursor (newSoftCursorPixels);
    }

    RectF getCursorRect () {
        if (drawable != null)
            return drawable.cursorRect;
        else // Return an empty new rectangle if drawable is null.
            return new RectF();
    }

    boolean isNotInitSoftCursor() {
        if (drawable != null)
            return (drawable.softCursorInit == false);
        else
            return false;
    }

    /**
     * 
     * @return The smallest scale supported by the implementation; the scale at which
     * the bitmap would be smaller than the screen
     */
    float getMinimumScale() {
        return Math.min((float)vncCanvas.getWidth()/bitmapwidth, (float)vncCanvas.getHeight()/bitmapheight);
    }

    /**
     * Determine if a rectangle in full-frame coordinates can be drawn in the existing buffer
     * @param x Top left x
     * @param y Top left y
     * @param w width (pixels)
     * @param h height (pixels)
     * @return True if entire rectangle fits into current screen buffer, false otherwise
     */
    public abstract boolean validDraw(int x, int y, int w, int h);

    /**
     * Return an offset in the bitmapPixels array of a point in full-frame coordinates
     * @param x
     * @param y
     * @return Offset in bitmapPixels array of color data for that point
     */
    public abstract int offset(int x, int y);

    /**
     * Update pixels in the bitmap with data from the bitmapPixels array, positioned
     * in full-frame coordinates
     * @param x Top left x
     * @param y Top left y
     * @param w width (pixels)
     * @param h height (pixels)
     */
    public abstract void updateBitmap(int x, int y, int w, int h);

    /**
     * Update pixels in the bitmap with data from the given bitmap, positioned
     * in full-frame coordinates
     * @param b The bitmap to copy from.
     * @param x Top left x
     * @param y Top left y
     * @param w width (pixels)
     * @param h height (pixels)
     */
    public abstract void updateBitmap(Bitmap b, int x, int y, int w, int h);

    /**
     * Create drawable appropriate for this data
     * @return drawable
     */
    abstract AbstractBitmapDrawable createDrawable();


    /**
     * Sets the canvas's drawable
     * @param v ImageView displaying bitmap data
     */
    void setImageDrawable(ImageView v)
    {
        v.setImageDrawable(drawable);
    }

    /**
     * Remote framebuffer size has changed.
     * <p>
     * This method is called when the framebuffer has changed size and reinitializes the
     * necessary data structures to support that change.
     */
    public abstract void frameBufferSizeChanged ();
    
    /**
     * Release resources
     */
    void dispose() {
        if (drawable != null)
            drawable.dispose();
        drawable = null;

        if (mbitmap != null)
            mbitmap.recycle();
        mbitmap      = null;

        memGraphics  = null;
        bitmapPixels = null;
    }
    
    public int fbWidth () {
        return framebufferwidth;
    }

    public int fbHeight () {
        return framebufferheight;
    }
    
    public int bmWidth () {
        return bitmapwidth;
    }

    public int bmHeight () {
        return bitmapheight;
    }
}
