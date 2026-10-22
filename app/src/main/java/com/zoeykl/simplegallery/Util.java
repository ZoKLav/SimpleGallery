/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Modified for SimpleGallery: package migration and Android API compatibility.
package com.zoeykl.simplegallery;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.MimeTypeMap;
import com.zoeykl.simplegallery.MonitoredActivity;
import java.io.Closeable;

public class Util {
    private static final String MAPS_CLASS_NAME = "com.google.android.maps.MapsActivity";
    private static final String MAPS_PACKAGE_NAME = "com.google.android.apps.maps";
    private static final String TAG = "db.Util";

    private Util() {
    }

    public static Bitmap rotate(Bitmap bitmap, int i) {
        Bitmap bitmap2;
        if (i == 0 || bitmap == null) {
            bitmap2 = bitmap;
        } else {
            Matrix matrix = new Matrix();
            matrix.setRotate(i, bitmap.getWidth() / 2.0f, bitmap.getHeight() / 2.0f);
            try {
                bitmap2 = bitmap;
                try {
                    Bitmap bitmapCreateBitmap = Bitmap.createBitmap(bitmap2, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (bitmap2 == bitmapCreateBitmap) {
                        return bitmap2;
                    }
                    bitmap2.recycle();
                    return bitmapCreateBitmap;
                } catch (OutOfMemoryError e) {
                }
            } catch (OutOfMemoryError e2) {
                bitmap2 = bitmap;
            }
        }
        return bitmap2;
    }

    public static Bitmap transform(Matrix matrix, Bitmap bitmap, int i, int i2, boolean z) {
        Matrix matrix2;
        Bitmap bitmapCreateBitmap;
        Matrix matrix3;
        int width = bitmap.getWidth() - i;
        int height = bitmap.getHeight() - i2;
        if (!z && (width < 0 || height < 0)) {
            Bitmap bitmapCreateBitmap2 = Bitmap.createBitmap(i, i2, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmapCreateBitmap2);
            int iMax = Math.max(0, width / 2);
            int iMax2 = Math.max(0, height / 2);
            Rect rect = new Rect(iMax, iMax2, Math.min(i, bitmap.getWidth()) + iMax, Math.min(i2, bitmap.getHeight()) + iMax2);
            int iWidth = (i - rect.width()) / 2;
            int iHeight = (i2 - rect.height()) / 2;
            canvas.drawBitmap(bitmap, rect, new Rect(iWidth, iHeight, i - iWidth, i2 - iHeight), (Paint) null);
            return bitmapCreateBitmap2;
        }
        float width2 = bitmap.getWidth();
        float height2 = bitmap.getHeight();
        float f = i;
        float f2 = i2;
        if (width2 / height2 > f / f2) {
            float f3 = f2 / height2;
            if (f3 < 0.9f || f3 > 1.0f) {
                matrix.setScale(f3, f3);
                matrix3 = matrix;
            } else {
                matrix3 = null;
            }
            matrix2 = matrix3;
        } else {
            float f4 = f / width2;
            if (f4 < 0.9f || f4 > 1.0f) {
                matrix.setScale(f4, f4);
                matrix2 = matrix;
            } else {
                matrix2 = null;
            }
        }
        if (matrix2 != null) {
            bitmapCreateBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix2, true);
        } else {
            bitmapCreateBitmap = bitmap;
        }
        Bitmap bitmapCreateBitmap3 = Bitmap.createBitmap(bitmapCreateBitmap, Math.max(0, bitmapCreateBitmap.getWidth() - i) / 2, Math.max(0, bitmapCreateBitmap.getHeight() - i2) / 2, i, i2);
        if (bitmapCreateBitmap != bitmap) {
            bitmapCreateBitmap.recycle();
        }
        return bitmapCreateBitmap3;
    }

    public static Bitmap extractMiniThumb(Bitmap bitmap, int i, int i2) {
        return extractMiniThumb(bitmap, i, i2, true);
    }

    public static Bitmap extractMiniThumb(Bitmap bitmap, int i, int i2, boolean z) {
        float height;
        if (bitmap == null) {
            return null;
        }
        if (bitmap.getWidth() < bitmap.getHeight()) {
            height = i / bitmap.getWidth();
        } else {
            height = i2 / bitmap.getHeight();
        }
        Matrix matrix = new Matrix();
        matrix.setScale(height, height);
        Bitmap bitmapTransform = transform(matrix, bitmap, i, i2, false);
        if (z && bitmapTransform != bitmap) {
            bitmap.recycle();
        }
        return bitmapTransform;
    }

    public static <T> int indexOf(T[] tArr, T t) {
        for (int i = 0; i < tArr.length; i++) {
            if (tArr[i].equals(t)) {
                return i;
            }
        }
        return -1;
    }

    public static void closeSilently(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable th) {
        }
    }

    public static void closeSilently(ParcelFileDescriptor parcelFileDescriptor) {
        if (parcelFileDescriptor == null) {
            return;
        }
        try {
            parcelFileDescriptor.close();
        } catch (Throwable th) {
        }
    }

    public static void Assert(boolean z) {
        if (!z) {
            throw new AssertionError();
        }
    }

    public static boolean equals(String str, String str2) {
        return str == str2 || str.equals(str2);
    }

    private static class BackgroundJob extends MonitoredActivity.LifeCycleAdapter implements Runnable {
        private final MonitoredActivity mActivity;
        private final Runnable mCleanupRunner = new Runnable() {
            @Override
            public void run() {
                BackgroundJob.this.mActivity.removeLifeCycleListener(BackgroundJob.this);
                if (BackgroundJob.this.mDialog.getWindow() != null) {
                    BackgroundJob.this.mDialog.dismiss();
                }
            }
        };
        private final ProgressDialog mDialog;
        private final Handler mHandler;
        private final Runnable mJob;

        public BackgroundJob(MonitoredActivity monitoredActivity, Runnable runnable, ProgressDialog progressDialog, Handler handler) {
            this.mActivity = monitoredActivity;
            this.mDialog = progressDialog;
            this.mJob = runnable;
            this.mActivity.addLifeCycleListener(this);
            this.mHandler = handler;
        }

        @Override
        public void run() {
            try {
                this.mJob.run();
            } finally {
                this.mHandler.post(this.mCleanupRunner);
            }
        }

        @Override
        public void onActivityDestroyed(MonitoredActivity monitoredActivity) {
            this.mCleanupRunner.run();
            this.mHandler.removeCallbacks(this.mCleanupRunner);
        }

        @Override
        public void onActivityStopped(MonitoredActivity monitoredActivity) {
            this.mDialog.hide();
        }

        @Override
        public void onActivityStarted(MonitoredActivity monitoredActivity) {
            this.mDialog.show();
        }
    }

    public static void startBackgroundJob(MonitoredActivity monitoredActivity, String str, String str2, Runnable runnable, Handler handler) {
        new Thread(new BackgroundJob(monitoredActivity, runnable, ProgressDialog.show(monitoredActivity, str, str2, true, false), handler)).start();
    }

    public static Intent createSetAsIntent(Uri uri, String str) {
        int iLastIndexOf;
        if (uri.getScheme().equals("file") && (iLastIndexOf = uri.getPath().lastIndexOf(46)) != -1) {
            str = MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.getPath().substring(iLastIndexOf + 1).toLowerCase());
        }
        Intent intent = new Intent("android.intent.action.ATTACH_DATA");
        intent.setDataAndType(uri, str);
        intent.putExtra("mimeType", str);
        return intent;
    }

    public static void openMaps(Context context, double d, double d2) {
        try {
            String str = String.format("http://maps.google.com/maps?f=q&q=(%s,%s)", Double.valueOf(d), Double.valueOf(d2));
            context.startActivity(new Intent("android.intent.action.VIEW", Uri.parse(str)).setComponent(new ComponentName(MAPS_PACKAGE_NAME, MAPS_CLASS_NAME)));
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "GMM activity not found!", e);
            context.startActivity(new Intent("android.intent.action.VIEW", Uri.parse(String.format("geo:%s,%s", Double.valueOf(d), Double.valueOf(d2)))));
        }
    }
}
