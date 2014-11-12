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
 * Interface between the RemoteCanvas and the bitmap data buffers that actually contain
 * the data.
 * @author Michael A. MacDonald
 */
public class BitmapData {
    static private final Bitmap.Config cfg = Bitmap.Config.ARGB_8888;

    class BitmapDrawable extends AbstractBitmapDrawable {
        BitmapDrawable() {
            super(BitmapData.this);
        }

        /* (non-Javadoc)
         * @see android.graphics.drawable.DrawableContainer#draw(android.graphics.Canvas)
         */
        @Override
        public void draw(Canvas canvas) {
            try {
                synchronized (mbitmap) {
                    canvas.drawBitmap(data.mbitmap, 0.0f, 0.0f, _defaultPaint);
                    canvas.drawBitmap(softCursor, cursorRect.left, cursorRect.top, _defaultPaint);
                }
            } catch (Throwable e) { }
        }
    }

    int framebufferwidth;
    int framebufferheight;
    int bitmapwidth;
    int bitmapheight;
    Bitmap mbitmap;
    private SpiceCommunicator spice;
    private RemoteCanvas canvas;
    public AbstractBitmapDrawable drawable;

    BitmapData(SpiceCommunicator s, RemoteCanvas c) {
        spice = s;
        canvas = c;
        framebufferwidth  = spice.framebufferWidth();
        framebufferheight = spice.framebufferHeight();
        drawable = createDrawable();

        bitmapwidth = framebufferwidth;
        bitmapheight = framebufferheight;
        // To please createBitmap, we ensure the size it at least 1x1.
        if (bitmapwidth  == 0) bitmapwidth  = 1;
        if (bitmapheight == 0) bitmapheight = 1;

        mbitmap = Bitmap.createBitmap(bitmapwidth, bitmapheight, cfg);
        mbitmap.setHasAlpha(false);
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
        return Math.min((float)canvas.getWidth()/bitmapwidth, (float)canvas.getHeight()/bitmapheight);
    }

    /**
     * Create drawable appropriate for this data
     * @return drawable
     */
    AbstractBitmapDrawable createDrawable() {
        return new BitmapDrawable();
    }

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
    public void frameBufferSizeChanged() {
        framebufferwidth = spice.framebufferWidth();
        framebufferheight = spice.framebufferHeight();
        android.util.Log.i("CBM", "bitmapsize changed = ("+bitmapwidth+","+bitmapheight+")");
        if ( bitmapwidth < framebufferwidth || bitmapheight < framebufferheight ) {
            dispose();
            // Try to free up some memory.
            System.gc();
            bitmapwidth  = framebufferwidth;
            bitmapheight = framebufferheight;
            mbitmap      = Bitmap.createBitmap(bitmapwidth, bitmapheight, cfg);
            drawable     = createDrawable();
        }
    }
    
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
