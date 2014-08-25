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
