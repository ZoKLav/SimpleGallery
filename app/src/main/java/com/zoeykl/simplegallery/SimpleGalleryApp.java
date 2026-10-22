package com.zoeykl.simplegallery;

import android.app.Application;
import java.io.File;

public final class SimpleGalleryApp extends Application {
    private static File cacheRoot;

    @Override
    public void onCreate() {
        super.onCreate();
        cacheRoot = getCacheDir();
    }

    public static String cachePath(String str) {
        if (cacheRoot == null) {
            throw new IllegalStateException("Application has not initialized");
        }
        File file = new File(cacheRoot, str);
        file.mkdirs();
        return file.getAbsolutePath() + File.separator;
    }
}
