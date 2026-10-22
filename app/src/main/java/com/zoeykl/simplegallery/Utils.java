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

import com.zoeykl.simplegallery.legacy.app.App;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory.Options;
import android.provider.MediaStore.Images.Media;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;
import com.zoeykl.simplegallery.R;
import com.zoeykl.simplegallery.legacy.app.App;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class Utils {
    private static final long INITIALCRC = -1;
    private static final long POLY64REV = -7661587058870466123L;
    private static final int UNCONSTRAINED = -1;
    private static boolean init = false;
    private static long[] CRCTable = new long[256];

    public static void playVideo(final Context context, final MediaItem mediaItem) {
        App.get(context).getHandler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(mediaItem.mContentUri));
                    intent.setDataAndType(Uri.parse(mediaItem.mContentUri), mediaItem.mMimeType);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setClass(context, MovieView.class);
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Context context2 = context;
                    Resources resources = context.getResources();

                    Toast.makeText(context2, resources.getString(R.string.video_err), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public static final void writeUTF(DataOutputStream dataOutputStream, String str) throws IOException {
        if (str == null) {
            dataOutputStream.writeUTF(new String());
        } else {
            dataOutputStream.writeUTF(str);
        }
    }

    public static final String readUTF(DataInputStream dataInputStream) throws IOException {
        String utf = dataInputStream.readUTF();
        if (utf.length() == 0) {
            return null;
        }
        return utf;
    }

    public static final Bitmap resizeBitmap(Bitmap bitmap, int maxSize) {
        int srcWidth = bitmap.getWidth();
        int srcHeight = bitmap.getHeight();
        int width = maxSize;
        int height = maxSize;
        boolean needsResize = false;
        if (srcWidth > srcHeight) {
            if (srcWidth > maxSize) {
                needsResize = true;
                height = ((maxSize * srcHeight) / srcWidth);
            }
        } else {
            if (srcHeight > maxSize) {
                needsResize = true;
                width = ((maxSize * srcWidth) / srcHeight);
            }
        }
        if (needsResize) {
            Bitmap retVal = Bitmap.createScaledBitmap(bitmap, width, height, true);
            return retVal;
        } else {
            return bitmap;
        }
    }

    public static final long Crc64Long(String str) {
        if (str == null || str.length() == 0) {
            return 0L;
        }
        if (!init) {
            for (int i = 0; i < 256; i++) {
                long j = i;
                for (int i2 = 0; i2 < 8; i2++) {
                    if ((((int) j) & 1) != 0) {
                        j = (j >> 1) ^ POLY64REV;
                    } else {
                        j >>= 1;
                    }
                }
                CRCTable[i] = j;
            }
            init = true;
        }
        int length = str.length();
        long j2 = INITIALCRC;
        for (int i3 = 0; i3 < length; i3++) {
            j2 = (j2 >> 8) ^ CRCTable[(str.charAt(i3) ^ ((int) j2)) & 255];
        }
        return j2;
    }

    public static final String Crc64(String str) {
        if (str == null) {
            return null;
        }
        long jCrc64Long = Crc64Long(str);
        return Integer.toHexString(((int) (jCrc64Long >> 32)) & (-1)) + Integer.toHexString(((int) jCrc64Long) & (-1));
    }

    public static long getBucketIdFromUri(ContentResolver contentResolver, Uri uri) {
        if (uri.getScheme().equals("file")) {
            List<String> pathSegments = uri.getPathSegments();
            int size = pathSegments.size();
            String str = "/";
            for (int i = 0; i < size - 1; i++) {
                str = str + pathSegments.get(i);
                if (i != size - 2) {
                    str = str + "/";
                }
            }
            return LocalDataSource.getBucketId(str);
        }
        try {
            long id = ContentUris.parseId(uri);
            Cursor cursorQuery = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{"bucket_id"}, "_id=" + id, null, null);
            if (cursorQuery == null || !cursorQuery.moveToFirst()) {
                Cursor cursorQuery2 = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, new String[]{"bucket_id"}, "_id=" + id, null, null);
                if (cursorQuery2 != null && cursorQuery2.moveToFirst()) {
                    long j = cursorQuery2.getLong(0);
                    cursorQuery2.close();
                    return j;
                }
                return INITIALCRC;
            }
            long j2 = cursorQuery.getLong(0);
            cursorQuery.close();
            return j2;
        } catch (Exception e) {
            return INITIALCRC;
        }
    }

    public static String getBucketNameFromUri(ContentResolver contentResolver, Uri uri) {
        long bucketIdFromUri = getBucketIdFromUri(contentResolver, uri);
        if (bucketIdFromUri != INITIALCRC) {
            try {
                Cursor cursorQuery = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{"bucket_display_name"}, "bucket_id='" + bucketIdFromUri + "'", null, null);
                if (cursorQuery == null || !cursorQuery.moveToFirst()) {
                    Cursor cursorQuery2 = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, new String[]{"bucket_display_name"}, "bucket_id='" + bucketIdFromUri + "'", null, null);
                    if (cursorQuery2 != null && cursorQuery2.moveToFirst()) {
                        String string = cursorQuery2.getString(0);
                        cursorQuery2.close();
                        return string;
                    }
                    return "";
                }
                String string2 = cursorQuery.getString(0);
                cursorQuery.close();
                return string2;
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    public static void Copy(File file, File file2) throws IOException {
        copyStream(new FileInputStream(file), new FileOutputStream(file2));
    }

    public static void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] bArr = new byte[1024];
        while (true) {
            int i = inputStream.read(bArr);
            if (i > 0) {
                outputStream.write(bArr, 0, i);
            } else {
                inputStream.close();
                outputStream.close();
                return;
            }
        }
    }

    public static int computeSampleSize(BitmapFactory.Options options, int i, int i2) {
        int iComputeInitialSampleSize = computeInitialSampleSize(options, i, i2);
        if (iComputeInitialSampleSize > 8) {
            return 8 * ((iComputeInitialSampleSize + 7) / 8);
        }
        int i3 = 1;
        while (i3 < iComputeInitialSampleSize) {
            i3 <<= 1;
        }
        return i3;
    }

    public static int computeInitialSampleSize(BitmapFactory.Options options, int i, int i2) {
        int iCeil;
        int iMin;
        double d = options.outWidth;
        double d2 = options.outHeight;
        if (i2 == -1) {
            iCeil = 1;
        } else {
            iCeil = (int) Math.ceil(Math.sqrt((d * d2) / i2));
        }
        if (i == -1) {
            iMin = 128;
        } else {
            double d3 = i;
            iMin = (int) Math.min(Math.floor(d / d3), Math.floor(d2 / d3));
        }
        if (iMin < iCeil) {
            return iCeil;
        }
        if (i2 == -1 && i == -1) {
            return 1;
        }
        if (i == -1) {
            return iCeil;
        }
        return iMin;
    }
}
