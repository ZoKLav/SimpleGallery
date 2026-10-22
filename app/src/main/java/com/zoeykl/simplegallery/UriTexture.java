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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

public final class UriTexture extends Texture {
    public static final int MAX_RESOLUTION = 2048;
    public static final int MAX_RESOLUTION_A = 2048;
    public static final int MAX_RESOLUTION_B = 2048;
    public static final String URI_CACHE = SimpleGalleryApp.cachePath("hires-image-cache");
    private long cacheId;
    private final String uri;

    public UriTexture(String str) {
        this.uri = str;
    }

    public void setCacheId(long j) {
        this.cacheId = j;
    }
    public static Bitmap createFromUri(Context context, String source, int maxWidth,
            int maxHeight, long cacheId, Object connectionManager) throws URISyntaxException, IOException {
        if (maxWidth <= 0 || maxHeight <= 0) throw new IllegalArgumentException("Invalid decode bounds");
        Uri uri = Uri.parse(source);
        if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) {
            throw new IOException("Local media only");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(stream, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Unsupported or damaged image");
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > maxWidth
                || options.outHeight / options.inSampleSize > maxHeight) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            Bitmap result = BitmapFactory.decodeStream(stream, null, options);
            if (result == null) throw new IOException("Unsupported or damaged image");
            return result;
        }
    }

    @Override
    protected Bitmap load(RenderView renderView) {
        try {
            return createFromUri(renderView.getContext(), this.uri, 2048, 2048, this.cacheId, null);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    public static String createFilePathFromCrc64(long j, int i) {
        return URI_CACHE + j + "_" + i + ".cache";
    }

    public static boolean isCached(long j, int i) {
        return j != 0 && new File(createFilePathFromCrc64(j, i)).isFile();
    }

    public static Bitmap createFromCache(long j, int i) {
        if (j == 0) {
            return null;
        }
        return BitmapFactory.decodeFile(createFilePathFromCrc64(j, i));
    }

    public static void invalidateCache(long j, int i) {
        new File(createFilePathFromCrc64(j, i)).delete();
    }
    public static void writeToCache(long cacheId, Bitmap bitmap, int resolution) throws IOException {
        if (cacheId == 0 || bitmap == null) return;
        try (FileOutputStream stream = new FileOutputStream(createFilePathFromCrc64(cacheId, resolution))) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        } catch (IOException exception) {
            // Cache writes are optional; the source image remains available.
        }
    }

    public static String writeHttpDataInDirectory(Context context, String str, String str2) {
        return null;
    }
}
