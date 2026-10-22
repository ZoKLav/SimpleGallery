package com.zoeykl.simplegallery.legacy.cache;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;
import com.zoeykl.simplegallery.DiskCache;
import com.zoeykl.simplegallery.LocalDataSource;
import com.zoeykl.simplegallery.SimpleGalleryApp;
import com.zoeykl.simplegallery.UriTexture;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class CacheService {
    private CacheService() {
    }

    public static String getCachePath(String str) {
        return SimpleGalleryApp.cachePath(str);
    }

    public static final byte[] queryThumbnail(Context context, long j, long j2, boolean z, long j3) {
        Bitmap bitmapCreateFromUri;
        DiskCache diskCache = z ? LocalDataSource.sThumbnailCacheVideo : LocalDataSource.sThumbnailCache;
        byte[] bArr = diskCache.get(j, j3);
        if (bArr != null) {
            return bArr;
        }
        try {
            Uri uriWithAppendedId = ContentUris.withAppendedId(z ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, j2);
            if (Build.VERSION.SDK_INT >= 29) {
                bitmapCreateFromUri = context.getContentResolver().loadThumbnail(uriWithAppendedId, new Size(256, 256), null);
                if (!z) {
                    Cursor cursorQuery = context.getContentResolver().query(uriWithAppendedId, new String[]{"orientation"}, null, null, null);
                    int i = 0;
                    if (cursorQuery != null) {
                        try {
                            if (cursorQuery.moveToFirst()) {
                                i = cursorQuery.getInt(0);
                            }
                        } finally {
                        }
                    }
                    if (cursorQuery != null) {
                        cursorQuery.close();
                    }
                    if (i != 0) {
                        Matrix matrix = new Matrix();
                        matrix.postRotate(-i);
                        Bitmap original = bitmapCreateFromUri;
                        bitmapCreateFromUri = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);
                        if (bitmapCreateFromUri != original) original.recycle();
                    }
                }
            } else if (z) {
                bitmapCreateFromUri = MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(), j2, 1, null);
            } else {
                bitmapCreateFromUri = UriTexture.createFromUri(context, uriWithAppendedId.toString(), 256, 256, 0L, null);
            }
            if (bitmapCreateFromUri != null) {
                return writeBitmapToCache(diskCache, j, j2, bitmapCreateFromUri, 128, 96, j3);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static final byte[] writeBitmapToCache(DiskCache diskCache, long j, long j2, Bitmap bitmap, int i, int i2, long j3) {
        int i3;
        int iMax;
        float f;
        int i4;
        int iMax2;
        byte[] byteArray = null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int i5 = width / 2;
        int i6 = height / 2;
        int i7 = i * height;
        int i8 = i2 * width;
        if (i7 < i8) {
            i4 = i7 / i2;
            iMax2 = Math.max(0, Math.min(i5 - (i4 / 2), width - i4));
            f = (float) i2 / height;
            i3 = height;
            iMax = 0;
        } else {
            i3 = i8 / i;
            iMax = Math.max(0, Math.min(i6 - (i3 / 2), height - i3));
            f = (float) i / width;
            i4 = width;
            iMax2 = 0;
        }
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(i, i2, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        Paint paint = new Paint();
        paint.setDither(true);
        paint.setFilterBitmap(true);
        canvas.drawColor(0);
        canvas.drawBitmap(bitmap, new Rect(iMax2, iMax, i4 + iMax2, i3 + iMax), new Rect(0, 0, i, i2), paint);
        bitmap.recycle();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(16384);
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        byte[] bArr = null;
        try {
            dataOutputStream.writeLong(j2);
            dataOutputStream.writeShort((int) ((i5 - iMax2) * f));
            dataOutputStream.writeShort((int) ((i6 - iMax) * f));
            dataOutputStream.flush();
            bitmapCreateBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            byteArray = byteArrayOutputStream.toByteArray();
            synchronized (diskCache) {
                diskCache.put(j, byteArray, j3);
            }
            byteArrayOutputStream.close();
            bitmapCreateBitmap.recycle();
            return byteArray;
        } catch (Exception e2) {
            bArr = byteArray;
            return bArr;
        }
    }
}
