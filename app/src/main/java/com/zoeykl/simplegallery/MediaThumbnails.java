package com.zoeykl.simplegallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Size;

/** Thumbnail fallback for document and file URIs, including WebM video. */
public final class MediaThumbnails {
    private MediaThumbnails() {}

    public static Bitmap load(Context context, MediaItem item, int width, int height) {
        Uri uri = Uri.parse(item.mContentUri);
        if (item.getMediaType() != MediaItem.MEDIA_TYPE_VIDEO) {
            try { return UriTexture.createFromUri(context, uri.toString(), width, height, 0, null); }
            catch (Exception | OutOfMemoryError exception) { return null; }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                Bitmap result = context.getContentResolver().loadThumbnail(uri, new Size(width, height), null);
                if (result != null) return result;
            } catch (Exception | OutOfMemoryError ignored) {}
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= 27) {
                int videoWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int videoHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                int[] size = MediaFormats.fitSize(videoWidth, videoHeight, width, height);
                bitmap = retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size[0], size[1]);
            } else {
                bitmap = retriever.getFrameAtTime();
            }
            if (bitmap == null) return null;
            int[] size = MediaFormats.fitSize(bitmap.getWidth(), bitmap.getHeight(), width, height);
            if (size[0] == bitmap.getWidth() && size[1] == bitmap.getHeight()) return bitmap;
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size[0], size[1], true);
            if (scaled != bitmap) bitmap.recycle();
            return scaled;
        } catch (Exception | OutOfMemoryError exception) {
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }
}
