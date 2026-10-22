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

import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;

public class LocalDataSource implements DataSource {
    public static final String CAMERA_STRING = "Camera";
    public static final String DOWNLOAD_STRING = "Download";
    private final Context context;
    private final String uri;
    public static final String URI_ALL_MEDIA = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString();
    public static final DiskCache sThumbnailCache = new DiskCache("local-image-thumbs");
    public static final DiskCache sThumbnailCacheVideo = new DiskCache("local-video-thumbs");
    public static final String CAMERA_BUCKET_NAME = Environment.getExternalStorageDirectory() + "/DCIM/Camera";
    public static final String DOWNLOAD_BUCKET_NAME = Environment.getExternalStorageDirectory() + "/Download";
    public static final int CAMERA_BUCKET_ID = getBucketId(CAMERA_BUCKET_NAME);
    public static final int DOWNLOAD_BUCKET_ID = getBucketId(DOWNLOAD_BUCKET_NAME);
    private boolean images = true;
    private boolean videos = true;
    private final LinkedHashMap<Long, ArrayList<MediaItem>> albums = new LinkedHashMap<>();
    private final LinkedHashMap<Long, String> names = new LinkedHashMap<>();

    public LocalDataSource(Context context, String str, boolean z) {
        this.context = context;
        this.uri = str;
    }

    public static int getBucketId(String str) {
        return str.toLowerCase(Locale.ROOT).hashCode();
    }

    public void setMimeFilter(boolean z, boolean z2) {
        this.images = z;
        this.videos = z2;
    }

    public boolean isSingleImage() {
        return !URI_ALL_MEDIA.equals(this.uri);
    }

    @Override
    public DiskCache getThumbnailCache() {
        return sThumbnailCache;
    }

    @Override
    public void shutdown() {
    }

    private boolean allowed(boolean z) {
        if (Build.VERSION.SDK_INT < 33) {
            return this.context.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        if (this.context.checkSelfPermission(z ? "android.permission.READ_MEDIA_VIDEO" : "android.permission.READ_MEDIA_IMAGES") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return Build.VERSION.SDK_INT >= 34 && this.context.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    private void scan(boolean video) {
        if (!allowed(video)) return;
        Uri collection = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {"_id", "bucket_id", "bucket_display_name", "_display_name", "mime_type",
                "datetaken", "date_added", "date_modified", "_size", video ? "duration" : "orientation"};
        try (Cursor cursor = context.getContentResolver().query(collection, projection,
                null, null, "datetaken DESC, _id DESC")) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) return;
                long albumId = cursor.getLong(1);
                MediaItem item = new MediaItem();
                item.mId = cursor.getLong(0);
                item.mContentUri = ContentUris.withAppendedId(collection, item.mId).toString();
                item.mFilePath = item.mContentUri;
                item.mThumbnailUri = item.mContentUri;
                item.mScreennailUri = item.mContentUri;
                item.mCaption = cursor.getString(3);
                item.mMimeType = MediaFormats.resolve(cursor.getString(4), item.mCaption, null);
                if (item.mMimeType == null) item.mMimeType = video ? "video/*" : "image/*";
                item.mDateTakenInMs = cursor.getLong(5);
                item.mDateAddedInSec = cursor.getLong(6);
                item.mDateModifiedInSec = cursor.getLong(7);
                item.setMediaType(video ? MediaItem.MEDIA_TYPE_VIDEO : MediaItem.MEDIA_TYPE_IMAGE);
                if (video) item.mDurationInSec = (int) (cursor.getLong(9) / 1000);
                else item.mRotation = cursor.getInt(9);
                if (item.mDateTakenInMs == 0) item.mDateTakenInMs = item.mDateAddedInSec * 1000;
                String albumName = cursor.getString(2);
                names.put(albumId, albumName == null ? "Photos" : albumName);
                if (!albums.containsKey(albumId)) albums.put(albumId, new ArrayList<>());
                albums.get(albumId).add(item);
            }
        } catch (IllegalArgumentException | SecurityException exception) {
            Log.w("SimpleGallery", "Media access changed", exception);
        }
    }

    @Override
    public void loadMediaSets(MediaFeed mediaFeed) {
        this.albums.clear();
        this.names.clear();
        if (isSingleImage()) {
            MediaItem mediaItemCreateMediaItemFromUri = createMediaItemFromUri(this.context, Uri.parse(this.uri), 0);
            ArrayList<MediaItem> arrayList = new ArrayList<>();
            arrayList.add(mediaItemCreateMediaItemFromUri);
            this.albums.put(0L, arrayList);
            this.names.put(0L, mediaItemCreateMediaItemFromUri.getMediaType() == MediaItem.MEDIA_TYPE_VIDEO ? "Video" : "Photo");
        } else {
            if (this.images) {
                scan(false);
            }
            if (this.videos) {
                scan(true);
            }
        }
        for (Long l : this.albums.keySet()) {
            this.albums.get(l).sort(new Comparator() {
                @Override
                public final int compare(Object obj, Object obj2) {
                    return Long.compare(((MediaItem) obj2).mDateTakenInMs, ((MediaItem) obj).mDateTakenInMs);
                }
            });
            MediaSet mediaSetAddMediaSet = mediaFeed.addMediaSet(l.longValue(), this);
            mediaSetAddMediaSet.mName = this.names.get(l);
            mediaSetAddMediaSet.mIsLocal = true;
            mediaSetAddMediaSet.setNumExpectedItems(this.albums.get(l).size());
            mediaSetAddMediaSet.generateTitle(true);
        }
        if (this.albums.isEmpty()) {
            ((Gallery) this.context).runOnUiThread(new Runnable() {
                @Override
                public final void run() {
                    LocalDataSource.this.showEmptyLibraryMessage();
                }
            });
        }
    }

     void showEmptyLibraryMessage() {
        Toast.makeText(this.context, "No accessible photos or videos. Use the menu to change permissions.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void loadItemsForSet(MediaFeed mediaFeed, MediaSet mediaSet, int i, int i2) {
        if (mediaSet.mNumItemsLoaded > 0) {
            return;
        }
        ArrayList<MediaItem> arrayList = this.albums.get(Long.valueOf(mediaSet.mId));
        mediaSet.clear();
        mediaSet.setNumExpectedItems(arrayList == null ? 0 : arrayList.size());
        if (arrayList != null) {
            Iterator<MediaItem> it = arrayList.iterator();
            while (it.hasNext()) {
                MediaItem next = it.next();
                next.mClusteringState = 0;
                mediaFeed.addItemToMediaSet(next, mediaSet);
            }
        }
        mediaSet.updateNumExpectedItems();
        mediaSet.generateTitle(true);
    }
    public static MediaItem createMediaItemFromUri(Context context, Uri uri, int mediaType) {
        MediaItem item = new MediaItem();
        item.mId = Math.abs(uri.toString().hashCode());
        item.mContentUri = uri.toString();
        item.mFilePath = item.mContentUri;
        item.mThumbnailUri = item.mContentUri;
        item.mScreennailUri = item.mContentUri;
        MediaInfo info = MediaInfo.read(context, uri, null);
        item.mMimeType = info.mime;
        item.mCaption = info.name;
        item.setMediaType(MediaFormats.isSpecificMediaType(info.mime)
                ? (MediaFormats.isVideo(info.mime) ? MediaItem.MEDIA_TYPE_VIDEO : MediaItem.MEDIA_TYPE_IMAGE) : mediaType);
        if (item.getMediaType() == MediaItem.MEDIA_TYPE_IMAGE) item.mRotation = readRotation(context, uri);
        return item;
    }

    static int readRotation(Context context, Uri uri) {
        try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor != null) {
                ExifInterface exif = new ExifInterface(descriptor.getFileDescriptor());
                return (int) Shared.exifOrientationToDegrees(exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static MediaItem createMediaItemFromFileUri(Context context, String str) {
        return createMediaItemFromUri(context, Uri.parse(str), 0);
    }

    @Override
    public String[] getDatabaseUris() {
        return new String[]{URI_ALL_MEDIA, MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()};
    }

    @Override
    public void refresh(MediaFeed mediaFeed, String[] strArr) {
    }

    @Override
    public boolean performOperation(final int i, ArrayList<MediaBucket> arrayList, final Object obj) {
        PendingIntent pendingIntentCreateWriteRequest;
        final ArrayList arrayList2 = new ArrayList();
        Iterator<MediaBucket> it = arrayList.iterator();
        while (it.hasNext()) {
            MediaBucket next = it.next();
            ArrayList<MediaItem> arrayList3 = next.mediaItems != null ? next.mediaItems : this.albums.get(Long.valueOf(next.mediaSet.mId));
            if (arrayList3 != null) {
                arrayList2.addAll(arrayList3);
            }
        }
        if (arrayList2.isEmpty()) {
            return false;
        }
        if (arrayList2.size() > 2000) {
            ((Gallery) this.context).runOnUiThread(new Runnable() {
                @Override
                public final void run() {
                    LocalDataSource.this.showSelectionLimitMessage();
                }
            });
            return false;
        }
        ArrayList arrayList4 = new ArrayList();
        Iterator it2 = arrayList2.iterator();
        while (it2.hasNext()) {
            arrayList4.add(Uri.parse(((MediaItem) it2.next()).mContentUri));
        }
        Runnable runnable = new Runnable() {
            @Override
            public final void run() {
                LocalDataSource.this.performSelectedOperation(i, arrayList2, obj);
            }
        };
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Gallery gallery = (Gallery) this.context;
                if (i == 0) {
                    pendingIntentCreateWriteRequest = MediaStore.createDeleteRequest(this.context.getContentResolver(), arrayList4);
                } else {
                    pendingIntentCreateWriteRequest = MediaStore.createWriteRequest(this.context.getContentResolver(), arrayList4);
                }
                if (i == 0) {
                    runnable = new Runnable() {
                        @Override
                        public final void run() {
                            LocalDataSource.this.reload();
                        }
                    };
                }
                gallery.requestWrite(pendingIntentCreateWriteRequest, runnable);
            } catch (Exception e) {
                showFailure("This media cannot be modified");
            }
        } else {
            runnable.run();
        }
        return false;
    }

     void showSelectionLimitMessage() {
        Toast.makeText(this.context, "Select up to 2,000 items at a time", Toast.LENGTH_LONG).show();
    }

     void performSelectedOperation(int i, ArrayList arrayList, Object obj) {
        modifySequentially(i, arrayList, obj, 0);
    }

    public void reload() {
        ((Gallery) this.context).runOnUiThread(new Runnable() {
            @Override
            public final void run() {
                LocalDataSource.this.reloadGallery();
            }
        });
    }

     void reloadGallery() {
        ((Gallery) this.context).reloadMedia();
    }

    private void showFailure(final String str) {
        ((Gallery) this.context).runOnUiThread(new Runnable() {
            @Override
            public final void run() {
                LocalDataSource.this.showFailureToast(str);
            }
        });
    }

     void showFailureToast(String str) {
        Toast.makeText(this.context, str, Toast.LENGTH_LONG).show();
    }

    public void modifySequentially(final int operation, final ArrayList<MediaItem> items,
            final Object argument, int start) {
        for (int index = start; index < items.size(); index++) {
            MediaItem item = items.get(index);
            Uri uri = Uri.parse(item.mContentUri);
            try {
                if (operation == MediaFeed.OPERATION_DELETE) {
                    this.context.getContentResolver().delete(uri, null, null);
                } else if (operation == MediaFeed.OPERATION_ROTATE
                        && item.getMediaType() == MediaItem.MEDIA_TYPE_IMAGE) {
                    float rotation = Shared.normalizePositive(item.mRotation + ((Float) argument));
                    try (ParcelFileDescriptor descriptor = this.context.getContentResolver().openFileDescriptor(uri, "rw")) {
                        if (descriptor == null) throw new IOException("Media is not writable");
                        ExifInterface exif = new ExifInterface(descriptor.getFileDescriptor());
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                                String.valueOf(Shared.degreesToExifOrientation(rotation)));
                        exif.saveAttributes();
                    }
                    item.mRotation = rotation;
                    sThumbnailCache.delete(Utils.Crc64Long(item.mFilePath));
                }
            } catch (Exception exception) {
                final int retryIndex = index;
                if (Build.VERSION.SDK_INT == 29 && Api29.requestConsent(exception, (Gallery) this.context,
                        () -> modifySequentially(operation, items, argument, retryIndex))) return;
                showFailure("Unable to modify this media");
                reload();
                return;
            }
        }
        reload();
    }

    @android.annotation.TargetApi(29)
    private static final class Api29 {
        private Api29() {
        }

        static boolean requestConsent(Exception exc, Gallery gallery, Runnable runnable) {
            if (!(exc instanceof RecoverableSecurityException)) {
                return false;
            }
            gallery.requestWrite(((RecoverableSecurityException) exc).getUserAction().getActionIntent(), runnable);
            return true;
        }
    }
}
