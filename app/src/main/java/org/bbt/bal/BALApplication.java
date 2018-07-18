package org.bbt.bal;

import android.app.Application;

/**
 * Application class
 */
public class BALApplication extends Application {

    /**
     * {@link BALApplication} instance
     */
    public static BALApplication applicationInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        applicationInstance = this;
    }
}