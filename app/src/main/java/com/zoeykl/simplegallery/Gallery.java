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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.ClipData;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.zoeykl.simplegallery.legacy.app.App;

public final class Gallery extends Activity {
    public static final String REVIEW_ACTION = "com.zoeykl.simplegallery.action.REVIEW";
    private App app;
    private GridLayer grid;
    private Runnable pendingWrite;
    private RenderView render;
    private boolean wasPaused;
    private static final int OPEN_MEDIA = 30;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.app = new App(this);
        getWindow().getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public final WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                return Gallery.applyWindowInsets(view, windowInsets);
            }
        });
        if (canRead() || "android.intent.action.VIEW".equals(getIntent().getAction())) {
            openGallery();
        } else {
            showPermissionPage();
        }
    }

    static  WindowInsets applyWindowInsets(View view, WindowInsets windowInsets) {
        view.setPadding(windowInsets.getSystemWindowInsetLeft(), windowInsets.getSystemWindowInsetTop(), windowInsets.getSystemWindowInsetRight(), windowInsets.getSystemWindowInsetBottom());
        return windowInsets;
    }

    private boolean canRead() {
        if (Build.VERSION.SDK_INT >= 33) {
            return granted("android.permission.READ_MEDIA_IMAGES") || granted("android.permission.READ_MEDIA_VIDEO") || (Build.VERSION.SDK_INT >= 34 && granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"));
        }
        return granted("android.permission.READ_EXTERNAL_STORAGE");
    }

    private boolean granted(String str) {
        return checkSelfPermission(str) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public void requestMediaAccess() {
        if (Build.VERSION.SDK_INT < 34) {
            if (Build.VERSION.SDK_INT < 33) {
                if (Build.VERSION.SDK_INT > 28) {
                    requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, 10);
                    return;
                } else {
                    requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"}, 10);
                    return;
                }
            }
            requestPermissions(new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"}, 10);
            return;
        }
        requestPermissions(new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"}, 10);
    }

    private void showPermissionPage() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(40, 64, 40, 40);
        linearLayout.setGravity(17);
        TextView textView = new TextView(this);
        textView.setText("SimpleGallery");
        textView.setTextSize(28.0f);
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText("Allow access to photos and videos to browse your albums in 3D. You can change your selection later from the menu.");
        textView2.setPadding(0, 24, 0, 24);
        linearLayout.addView(textView2);
        Button button = new Button(this);
        button.setText("Choose photos and videos");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                Gallery.this.onChoosePhotosClicked(view);
            }
        });
        linearLayout.addView(button);
        Button button2 = new Button(this);
        button2.setText("App permissions");
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                Gallery.this.onAppPermissionsClicked(view);
            }
        });
        linearLayout.addView(button2);
        Button openFile = new Button(this);
        openFile.setText(R.string.open_media_file);
        openFile.setOnClickListener(view -> openDocument());
        linearLayout.addView(openFile);
        setContentView(linearLayout);
    }

     void onChoosePhotosClicked(View view) {
        requestMediaAccess();
    }

     void onAppPermissionsClicked(View view) {
        startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName())));
    }

    private void openGallery() {
        if (this.render == null) {
            this.render = new RenderView(this);
            this.grid = new GridLayer(this, (int) (App.PIXEL_DENSITY * 96.0f), (int) (App.PIXEL_DENSITY * 72.0f), new GridLayoutInterface(4), this.render);
            this.render.setRootLayer(this.grid);
            FrameLayout frameLayout = new FrameLayout(this);
            frameLayout.addView(this.render);
            final Button button = new Button(this);
            button.setText("⋮");
            button.setTextSize(24.0f);
            button.setContentDescription("Gallery options");
            button.setTextColor(-1);
            button.setBackgroundColor(-2013265920);
            int i = (int) (getResources().getDisplayMetrics().density * 44.0f);
            frameLayout.addView(button, new FrameLayout.LayoutParams(i, i, 53));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    Gallery.this.showGalleryOptions(button, view);
                }
            });
            setContentView(frameLayout);
            registerForContextMenu(this.render);
        }
        loadSource();
    }

     void showGalleryOptions(Button button, View view) {
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(this, button);
        popupMenu.getMenu().add(R.string.open_media_file).setOnMenuItemClickListener(item -> { openDocument(); return true; });
        popupMenu.getMenu().add("Choose photos / permissions").setOnMenuItemClickListener(this::onPhotoAccessMenuItem);
        popupMenu.getMenu().add("Refresh albums").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public final boolean onMenuItemClick(MenuItem menuItem) {
                return Gallery.this.onRefreshMenuItem(menuItem);
            }
        });
        popupMenu.show();
    }

     boolean onPhotoAccessMenuItem(MenuItem menuItem) {
        requestMediaAccess();
        return true;
    }

     boolean onRefreshMenuItem(MenuItem menuItem) {
        loadSource();
        return true;
    }

    public void reloadMedia() {
        loadSource();
    }

    private void loadSource() {
        if (this.grid == null) {
            return;
        }
        Intent intent = getIntent();
        String action = intent.getAction();
        boolean z = "android.intent.action.VIEW".equals(action) || REVIEW_ACTION.equals(action);
        boolean z2 = "android.intent.action.PICK".equals(action) || "android.intent.action.GET_CONTENT".equals(action);
        LocalDataSource localDataSource = new LocalDataSource(this, (!z || intent.getData() == null) ? LocalDataSource.URI_ALL_MEDIA : intent.getData().toString(), z);
        String type = intent.getType();
        localDataSource.setMimeFilter((z2 && type != null && type.startsWith("video/")) ? false : true, (z2 && type != null && type.startsWith("image/")) ? false : true);
        this.grid.setDataSource(localDataSource);
        this.grid.setPickIntent(z2);
        if (z) {
            this.grid.setViewIntent(true, "Photo");
        }
    }

    @Override
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i, strArr, iArr);
        if (i == 10) {
            if (canRead()) {
                openGallery();
                if (this.render.getParent() == null) {
                    recreate();
                    return;
                }
                return;
            }
            if (this.render != null) {
                this.grid.shutdown();
                this.render.shutdown();
                this.grid = null;
                this.render = null;
            }
            showPermissionPage();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Choose photos / permissions");
        menu.add(0, 2, 1, "Refresh albums");
        menu.add(0, 3, 2, R.string.open_media_file);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == 3) { openDocument(); return true; }
        if (menuItem.getItemId() == 1) {
            requestMediaAccess();
        } else {
            loadSource();
        }
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {
        super.onCreateContextMenu(contextMenu, view, contextMenuInfo);
        contextMenu.add("Choose photos / permissions").setOnMenuItemClickListener(this::onPhotoAccessMenuItem);
        contextMenu.add(R.string.open_media_file).setOnMenuItemClickListener(item -> { openDocument(); return true; });
    }

    private void openDocument() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*", "application/octet-stream", "application/x-matroska", "application/webm"});
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(picker, OPEN_MEDIA); }
        catch (android.content.ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.file_picker_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    void openMediaUri(Uri uri, String suppliedType) {
        if (!MediaInfo.isLocal(uri)) return;
        new Thread(() -> {
            MediaInfo info = MediaInfo.read(this, uri, suppliedType);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!MediaFormats.isSpecificMediaType(info.mime)) {
                    Toast.makeText(this, R.string.unrecognized_media, Toast.LENGTH_LONG).show();
                    return;
                }
                Intent viewer = new Intent(this, MediaFormats.isVideo(info.mime) ? MovieView.class : ImageViewer.class);
                viewer.setDataAndType(uri, info.mime);
                viewer.setClipData(ClipData.newRawUri(info.name, uri));
                viewer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(viewer);
            });
        }, "OpenMedia").start();
    }

    void requestWrite(final PendingIntent pendingIntent, final Runnable runnable) {
        runOnUiThread(new Runnable() {
            @Override
            public final void run() {
                Gallery.this.launchWriteConsent(runnable, pendingIntent);
            }
        });
    }
    void launchWriteConsent(Runnable action, PendingIntent consent) {
        if (this.pendingWrite != null) return;
        this.pendingWrite = action;
        try {
            startIntentSenderForResult(consent.getIntentSender(), 20, null, 0, 0, 0);
        } catch (Exception exception) {
            this.pendingWrite = null;
            Toast.makeText(this, "Unable to request media access", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == OPEN_MEDIA) {
            if (i2 == RESULT_OK && intent != null && intent.getData() != null) {
                Uri uri = intent.getData();
                if ((intent.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
                        && (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                    try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                    catch (SecurityException ignored) {}
                }
                openMediaUri(uri, intent.getType());
            }
            return;
        }
        if (i == 20) {
            Runnable runnable = this.pendingWrite;
            this.pendingWrite = null;
            if (i2 != -1 || runnable == null) {
                return;
            }
            new Thread(runnable, "MediaWrite").start();
            return;
        }
        if (i != 10 || i2 != -1) {
            if (i != 100 || i2 != -1) {
                return;
            }
            loadSource();
            return;
        }
        setResult(i2, intent);
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openGallery();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.app.onResume();
        if (this.render != null) {
            this.render.onResume();
        }
        if (this.wasPaused && this.pendingWrite == null) {
            if (canRead()) {
                if (this.render == null) {
                    openGallery();
                } else {
                    loadSource();
                }
            } else if (!"android.intent.action.VIEW".equals(getIntent().getAction()) && this.render != null) {
                this.grid.shutdown();
                this.render.shutdown();
                this.grid = null;
                this.render = null;
                showPermissionPage();
            }
        }
        this.wasPaused = false;
    }

    @Override
    public void onPause() {
        this.wasPaused = true;
        if (this.render != null) {
            this.render.onPause();
        }
        this.app.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (this.grid != null) {
            this.grid.shutdown();
        }
        if (this.render != null) {
            this.render.shutdown();
        }
        this.app.shutdown();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (this.grid != null) {
            this.grid.markDirty(30);
        }
    }

    @Override
    public boolean onKeyDown(int i, KeyEvent keyEvent) {
        if (i != 82) {
            return (this.render != null && this.render.onKeyDown(i, keyEvent)) || super.onKeyDown(i, keyEvent);
        }
        openOptionsMenu();
        return true;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (this.render != null) {
            this.render.handleLowMemory();
        }
    }
}
