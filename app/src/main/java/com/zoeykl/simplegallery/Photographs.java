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

// Modified for SimpleGallery: in-app wallpaper picker.
package com.zoeykl.simplegallery;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/** In-app still wallpaper picker. No implicit intents or external wallpaper editor. */
public final class Photographs extends Activity {
    private WallpaperPreview preview;
    private RadioGroup destinations;
    private TextView status;
    private LinearLayout controls;
    private Button apply;
    private Job job;

    static Intent createIntent(Context context, Uri uri) {
        Intent intent = new Intent(context, Photographs.class).setData(uri);
        intent.setClipData(ClipData.newRawUri("Wallpaper", uri));
        return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Uri uri = getIntent().getData();
        if (!MediaInfo.isLocal(uri)) { finish(); return; }
        job = (Job) getLastNonConfigurationInstance();
        if (job == null) job = new Job(getApplicationContext(), uri);
        job.owner = this;
        Point screen = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(screen);
        preview = new WallpaperPreview(this, (float) screen.x / screen.y);
        if (state != null) {
            preview.zoom = state.getFloat("zoom", 1);
            preview.centerX = state.getFloat("x", .5f);
            preview.centerY = state.getFloat("y", .5f);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener(Gallery::applyWindowInsets);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        addButton(controls, R.string.wallpaper_zoom_out, () -> preview.zoomBy(.8f));
        addButton(controls, R.string.wallpaper_zoom_in, () -> preview.zoomBy(1.25f));
        addButton(controls, R.string.wallpaper_reset, preview::reset);
        root.addView(controls);
        destinations = new RadioGroup(this);
        destinations.setOrientation(RadioGroup.HORIZONTAL);
        destinations.setGravity(Gravity.CENTER);
        int[] labels = {R.string.wallpaper_home, R.string.wallpaper_lock, R.string.wallpaper_both};
        for (int i = 0; i < labels.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(i + 1); // IDs are also WallpaperManager's SYSTEM, LOCK, or both flags.
            button.setText(labels[i]);
            button.setTextColor(Color.WHITE);
            destinations.addView(button, new RadioGroup.LayoutParams(0, -2, 1));
        }
        destinations.check(state == null ? 3 : state.getInt("destination", 3));
        root.addView(destinations);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        addButton(actions, R.string.cancel, this::finish);
        apply = addButton(actions, R.string.set_as_wallpaper, this::applyWallpaper);
        root.addView(actions);
        setContentView(root);
        render();
        if (!job.started) job.load();
    }

    private Button addButton(LinearLayout row, int label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        return button;
    }

    private void render() {
        if (isFinishing() || isDestroyed()) return;
        if (job.success) {
            Toast.makeText(this, R.string.wallpaper_done, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK); finish(); return;
        }
        preview.bitmap = job.bitmap;
        preview.invalidate();
        boolean ready = job.bitmap != null && !job.applying && !job.blocked;
        apply.setEnabled(ready);
        preview.setEnabled(ready);
        for (int i = 0; i < controls.getChildCount(); i++) controls.getChildAt(i).setEnabled(ready);
        for (int i = 0; i < destinations.getChildCount(); i++) destinations.getChildAt(i).setEnabled(ready);
        status.setText(job.error != 0 ? job.error : job.applying ? R.string.wallpaper
                : job.bitmap == null ? R.string.loading_media : R.string.wallpaper_adjust);
    }

    private void applyWallpaper() {
        if (job.bitmap == null || job.applying || job.blocked) return;
        job.apply(preview.crop(), preview.aspect, destinations.getCheckedRadioButtonId());
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putFloat("zoom", preview.zoom);
        out.putFloat("x", preview.centerX);
        out.putFloat("y", preview.centerY);
        out.putInt("destination", destinations.getCheckedRadioButtonId());
        super.onSaveInstanceState(out);
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onDestroy() {
        if (job != null && job.owner == this) job.owner = null;
        super.onDestroy();
    }

    /** Retained across rotation: workers hold application context, never a destroyed Activity. */
    private static final class Job {
        final Context context;
        final Uri uri;
        final Handler main = new Handler(Looper.getMainLooper());
        Photographs owner;
        Bitmap bitmap;
        boolean started, applying, success, blocked;
        int error;
        Job(Context context, Uri uri) { this.context = context; this.uri = uri; }
        void changed() { if (owner != null) owner.render(); }
        void load() {
            started = true;
            new Thread(() -> {
                try {
                    WallpaperManager manager = WallpaperManager.getInstance(context);
                    if (!manager.isWallpaperSupported() || !manager.isSetWallpaperAllowed()) {
                        main.post(() -> { blocked = true; error = R.string.wallpaper_unavailable; changed(); });
                        return;
                    }
                    Bitmap decoded;
                    if (Build.VERSION.SDK_INT >= 28) decoded = Api28.decode(context, uri);
                    else {
                        decoded = UriTexture.createFromUri(context, uri.toString(), 4096, 4096, 0, null);
                        if (decoded == null) throw new java.io.IOException("Image decode failed");
                        decoded = Util.rotate(decoded, LocalDataSource.readRotation(context, uri));
                    }
                    final Bitmap image = decoded;
                    main.post(() -> { bitmap = image; changed(); });
                } catch (Exception | OutOfMemoryError exception) {
                    main.post(() -> { error = R.string.image_format_unavailable; changed(); });
                }
            }, "WallpaperDecode").start();
        }
        void apply(float[] crop, float aspect, int flags) {
            applying = true; error = 0;
            new Thread(() -> {
                Bitmap output = null;
                try {
                    // Flatten transparency onto black, using precisely the crop shown in the preview.
                    int width = Math.max(1, Math.round(crop[2] - crop[0]));
                    int height = Math.max(1, Math.round(width / aspect));
                    output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(output);
                    canvas.drawColor(Color.BLACK);
                    canvas.scale(width / (crop[2] - crop[0]), height / (crop[3] - crop[1]));
                    canvas.translate(-crop[0], -crop[1]);
                    canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
                    int id = WallpaperManager.getInstance(context).setBitmap(output, null, false, flags);
                    if (id == 0) throw new java.io.IOException("Wallpaper rejected");
                    main.post(() -> { applying = false; success = true; changed(); });
                } catch (Exception | OutOfMemoryError exception) {
                    main.post(() -> { applying = false; error = R.string.wallpaper_failed; changed(); });
                } finally { if (output != null) output.recycle(); }
            }, "WallpaperApply").start();
        }
    }

    @TargetApi(28) private static final class Api28 {
        static Bitmap decode(Context context, Uri uri) throws java.io.IOException {
            // decodeBitmap returns a still frame for animated WebP/GIF and honors EXIF orientation.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), uri),
                    (decoder, info, source) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        int[] size = MediaFormats.fitSize(info.getSize().getWidth(), info.getSize().getHeight(), 4096, 4096);
                        decoder.setTargetSize(size[0], size[1]);
                    });
        }
    }
}
