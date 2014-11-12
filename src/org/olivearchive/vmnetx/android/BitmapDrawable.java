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
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.DrawableContainer;

/**
 * @author Michael A. MacDonald
 */
public class BitmapDrawable extends DrawableContainer {
    RectF cursorRect;
    boolean softCursorInit;

    private Rect toDraw;
    private Bitmap softCursor;
    private BitmapData data;

    private int hotX, hotY;

    public Paint _defaultPaint;

    BitmapDrawable(BitmapData data) {
        this.data = data;
        cursorRect = new RectF();
        // Try to free up some memory.
        System.gc();
        softCursor = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        softCursorInit = false;

        _defaultPaint = new Paint();
        _defaultPaint.setFilterBitmap(true);
    }

    /* (non-Javadoc)
     * @see android.graphics.drawable.DrawableContainer#draw(android.graphics.Canvas)
     */
    @Override
    public void draw(Canvas canvas) {
        try {
            synchronized (data.mbitmap) {
                canvas.drawBitmap(data.mbitmap, 0.0f, 0.0f, _defaultPaint);
                canvas.drawBitmap(softCursor, cursorRect.left, cursorRect.top, _defaultPaint);
            }
        } catch (Throwable e) { }
    }

    void setCursorRect(int x, int y, float w, float h, int hX, int hY) {
        hotX = hX;
        hotY = hY;
        cursorRect.left   = x-hotX;
        cursorRect.right  = cursorRect.left + w;
        cursorRect.top    = y-hotY;
        cursorRect.bottom = cursorRect.top + h;
    }

    void moveCursorRect(int x, int y) {
        setCursorRect(x, y, cursorRect.width(), cursorRect.height(), hotX, hotY);
    }

    void setSoftCursor (int[] newSoftCursorPixels) {
        Bitmap oldSoftCursor = softCursor;
        softCursor = Bitmap.createBitmap(newSoftCursorPixels, (int)cursorRect.width(),
                                         (int)cursorRect.height(), Bitmap.Config.ARGB_8888);
        softCursorInit = true;
        oldSoftCursor.recycle();
    }

    /* (non-Javadoc)
     * @see android.graphics.drawable.DrawableContainer#getIntrinsicHeight()
     */
    @Override
    public int getIntrinsicHeight() {
        return data.framebufferheight;
    }

    /* (non-Javadoc)
     * @see android.graphics.drawable.DrawableContainer#getIntrinsicWidth()
     */
    @Override
    public int getIntrinsicWidth() {
        return data.framebufferwidth;
    }

    /* (non-Javadoc)
     * @see android.graphics.drawable.DrawableContainer#getOpacity()
     */
    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    /* (non-Javadoc)
     * @see android.graphics.drawable.DrawableContainer#isStateful()
     */
    @Override
    public boolean isStateful() {
        return false;
    }

    public void dispose() {
        if (softCursor != null)
            softCursor.recycle();
        softCursor = null;
        cursorRect = null;
        toDraw = null;
    }
}
