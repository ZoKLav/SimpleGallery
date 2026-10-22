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
import java.util.ArrayList;
import java.util.HashMap;

public final class PicasaDataSource implements DataSource {
    public static final DiskCache sThumbnailCache = new DiskCache("unused-cloud-thumbs");

    public PicasaDataSource(Context context) {
    }

    public static HashMap<String, Boolean> getAccountStatus(Context context) {
        return new HashMap<>();
    }

    @Override
    public void loadMediaSets(MediaFeed mediaFeed) {
    }

    @Override
    public void loadItemsForSet(MediaFeed mediaFeed, MediaSet mediaSet, int i, int i2) {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public boolean performOperation(int i, ArrayList<MediaBucket> arrayList, Object obj) {
        return false;
    }

    @Override
    public DiskCache getThumbnailCache() {
        return sThumbnailCache;
    }

    @Override
    public String[] getDatabaseUris() {
        return new String[0];
    }

    @Override
    public void refresh(MediaFeed mediaFeed, String[] strArr) {
    }
}
