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
import android.graphics.RectF;
import android.graphics.drawable.DrawableContainer;

/**
 * @author Michael A. MacDonald
 */
class BitmapDrawable extends DrawableContainer {
    RectF cursorRect;
    boolean softCursorInit;

    private Bitmap softCursor;
    private BitmapData data;

    private int hotX, hotY;

    private final Paint paint;

    BitmapDrawable(BitmapData data) {
        this.data = data;
        cursorRect = new RectF();
        // Try to free up some memory.
        System.gc();
        softCursor = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        softCursorInit = false;

        paint = new Paint();
        setFilteringEnabled(true);
    }

    /* (non-Javadoc)
     * @see android.graphics.drawable.DrawableContainer#draw(android.graphics.Canvas)
     */
    @Override
    public void draw(Canvas canvas) {
        try {
            synchronized (data.mbitmap) {
                canvas.drawBitmap(data.mbitmap, 0.0f, 0.0f, paint);
                canvas.drawBitmap(softCursor, cursorRect.left, cursorRect.top, paint);
            }
        } catch (Throwable e) { }
    }

    void setFilteringEnabled(boolean enabled) {
        paint.setFilterBitmap(enabled);
    }

    private void setCursorRect(int x, int y, float w, float h, int hX, int hY) {
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

    void setSoftCursor(int[] newSoftCursorPixels, int w, int h, int hX, int hY) {
        Bitmap oldSoftCursor = softCursor;
        int x = (int) cursorRect.left + hotX;
        int y = (int) cursorRect.top + hotY;

        softCursor = Bitmap.createBitmap(newSoftCursorPixels, w, h,
                Bitmap.Config.ARGB_8888);
        softCursorInit = true;
        setCursorRect(x, y, w, h, hX, hY);
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

    void dispose() {
        if (softCursor != null)
            softCursor.recycle();
        softCursor = null;
        cursorRect = null;
    }
}
