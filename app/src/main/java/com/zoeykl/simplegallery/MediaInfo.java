package com.zoeykl.simplegallery;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.InputStream;
import java.util.Arrays;

/** Reads provider metadata without converting content URIs into filesystem paths. */
public final class MediaInfo {
    public final String name;
    public final String mime;

    private MediaInfo(String name, String mime) { this.name = name; this.mime = mime; }

    public static MediaInfo read(Context context, Uri uri, String intentType) {
        String name = uri.getLastPathSegment();
        String declared = null;
        try { declared = context.getContentResolver().getType(uri); } catch (Exception ignored) {}
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
        } catch (Exception ignored) {}
        if (!MediaFormats.isSpecificMediaType(declared) && MediaFormats.isSpecificMediaType(intentType)) declared = intentType;
        byte[] header = null;
        if (!MediaFormats.isSpecificMediaType(declared)) {
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                if (stream != null) {
                    byte[] buffer = new byte[4096];
                    int length = 0;
                    while (length < buffer.length) {
                        int count = stream.read(buffer, length, buffer.length - length);
                        if (count <= 0) break;
                        length += count;
                    }
                    header = Arrays.copyOf(buffer, length);
                }
            } catch (Exception ignored) {}
        }
        String mime = MediaFormats.resolve(declared, name, header);
        if (mime == null) mime = MediaFormats.fromName(uri.getLastPathSegment());
        return new MediaInfo(name == null ? "Media" : name, mime);
    }

    public static boolean isLocal(Uri uri) {
        return uri != null && ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme()));
    }
}
