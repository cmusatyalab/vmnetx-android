/**
 * Copyright (C) 2013-2014 Carnegie Mellon University
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2009-2010 Michael A. MacDonald
 * Copyright (C) 2004 Horizon Wimba.  All Rights Reserved.
 * Copyright (C) 2001-2003 HorizonLive.com, Inc.  All Rights Reserved.
 * Copyright (C) 2001,2002 Constantin Kaplinsky.  All Rights Reserved.
 * Copyright (C) 2000 Tridia Corporation.  All Rights Reserved.
 * Copyright (C) 1999 AT&T Laboratories Cambridge.  All Rights Reserved.
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.DrawableContainer;
import android.os.Handler;
import android.widget.ImageView;

import org.olivearchive.vmnetx.android.input.RemotePointer;

public class Viewport {
    @SuppressWarnings("unused")
    private final static String TAG = "Viewport";
    private final static Bitmap.Config BITMAP_CONFIG = Bitmap.Config.ARGB_8888;

    private final RemoteCanvas canvas;
    private final SpiceCommunicator spice;
    private final Handler handler;

    // Image bitmap
    private Bitmap bitmap;
    private final Paint paint = new Paint();
    private final BitmapDrawable drawable = new BitmapDrawable();
    private int imageWidth = 1;
    private int imageHeight = 1;

    // Image transformation matrix for rendering
    private final Matrix matrix = new Matrix();

    // Image scaling
    private float scaling = 1;
    private float minimumScale;

    // The coordinates of the top-left image pixel visible in the view.
    // May be negative due to letterboxing or pillarboxing.
    private int visibleRegionX;
    private int visibleRegionY;

    // Mouse cursor
    private Bitmap softCursor;
    private final Rect cursorRect = new Rect();
    private int hotX, hotY;

    // Last-state tracking for updateViewport()
    private float prevScaling = -1;
    private int prevAbsoluteXPosition = -1;
    private int prevAbsoluteYPosition = -1;

    private class BitmapDrawable extends DrawableContainer {
        /* (non-Javadoc)
         * @see android.graphics.drawable.DrawableContainer#draw(android.graphics.Canvas)
         */
        @Override
        public void draw(Canvas canvas) {
            try {
                if (bitmap != null) {
                    synchronized (bitmap) {
                        canvas.drawBitmap(bitmap, 0.0f, 0.0f, paint);
                        if (softCursor != null)
                            canvas.drawBitmap(softCursor, cursorRect.left,
                                    cursorRect.top, paint);
                    }
                }
            } catch (Throwable e) { }
        }

        /* (non-Javadoc)
         * @see android.graphics.drawable.DrawableContainer#getIntrinsicHeight()
         */
        @Override
        public int getIntrinsicHeight() {
            return imageHeight;
        }

        /* (non-Javadoc)
         * @see android.graphics.drawable.DrawableContainer#getIntrinsicWidth()
         */
        @Override
        public int getIntrinsicWidth() {
            return imageWidth;
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
    }

    Viewport(SpiceCommunicator spice, RemoteCanvas canvas) {
        this.spice = spice;
        this.canvas = canvas;
        this.handler = new Handler();
        setFilteringEnabled(true);
        canvas.setImageDrawable(drawable);
        canvas.setScaleType(ImageView.ScaleType.MATRIX);
    }

    /**
     * Update scaling for view and image sizes
     */
    void updateScale() {
        boolean zoomedOut = (scaling <= minimumScale);
        minimumScale = computeMinimumScale();
        if (zoomedOut) {
            // We were fully zoomed out; stay that way
            setScale(minimumScale);
        } else {
            setScale(Math.max(scaling, minimumScale));
        }
    }

    /**
     * Change the scaling and focus dynamically, as from a detected scale
     * gesture
     * @param scaleFactor Factor by which to adjust scaling
     * @param fx Focus X of center of scale change
     * @param fy Focus Y of center of scale change
     */
    public void adjustScale(float scaleFactor, float fx, float fy) {
        float oldScale;
        float newScale = scaleFactor * scaling;
        if (scaleFactor < 1) {
            if (newScale < minimumScale)
                newScale = minimumScale;
        } else {
            if (newScale > 4)
                newScale = 4;
        }

        // ax is the absolute x of the focus
        int xPan = visibleRegionX;
        float ax = (fx / scaling) + xPan;
        float newXPan = (scaling * xPan - scaling * ax + newScale * ax) /
                newScale;
        int yPan = visibleRegionY;
        float ay = (fy / scaling) + yPan;
        float newYPan = (scaling * yPan - scaling * ay + newScale * ay) /
                newScale;

        // Here we do snapping to 1:1. If we are approaching scale = 1, we
        // snap to it.
        oldScale = scaling;
        if ((newScale > 0.90f && newScale < 1.00f) ||
            (newScale > 1.00f && newScale < 1.10f)) {
            newScale = 1.f;
            // Only if oldScale is outside the snap region, do we inform the
            // user.
            if (oldScale < 0.90f || oldScale > 1.10f)
                canvas.displayShortToastMessage(R.string.snap_one_to_one);
        }

        setScale(newScale);

        // Only if we have actually scaled do we pan.
        if (oldScale != newScale) {
            pan((int) (newXPan - xPan), (int) (newYPan - yPan));
        }
    }

    /**
     * Set new scaling
     */
    private void setScale(float scale) {
        scaling = scale;
        updateViewport();
    }

    /**
     * Update image transformation matrix from absolute[XY]Position and
     * scaling
     */
    private void updateViewport() {
        // Clamp to bounds of desktop image
        visibleRegionX = Math.max(visibleRegionX, 0);
        visibleRegionY = Math.max(visibleRegionY, 0);
        visibleRegionX = Math.min(visibleRegionX,
                imageWidth - getVisibleWidth());
        visibleRegionY = Math.min(visibleRegionY,
                imageHeight - getVisibleHeight());
        // If image is smaller than the canvas, center the image
        if (visibleRegionX < 0)
            visibleRegionX /= 2;
        if (visibleRegionY < 0)
            visibleRegionY /= 2;

        if (scaling != prevScaling ||
                visibleRegionX != prevAbsoluteXPosition ||
                visibleRegionY != prevAbsoluteYPosition) {
            matrix.reset();
            matrix.preTranslate(-visibleRegionX, -visibleRegionY);
            matrix.postScale(scaling, scaling);
            canvas.setImageMatrix(matrix);

            prevScaling = scaling;
            prevAbsoluteXPosition = visibleRegionX;
            prevAbsoluteYPosition = visibleRegionY;
        }
    }

    /**
     * Make sure mouse is visible within view
     */
    public void panToMouse() {
        RemotePointer pointer = canvas.getPointer();
        int x = pointer.getX();
        int y = pointer.getY();
        int w = getVisibleWidth();
        int h = getVisibleHeight();
        int wthresh = 30;
        int hthresh = 30;

        // Don't pan in a certain direction if dimension scaled is already less
        // than the dimension of the visible part of the screen.
        if (imageWidth > getVisibleWidth()) {
            if (x - visibleRegionX >= w - wthresh) {
                visibleRegionX = x - (w - wthresh);
                if (visibleRegionX + w > imageWidth)
                    visibleRegionX = imageWidth - w;
            } else if (x < visibleRegionX + wthresh) {
                visibleRegionX = x - wthresh;
                if (visibleRegionX < 0)
                    visibleRegionX = 0;
            }
        }
        if (imageHeight > getVisibleHeight()) {
            if (y - visibleRegionY >= h - hthresh) {
                visibleRegionY = y - (h - hthresh);
                if (visibleRegionY + h > imageHeight)
                    visibleRegionY = imageHeight - h;
            } else if (y < visibleRegionY + hthresh) {
                visibleRegionY = y - hthresh;
                if (visibleRegionY < 0)
                    visibleRegionY = 0;
            }
        }

        updateViewport();
    }

    /**
     * Pan by a number of pixels (relative pan)
     * @param dX
     * @param dY
     */
    public void pan(int dX, int dY) {
        visibleRegionX += (double) dX / scaling;
        visibleRegionY += (double) dY / scaling;
        updateViewport();
    }

    /**
     * Cause a redraw of the image at the indicated coordinates
     */
    private void reDraw(int x, int y, int w, int h) {
        // Make the box slightly larger to avoid artifacts due to truncation
        // errors.
        canvas.postInvalidate(
            (int) ((x - visibleRegionX - 1) * scaling),
            (int) ((y - visibleRegionY - 1) * scaling),
            (int) ((x - visibleRegionX + w + 1) * scaling),
            (int) ((y - visibleRegionY + h + 1) * scaling));
    }

    /**
     * Invalidates (to redraw) the location of the remote pointer.
     */
    public void invalidateMousePosition() {
        RemotePointer pointer = canvas.getPointer();
        setCursorRect(pointer.getX(), pointer.getY(),
                cursorRect.width(), cursorRect.height());
        if (softCursor != null)
            reDraw(cursorRect.left, cursorRect.top, cursorRect.width(),
                    cursorRect.height());
    }

    private void setCursorRect(int x, int y, int w, int h) {
        cursorRect.left   = x - hotX;
        cursorRect.right  = cursorRect.left + w;
        cursorRect.top    = y - hotY;
        cursorRect.bottom = cursorRect.top + h;
    }

    private void setSoftCursor(int[] newSoftCursorPixels, int w, int h,
            int hX, int hY) {
        int x = cursorRect.left + hotX;
        int y = cursorRect.top + hotY;

        softCursor = Bitmap.createBitmap(newSoftCursorPixels, w, h,
                BITMAP_CONFIG);
        hotX = hX;
        hotY = hY;
        setCursorRect(x, y, w, h);
    }

    private void setDefaultSoftCursor() {
        Bitmap bm = BitmapFactory.decodeResource(canvas.getResources(),
                R.drawable.cursor);
        int w = bm.getWidth();
        int h = bm.getHeight();
        int [] tempPixels = new int[w*h];
        bm.getPixels(tempPixels, 0, w, 0, 0, w, h);
        // Set softCursor to whatever the resource is.
        setSoftCursor(tempPixels, w, h, 0, 0);
    }

    public void setFilteringEnabled(boolean enabled) {
        paint.setFilterBitmap(enabled);
        if (enabled) {
            // Invalidate to make things pretty.
            canvas.invalidate();
        }
    }

    public float getScale() {
        return scaling;
    }

    public int getVisibleWidth() {
        return (int)((double) canvas.getWidth() / scaling + 0.5);
    }

    public int getVisibleHeight() {
        return (int)((double) canvas.getHeight() / scaling + 0.5);
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public int viewToImageX(float viewX) {
        return (int) (viewX / scaling + visibleRegionX);
    }

    public int viewToImageY(float viewY) {
        return (int) (viewY / scaling + visibleRegionY);
    }

    /**
     * @return The scale at which the image would be smaller than the view
     */
    private float computeMinimumScale() {
        return Math.min((float) canvas.getWidth() / imageWidth,
                (float) canvas.getHeight() / imageHeight);
    }

    ///////////////////////////////////////////////////////////////////////
    // SPICE callbacks
    ///////////////////////////////////////////////////////////////////////

    void OnSettingsChanged(final int width, final int height) {
        // Recreate bitmap.
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (bitmap != null) {
                    try {
                        bitmap.reconfigure(width, height, BITMAP_CONFIG);
                    } catch (IllegalArgumentException e) {
                        bitmap = null;
                    }
                }
                if (bitmap == null) {
                    try {
                        bitmap = Bitmap.createBitmap(width, height,
                                BITMAP_CONFIG);
                        bitmap.setHasAlpha(false);
                    } catch (Throwable e) {
                        canvas.showFatalMessageAndQuit(canvas.getContext().getString(R.string.error_out_of_memory));
                    }
                }
                imageWidth = width;
                imageHeight = height;
                updateScale();
                spice.redraw();
            }
        });
    }

    void OnGraphicsUpdate(int x, int y, int width, int height) {
        //android.util.Log.d(TAG, "OnGraphicsUpdate called: " + x +", " + y + " + " + width + "x" + height );

        if (bitmap == null)
            return;

        synchronized (bitmap) {
            spice.updateBitmap(bitmap, x, y, width, height);
        }

        reDraw(x, y, width, height);
    }

    void OnCursorConfig(final boolean shown, final int[] bitmap,
            final int w, final int h, final int hx, final int hy) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Rect prevRect = null;
                if (softCursor != null)
                    prevRect = new Rect(cursorRect);

                if (!shown)
                    softCursor = null;
                else if (bitmap != null)
                    setSoftCursor(bitmap, w, h, hx, hy);
                else
                    setDefaultSoftCursor();

                // Redraw the cursor.
                if (softCursor != null)
                    reDraw(cursorRect.left, cursorRect.top,
                            cursorRect.width(), cursorRect.height());
                if (prevRect != null)
                    reDraw(prevRect.left, prevRect.top, prevRect.width(),
                            prevRect.height());
            }
        });
    }
}
