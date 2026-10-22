package com.zoeykl.simplegallery;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Native original-file viewer; the 3D wall continues to use static previews. */
public final class ImageViewer extends Activity {
    private Drawable drawable;
    private boolean resumed;
    private volatile boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Uri uri = getIntent().getData();
        if (!MediaInfo.isLocal(uri)) { finish(); return; }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener(Gallery::applyWindowInsets);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(getString(R.string.original_image));
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        TextView status = new TextView(this);
        status.setText(R.string.loading_media);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        Button back = new Button(this);
        back.setText(R.string.back_to_gallery);
        back.setOnClickListener(view -> finish());
        controls.addView(back);
        Button share = new Button(this);
        share.setText(R.string.share_original);
        controls.addView(share);
        Button wallpaper = new Button(this);
        wallpaper.setText(R.string.set_as_wallpaper);
        wallpaper.setOnClickListener(view -> startActivity(Photographs.createIntent(this, uri)));
        controls.addView(wallpaper);
        root.addView(controls, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        setContentView(root);
        new Thread(() -> {
            MediaInfo info = MediaInfo.read(this, uri, getIntent().getType());
            share.post(() -> share.setOnClickListener(view -> share(uri, info.mime)));
            try {
                Drawable decoded;
                if (Build.VERSION.SDK_INT >= 28) {
                    decoded = Api28.decode(this, uri);
                } else {
                    Bitmap bitmap = UriTexture.createFromUri(this, uri.toString(), 4096, 4096, 0, null);
                    bitmap = Util.rotate(bitmap, LocalDataSource.readRotation(this, uri));
                    decoded = new BitmapDrawable(getResources(), bitmap);
                }
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    drawable = decoded;
                    image.setImageDrawable(decoded);
                    image.setContentDescription(info.name);
                    status.setVisibility(View.GONE);
                    if (resumed && Build.VERSION.SDK_INT >= 28) Api28.start(decoded);
                });
            } catch (Exception | OutOfMemoryError exception) {
                runOnUiThread(() -> {
                    if (!destroyed) status.setText(R.string.image_format_unavailable);
                });
            }
        }, "OriginalImage").start();
    }

    private void share(Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_SEND).setType(mime == null ? "image/*" : mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newRawUri("Photo", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(intent, getString(R.string.share_original))); }
        catch (RuntimeException exception) {
            Toast.makeText(this, R.string.no_share_app, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onResume() {
        super.onResume(); resumed = true;
        if (Build.VERSION.SDK_INT >= 28) Api28.start(drawable);
    }

    @Override public void onPause() {
        resumed = false;
        if (Build.VERSION.SDK_INT >= 28) Api28.stop(drawable);
        super.onPause();
    }

    @Override public void onDestroy() { destroyed = true; super.onDestroy(); }

    @TargetApi(28) private static final class Api28 {
        static Drawable decode(Activity context, Uri uri) throws java.io.IOException {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.getContentResolver(), uri),
                    (decoder, info, source) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        int[] size = MediaFormats.fitSize(info.getSize().getWidth(), info.getSize().getHeight(), 4096, 4096);
                        decoder.setTargetSize(size[0], size[1]);
                    });
        }
        static void start(Drawable image) { if (image instanceof AnimatedImageDrawable) ((AnimatedImageDrawable)image).start(); }
        static void stop(Drawable image) { if (image instanceof AnimatedImageDrawable) ((AnimatedImageDrawable)image).stop(); }
    }
}
