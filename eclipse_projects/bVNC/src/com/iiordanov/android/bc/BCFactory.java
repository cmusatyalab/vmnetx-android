/**
 * Copyright (C) 2009 Michael A. MacDonald
 */
package com.iiordanov.android.bc;

import android.content.Context;

/**
 * Create interface implementations appropriate to the current version of the SDK;
 * implementations can allow use of higher-level SDK calls in .apk's that will still run
 * on lower-level SDK's
 * @author Michael A. MacDonald
 */
public class BCFactory {
    
    private static BCFactory _theInstance = new BCFactory();
    
    private IBCGestureDetector bcGestureDetector;
    
    /**
     * Return the implementation of IBCGestureDetector appropriate for this SDK level
     * 
     * Since we dropped support of SDK levels < 3, there is only one version at the moment.
     * @return
     */
    public IBCGestureDetector getBCGestureDetector()
    {
        if (bcGestureDetector == null)
        {
            synchronized (this)
            {
                if (bcGestureDetector == null)
                {
                    try
                    {
                        bcGestureDetector = (IBCGestureDetector)getClass().getClassLoader().loadClass("com.iiordanov.android.bc.BCGestureDetectorDefault").newInstance();
                    }
                    catch (Exception ie)
                    {
                        throw new RuntimeException("Error instantiating", ie);
                    }
                }
            }
        }
        return bcGestureDetector;
    }
    
    @SuppressWarnings("unchecked")
    static private Class[] scaleDetectorConstructorArgs = new Class[] { Context.class, OnScaleGestureListener.class };
    
    /**
     * Return an instance of an implementation of {@link IBCScaleGestureDetector} appropriate to the SDK of this device.
     * This will work very much like android.view.ScaleGestureDetector on SDK >= 5.  For previous
     * SDK versions, it is a dummy implementation that does nothing and will never call the listener.
     * <p>
     * Note that unlike the other methods in this class, the returned interface instance is not
     * stateless.
     * @param context The context to which the detector is applied
     * @param listener The listener to which the implementation will send scale events
     * @return The gesture detector
     */
    public IBCScaleGestureDetector getScaleGestureDetector(Context context, OnScaleGestureListener listener)
    {
        IBCScaleGestureDetector result;
        
        try {
            result = (IBCScaleGestureDetector)getClass().getClassLoader().
                loadClass("com.iiordanov.android.bc.ScaleGestureDetector").
                getConstructor(scaleDetectorConstructorArgs).newInstance(new Object[] { context, listener });
        } catch (Exception e) {
            throw new RuntimeException("Error instantiating ScaleGestureDetector", e);
        }
        return result;
    }
    
    /**
     * Returns the only instance of this class, which manages the SDK specific interface
     * implementations
     * @return Factory instance
     */
    public static BCFactory getInstance()
    {
        return _theInstance;
    }
}
