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

import android.provider.MediaStore;
import com.zoeykl.simplegallery.legacy.app.App;
import com.zoeykl.simplegallery.MenuBar.Menu;
import com.zoeykl.simplegallery.PopupMenu.Option;

import android.app.AlertDialog.Builder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.widget.Toast;
import com.zoeykl.simplegallery.MenuBar;
import com.zoeykl.simplegallery.PopupMenu;
import com.zoeykl.simplegallery.R;
import com.zoeykl.simplegallery.RenderView;
import com.zoeykl.simplegallery.legacy.app.App;
import java.util.ArrayList;
import java.util.List;
import javax.microedition.khronos.opengles.GL11;

public final class HudLayer extends Layer {
    private static final int CAMERA_BUTTON_ICON;
    private static final int CAMERA_BUTTON_ICON_PRESSED;
    private static final int GRID_MODE_ICON;
    private static final int GRID_MODE_PRESSED_ICON;
    public static final int MODE_NORMAL = 0;
    public static final int MODE_SELECT = 1;
    private static final int STACK_MODE_ICON;
    private static final int STACK_MODE_PRESSED_ICON;
    private static final int ZOOM_IN_ICON;
    private static final int ZOOM_IN_ICON_PRESSED;
    private static final int ZOOM_OUT_ICON;
    private static final int ZOOM_OUT_ICON_PRESSED;
    private float mAnimAlpha;
    private boolean mAutoHide;
    private String mCachedCaption;
    private String mCachedCurrentLabel;
    private String mCachedPosition;
    private Context mContext;
    private final MenuBar mFullscreenMenu;
    private GridLayer mGridLayer;
    private long mLastTimeFullOpacity;
    private MenuBar.Menu[] mNormalBottomMenu;
    private MenuBar.Menu[] mNormalBottomMenuNoShare;
    private PathBarLayer mPathBar;
    private final MenuBar mSelectionMenuBottom;
    private final MenuBar mSelectionMenuTop;
    private MenuBar.Menu[] mSingleViewIntentBottomMenu;
    private MenuBar.Menu[] mSingleViewIntentBottomMenuNoShare;
    private TimeBar mTimeBar;
    private final ImageButton mTopRightButton = new ImageButton();
    private final ImageButton mZoomInButton = new ImageButton();
    private final ImageButton mZoomOutButton = new ImageButton();
    private final LoadingLayer mLoadingLayer = new LoadingLayer();
    private RenderView mView = null;
    private int mMode = 0;
    private final Runnable mCameraButtonAction = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent("android.media.action.STILL_IMAGE_CAMERA");
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                HudLayer.this.mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(HudLayer.this.mContext, "No camera app available", Toast.LENGTH_SHORT).show();
            }
        }
    };
    private final Runnable mZoomInButtonAction = new Runnable() {
        @Override
        public void run() {
            HudLayer.this.mGridLayer.zoomInToSelectedItem();
            HudLayer.this.mGridLayer.markDirty(1);
        }
    };
    private final Runnable mZoomOutButtonAction = new Runnable() {
        @Override
        public void run() {
            HudLayer.this.mGridLayer.zoomOutFromSelectedItem();
            HudLayer.this.mGridLayer.markDirty(1);
        }
    };
    private final Runnable mGridModeButtonAction = new Runnable() {
        @Override
        public void run() {
            HudLayer.this.mGridLayer.setState(1);
        }
    };
    private final Runnable mStackModeButtonAction = new Runnable() {
        @Override
        public void run() {
            HudLayer.this.mGridLayer.setState(3);
        }
    };
    private float mAlpha = 1.0f;

    static {

        CAMERA_BUTTON_ICON = R.drawable.btn_camera;

        CAMERA_BUTTON_ICON_PRESSED = R.drawable.btn_camera_pressed;

        ZOOM_IN_ICON = R.drawable.gallery_zoom_in;

        ZOOM_IN_ICON_PRESSED = R.drawable.gallery_zoom_in_touch;

        ZOOM_OUT_ICON = R.drawable.gallery_zoom_out;

        ZOOM_OUT_ICON_PRESSED = R.drawable.gallery_zoom_out_touch;

        GRID_MODE_ICON = R.drawable.mode_stack;

        GRID_MODE_PRESSED_ICON = R.drawable.mode_stack;

        STACK_MODE_ICON = R.drawable.mode_grid;

        STACK_MODE_PRESSED_ICON = R.drawable.mode_grid;
    }

    HudLayer(Context context) throws Resources.NotFoundException {
        this.mNormalBottomMenu = null;
        this.mSingleViewIntentBottomMenu = null;
        this.mNormalBottomMenuNoShare = null;
        this.mSingleViewIntentBottomMenuNoShare = null;
        if (this.mTimeBar == null) {
            this.mTimeBar = new TimeBar(context);
            this.mPathBar = new PathBarLayer();
        }
        this.mTopRightButton.setSize((int) (App.PIXEL_DENSITY * 100.0f), (int) (App.PIXEL_DENSITY * 94.0f));
        this.mZoomInButton.setSize(App.PIXEL_DENSITY * 66.666f, App.PIXEL_DENSITY * 42.0f);
        this.mZoomOutButton.setSize(App.PIXEL_DENSITY * 66.666f, App.PIXEL_DENSITY * 42.0f);
        this.mZoomInButton.setImages(ZOOM_IN_ICON, ZOOM_IN_ICON_PRESSED);
        this.mZoomInButton.setAction(this.mZoomInButtonAction);
        this.mZoomOutButton.setImages(ZOOM_OUT_ICON, ZOOM_OUT_ICON_PRESSED);
        this.mZoomOutButton.setAction(this.mZoomOutButtonAction);
        Resources resources = context.getResources();
        Resources resources2 = context.getResources();

        String string = resources2.getString(R.string.confirm_delete);

        Resources resources3 = context.getResources();

        String string2 = resources3.getString(R.string.cancel);

        PopupMenu.Option[] optionArr = {new PopupMenu.Option(string, resources.getDrawable(R.drawable.icon_delete), new Runnable() {
            @Override
            public void run() {
                HudLayer.this.deleteSelection();
            }
        }), new PopupMenu.Option(string2, resources.getDrawable(R.drawable.icon_cancel), new Runnable() {
            @Override
            public void run() {
            }
        })};
        this.mSelectionMenuBottom = new MenuBar(context);
        Resources resources4 = context.getResources();

        MenuBar.Menu.Builder builder = new MenuBar.Menu.Builder(resources4.getString(R.string.share));

        MenuBar.Menu menuBuild = builder.icon(R.drawable.icon_share).onSelect(new Runnable() {
            @Override
            public void run() {
                HudLayer.this.updateShareMenu();
            }
        }).build();
        Resources resources5 = context.getResources();

        MenuBar.Menu.Builder builder2 = new MenuBar.Menu.Builder(resources5.getString(R.string.delete));

        MenuBar.Menu menuBuild2 = builder2.icon(R.drawable.icon_delete).options(optionArr).build();
        Resources resources6 = context.getResources();

        MenuBar.Menu.Builder builder3 = new MenuBar.Menu.Builder(resources6.getString(R.string.more));

        MenuBar.Menu menuBuild3 = builder3.icon(R.drawable.icon_more).onSelect(new Runnable() {
            @Override
            public void run() throws Resources.NotFoundException {
                HudLayer.this.buildMoreOptions();
            }
        }).build();
        this.mNormalBottomMenu = new MenuBar.Menu[]{menuBuild, menuBuild2, menuBuild3};
        this.mSingleViewIntentBottomMenu = new MenuBar.Menu[]{menuBuild, menuBuild3};
        this.mNormalBottomMenuNoShare = new MenuBar.Menu[]{menuBuild2, menuBuild3};
        this.mSingleViewIntentBottomMenuNoShare = new MenuBar.Menu[]{menuBuild3};
        this.mSelectionMenuBottom.setMenus(this.mNormalBottomMenu);
        this.mSelectionMenuTop = new MenuBar(context);
        MenuBar menuBar = this.mSelectionMenuTop;
        Resources resources7 = context.getResources();

        Resources resources8 = context.getResources();

        menuBar.setMenus(new MenuBar.Menu[]{new MenuBar.Menu.Builder(resources7.getString(R.string.select_all)).onSelect(new Runnable() {
            @Override
            public void run() {
                HudLayer.this.mGridLayer.selectAll();
            }
        }).build(), new MenuBar.Menu.Builder("").build(), new MenuBar.Menu.Builder(resources8.getString(R.string.deselect_all)).onSelect(new Runnable() {
            @Override
            public void run() {
                HudLayer.this.mGridLayer.deselectOrCancelSelectMode();
            }
        }).build()});
        this.mFullscreenMenu = new MenuBar(context);
        MenuBar menuBar2 = this.mFullscreenMenu;
        Resources resources9 = context.getResources();

        MenuBar.Menu.Builder builder4 = new MenuBar.Menu.Builder(resources9.getString(R.string.slideshow));

        Resources resources10 = context.getResources();

        MenuBar.Menu.Builder builder5 = new MenuBar.Menu.Builder(resources10.getString(R.string.menu));

        menuBar2.setMenus(new MenuBar.Menu[]{builder4.icon(R.drawable.icon_play).onSingleTapUp(new Runnable() {
            @Override
            public void run() {
                if (HudLayer.this.getAlpha() == 1.0f) {
                    HudLayer.this.mGridLayer.startSlideshow();
                } else {
                    HudLayer.this.setAlpha(1.0f);
                }
            }
        }).build(), builder5.icon(R.drawable.icon_more).onSingleTapUp(new Runnable() {
            @Override
            public void run() {
                if (HudLayer.this.getAlpha() == 1.0f) {
                    HudLayer.this.mGridLayer.enterSelectionMode();
                } else {
                    HudLayer.this.setAlpha(1.0f);
                }
            }
        }).build()});
    }

    public void setContext(Context context) {
        if (this.mContext != context) {
            this.mContext = context;
            this.mTimeBar.regenerateStringsForContext(context);
        }
    }

    public void buildMoreOptions() throws Resources.NotFoundException {
        boolean z;
        boolean z2;
        boolean z3;
        int mediaType;
        String string;
        PopupMenu.Option[] optionArr;
        ArrayList<MediaBucket> selectedBuckets = this.mGridLayer.getSelectedBuckets();
        int size = selectedBuckets.size();
        if (size <= 1) {
            z = false;
        } else {
            z = true;
        }
        if (size != 1) {
            z2 = false;
            z3 = false;
            mediaType = 0;
        } else {
            MediaBucket mediaBucket = selectedBuckets.get(0);
            MediaSet mediaSet = mediaBucket.mediaSet;
            if (mediaSet != null) {
                z3 = mediaSet.mPicasaAlbumId != -1;
                if (mediaBucket.mediaItems == null || mediaBucket.mediaItems.size() == 0) {
                    z2 = false;
                    mediaType = 0;
                    z = true;
                } else {
                    ArrayList<MediaItem> arrayList = mediaBucket.mediaItems;
                    int size2 = arrayList.size();
                    mediaType = arrayList.get(0).getMediaType();
                    if (size2 == 1) {
                        z2 = true;
                    } else {
                        int i = 1;
                        while (true) {
                            if (i >= size2) {
                                z2 = false;
                                break;
                            } else if (arrayList.get(0).getMediaType() == mediaType) {
                                i++;
                            } else {
                                z2 = false;
                                z = true;
                                break;
                            }
                        }
                    }
                }
            } else {
                return;
            }
        }
        Resources resources = this.mContext.getResources();

        String string2 = resources.getString(R.string.details);
        Resources resources2 = this.mContext.getResources();

        PopupMenu.Option[] optionArr2 = {new PopupMenu.Option(string2, resources2.getDrawable(R.drawable.ic_menu_view_details), new Runnable() {
            @Override
            public void run() {
                CharSequence[] charSequenceArrPopulateDetailModeStrings;
                ArrayList<MediaBucket> selectedBuckets2 = HudLayer.this.mGridLayer.getSelectedBuckets();
                final AlertDialog.Builder builder = new AlertDialog.Builder(HudLayer.this.mContext);
                Resources resources3 = HudLayer.this.mContext.getResources();

                builder.setTitle(resources3.getString(R.string.details));
                boolean z4 = false;
                if (selectedBuckets2 != null && (charSequenceArrPopulateDetailModeStrings = DetailMode.populateDetailModeStrings(HudLayer.this.mContext, selectedBuckets2)) != null) {
                    builder.setItems(charSequenceArrPopulateDetailModeStrings, (DialogInterface.OnClickListener) null);
                    z4 = true;
                }
                HudLayer.this.mGridLayer.deselectAll();
                if (z4) {

                    builder.setNeutralButton(R.string.details_ok, (DialogInterface.OnClickListener) null);
                    App.get(HudLayer.this.mContext).getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            builder.show();
                        }
                    });
                }
            }
        })};
        Resources resources3 = this.mContext.getResources();

        String string3 = resources3.getString(R.string.show_on_map);
        Resources resources4 = this.mContext.getResources();

        PopupMenu.Option[] optionArr3 = {new PopupMenu.Option(string3, resources4.getDrawable(R.drawable.ic_menu_mapmode), new Runnable() {
            @Override
            public void run() {
                MediaItem firstItemSelection = MediaBucketList.getFirstItemSelection(HudLayer.this.mGridLayer.getSelectedBuckets());
                if (firstItemSelection != null) {
                    HudLayer.this.mGridLayer.deselectAll();
                    Util.openMaps(HudLayer.this.mContext, firstItemSelection.mLatitude, firstItemSelection.mLongitude);
                }
            }
        })};
        Resources resources5 = this.mContext.getResources();

        String string4 = resources5.getString(R.string.rotate_left);
        Resources resources6 = this.mContext.getResources();

        Resources resources7 = this.mContext.getResources();

        String string5 = resources7.getString(R.string.rotate_right);
        Resources resources8 = this.mContext.getResources();

        PopupMenu.Option[] optionArr4 = {new PopupMenu.Option(string4, resources6.getDrawable(R.drawable.ic_menu_rotate_left), new Runnable() {
            @Override
            public void run() {
                HudLayer.this.mGridLayer.rotateSelectedItems(-90.0f);
            }
        }), new PopupMenu.Option(string5, resources8.getDrawable(R.drawable.ic_menu_rotate_right), new Runnable() {
            @Override
            public void run() {
                HudLayer.this.mGridLayer.rotateSelectedItems(90.0f);
            }
        })};
        if (z3) {
            optionArr4 = new PopupMenu.Option[0];
        }
        if (z3) {
            Resources resources9 = this.mContext.getResources();

            String string6 = resources9.getString(R.string.set_as_wallpaper);
            Resources resources10 = this.mContext.getResources();

            optionArr = new PopupMenu.Option[]{new PopupMenu.Option(string6, resources10.getDrawable(R.drawable.ic_menu_set_as), new Runnable() {
                @Override
                public void run() {
                    HudLayer.this.openWallpaperPicker();
                }
            })};
        } else {
            PopupMenu.Option[] optionArr5 = new PopupMenu.Option[2];
            if (z3) {
                Resources resources11 = this.mContext.getResources();

                string = resources11.getString(R.string.set_as_wallpaper);
            } else {
                Resources resources12 = this.mContext.getResources();

                string = resources12.getString(R.string.set_as);
            }
            Resources resources13 = this.mContext.getResources();

            optionArr5[0] = new PopupMenu.Option(string, resources13.getDrawable(R.drawable.ic_menu_set_as), new Runnable() {
                @Override
                public void run() {
                    HudLayer.this.openWallpaperPicker();
                }
            });
            Resources resources14 = this.mContext.getResources();

            String string7 = resources14.getString(R.string.crop);
            Resources resources15 = this.mContext.getResources();

            optionArr5[1] = new PopupMenu.Option(string7, resources15.getDrawable(R.drawable.ic_menu_crop), new Runnable() {
                @Override
                public void run() {
                    MediaItem firstItemSelection = MediaBucketList.getFirstItemSelection(HudLayer.this.mGridLayer.getSelectedBuckets());
                    if (firstItemSelection != null) {
                        HudLayer.this.mGridLayer.deselectAll();
                        Intent intent = new Intent("com.android.camera.action.CROP");
                        intent.setClass(HudLayer.this.mContext, CropImage.class);
                        intent.setData(Uri.parse(firstItemSelection.mContentUri));
                        ((Activity) HudLayer.this.mContext).startActivityForResult(intent, 100);
                    }
                }
            });
            optionArr = optionArr5;
        }
        Resources resources16 = this.mContext.getResources();

        PopupMenu.Option[] optionArrConcat = concat(optionArr2, new PopupMenu.Option[]{new PopupMenu.Option("Photo access", resources16.getDrawable(R.drawable.ic_menu_set_as), new PhotoAccessAction())});
        if (!z) {
            if (!z2) {
                if (mediaType == 0) {
                    optionArrConcat = concat(optionArrConcat, optionArr4);
                }
            } else {
                MediaItem firstItemSelection = MediaBucketList.getFirstItemSelection(selectedBuckets);
                if (firstItemSelection.mLatitude != 0.0d && firstItemSelection.mLongitude != 0.0d) {
                    optionArrConcat = concat(optionArrConcat, optionArr3);
                }
                if (mediaType == 0) {
                    optionArrConcat = concat(concat(optionArrConcat, optionArr), optionArr4);
                }
            }
        }
        final MediaItem original = MediaBucketList.getFirstItemSelection(selectedBuckets);
        if (z2 && original != null && original.mContentUri != null) {
            optionArrConcat = concat(optionArrConcat, new PopupMenu.Option[]{new PopupMenu.Option(
                    mContext.getString(R.string.open_original), mContext.getDrawable(R.drawable.ic_menu_view_details),
                    () -> ((Gallery)mContext).openMediaUri(Uri.parse(original.mContentUri), original.mMimeType))});
        }
        this.mSelectionMenuBottom.getMenus()[this.mSelectionMenuBottom.getMenus().length - 1].options = optionArrConcat;
    }

    class PhotoAccessAction implements Runnable {
        PhotoAccessAction() {
        }

         void requestPhotoAccess() {
            ((Gallery) HudLayer.this.mContext).requestMediaAccess();
        }

        @Override
        public void run() {
            ((Gallery) HudLayer.this.mContext).runOnUiThread(new Runnable() {
                @Override
                public final void run() {
                    ((Gallery) HudLayer.this.mContext).requestMediaAccess();
                }
            });
        }
    }

    private static final PopupMenu.Option[] concat(PopupMenu.Option[] optionArr, PopupMenu.Option[] optionArr2) {
        PopupMenu.Option[] optionArr3 = new PopupMenu.Option[optionArr.length + optionArr2.length];
        System.arraycopy(optionArr, 0, optionArr3, 0, optionArr.length);
        System.arraycopy(optionArr2, 0, optionArr3, optionArr.length, optionArr2.length);
        return optionArr3;
    }

    public void updateNumItemsSelected(int i) {
        StringBuilder sbAppend = new StringBuilder().append(" ");
        Context context = this.mContext;

        this.mSelectionMenuTop.updateMenu(new MenuBar.Menu.Builder(i + sbAppend.append(context.getString(i == 1 ? R.string.item : R.string.items)).toString()).config(MenuBar.MENU_TITLE_STYLE_TEXT).build(), 1);
    }

    protected void deleteSelection() {
        this.mGridLayer.deleteSelection();
    }

    void setGridLayer(GridLayer gridLayer) {
        this.mGridLayer = gridLayer;
        updateViews();
    }

    int getMode() {
        return this.mMode;
    }

    void setMode(int i) {
        if (this.mMode != i) {
            this.mMode = i;
            updateViews();
        }
    }

    @Override
    protected void onSizeChanged() {
        float f = this.mWidth;
        float f2 = this.mHeight;
        closeSelectionMenu();
        this.mTimeBar.setPosition(0.0f, f2 - (App.PIXEL_DENSITY * 48.0f));
        this.mTimeBar.setSize(f, App.PIXEL_DENSITY * 48.0f);
        this.mSelectionMenuTop.setPosition(0.0f, 0.0f);
        this.mSelectionMenuTop.setSize(f, App.PIXEL_DENSITY * 45.0f);
        this.mSelectionMenuBottom.setPosition(0.0f, f2 - (App.PIXEL_DENSITY * 45.0f));
        this.mSelectionMenuBottom.setSize(f, App.PIXEL_DENSITY * 45.0f);
        this.mFullscreenMenu.setPosition(0.0f, f2 - (App.PIXEL_DENSITY * 45.0f));
        this.mFullscreenMenu.setSize(f, App.PIXEL_DENSITY * 45.0f);
        this.mPathBar.setPosition(0.0f, App.PIXEL_DENSITY * (-4.0f));
        computeSizeForPathbar();
        this.mTopRightButton.setPosition(f - this.mTopRightButton.getWidth(), 0.0f);
        float height = (f2 - (App.PIXEL_DENSITY * 45.0f)) - this.mZoomInButton.getHeight();
        this.mZoomInButton.setPosition(f - this.mZoomInButton.getWidth(), height);
        this.mZoomOutButton.setPosition(f - (this.mZoomInButton.getWidth() * 2.0f), height);
    }

    private void computeSizeForPathbar() {
        this.mPathBar.setSize(this.mWidth - (App.PIXEL_DENSITY * (this.mGridLayer.getState() == 2 ? 32.0f : 120.0f)), (float) Math.ceil(App.PIXEL_DENSITY * 39.0f));
        this.mPathBar.recomputeComponents();
    }

    public void setFeed(MediaFeed mediaFeed, int i, boolean z) {
        this.mTimeBar.setFeed(mediaFeed, i, z);
    }

    public void onGridStateChanged() {
        updateViews();
    }

    private void updateViews() {
        if (mGridLayer == null)
            return;
        final int state = mGridLayer.getState();
        // Show the selection menu in selection mode.
        final boolean selectionMode = mMode == MODE_SELECT;
        final boolean fullscreenMode = state == GridLayer.STATE_FULL_SCREEN;
        final boolean stackMode = state == GridLayer.STATE_MEDIA_SETS || state == GridLayer.STATE_TIMELINE;
        mSelectionMenuTop.setHidden(!selectionMode || fullscreenMode);
        mSelectionMenuBottom.setHidden(!selectionMode);
        mFullscreenMenu.setHidden(!fullscreenMode || selectionMode);
        mZoomInButton.setHidden(mFullscreenMenu.isHidden());
        mZoomOutButton.setHidden(mFullscreenMenu.isHidden());

        // Show the time bar in stack and grid states, except in selection mode.
        mTimeBar.setHidden(fullscreenMode || selectionMode || stackMode);
        // mTimeBar.setHidden(selectionMode || (state !=
        // GridLayer.STATE_TIMELINE && state != GridLayer.STATE_GRID_VIEW));

        // Hide the path bar and top-right button in selection mode.
        mPathBar.setHidden(selectionMode);
        mTopRightButton.setHidden(selectionMode || fullscreenMode);
        computeSizeForPathbar();

        // Configure the top-right button.
        int image = 0;
        int pressedImage = 0;
        Runnable action = null;
        final ImageButton topRightButton = mTopRightButton;
        int height = (int) (94 * App.PIXEL_DENSITY);
        switch (state) {
        case GridLayer.STATE_MEDIA_SETS:
            image = CAMERA_BUTTON_ICON;
            pressedImage = CAMERA_BUTTON_ICON_PRESSED;
            action = mCameraButtonAction;
            break;
        case GridLayer.STATE_GRID_VIEW:
            height /= 2;
            image = STACK_MODE_ICON;
            pressedImage = STACK_MODE_PRESSED_ICON;
            action = mStackModeButtonAction;
            break;
        case GridLayer.STATE_TIMELINE:
            image = GRID_MODE_ICON;
            pressedImage = GRID_MODE_PRESSED_ICON;
            action = mGridModeButtonAction;
            break;
        case GridLayer.STATE_FULL_SCREEN:
            if (getGridLayer() != null && getGridLayer().getFeed() != null
                    && getGridLayer().getFeed().getExpandedMediaSet() != null) {
                if (getGridLayer().getFeed().getExpandedMediaSet().mId == LocalDataSource.CAMERA_BUCKET_ID) {
                    image = CAMERA_BUTTON_ICON;
                    pressedImage = CAMERA_BUTTON_ICON_PRESSED;
                    action = mCameraButtonAction;
                    topRightButton.setHidden(false);
                }
            }
            break;
        default:
            break;
        }
        topRightButton.setSize((int) (100 * App.PIXEL_DENSITY), height);
        topRightButton.setImages(image, pressedImage);
        topRightButton.setAction(action);
    }

    public TimeBar getTimeBar() {
        return this.mTimeBar;
    }

    public PathBarLayer getPathBar() {
        return this.mPathBar;
    }

    public GridLayer getGridLayer() {
        return this.mGridLayer;
    }

    @Override
    public boolean update(RenderView renderView, float f) {
        float f2;
        if (this.mAlpha != 1.0f) {
            f2 = 1.0f;
        } else {
            f2 = 4.0f;
        }
        this.mAnimAlpha = FloatUtils.animate(this.mAnimAlpha, this.mAlpha, f * f2);
        if (this.mAutoHide && this.mAlpha == 1.0f && this.mMode != 1 && System.currentTimeMillis() - this.mLastTimeFullOpacity >= 5000) {
            setAlpha(0.0f);
        }
        return this.mAnimAlpha != this.mAlpha;
    }

    @Override
    public void renderOpaque(RenderView renderView, GL11 gl11) {
    }

    @Override
    public void renderBlended(RenderView renderView, GL11 gl11) {
        renderView.setAlpha(this.mAnimAlpha);
    }

    public void setAlpha(float f) {
        float f2 = this.mAlpha;
        this.mAlpha = f;
        if (f2 != f && this.mView != null) {
            this.mView.requestRender();
        }
        if (f == 1.0f) {
            this.mLastTimeFullOpacity = System.currentTimeMillis();
            App.get(this.mContext).getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (HudLayer.this.mView != null) {
                        HudLayer.this.mView.requestRender();
                    }
                }
            }, 5000L);
        }
    }

    public float getAlpha() {
        return this.mAlpha;
    }

    public void setTimeBarTime(long j) {
    }

    @Override
    public void generate(RenderView renderView, RenderView.Lists lists) {
        lists.opaqueList.add(this);
        lists.blendedList.add(this);
        lists.hitTestList.add(this);
        lists.updateList.add(this);
        this.mTopRightButton.generate(renderView, lists);
        this.mZoomInButton.generate(renderView, lists);
        this.mZoomOutButton.generate(renderView, lists);
        this.mTimeBar.generate(renderView, lists);
        this.mSelectionMenuTop.generate(renderView, lists);
        this.mSelectionMenuBottom.generate(renderView, lists);
        this.mFullscreenMenu.generate(renderView, lists);
        this.mPathBar.generate(renderView, lists);
        this.mView = renderView;
    }

    @Override
    public boolean containsPoint(float f, float f2) {
        return false;
    }

    public void cancelSelection() {
        this.mSelectionMenuBottom.close();
        closeSelectionMenu();
        setMode(0);
    }

    public void closeSelectionMenu() {
        this.mSelectionMenuBottom.close();
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        return this.mMode == 1 ? false : false;
    }

    public boolean isLoaded() {
        return this.mLoadingLayer.isLoaded();
    }

    void reset() {
        this.mLoadingLayer.reset();
        this.mTimeBar.regenerateStringsForContext(this.mContext);
    }

    public void fullscreenSelectionChanged(MediaItem mediaItem, int i, int i2) {
        if (mediaItem == null) {
            return;
        }
        String str = i + "/" + i2;
        this.mCachedCaption = mediaItem.mCaption;
        this.mCachedPosition = str;
        this.mCachedCurrentLabel = str;
        this.mPathBar.changeLabel(str);
    }

    public void updateShareMenu() {
        String str;
        int numItems;
        ArrayList<MediaItem> arrayList;
        ArrayList<MediaBucket> selectedBuckets = this.mGridLayer.getSelectedBuckets();
        ArrayList arrayList2 = new ArrayList();
        PopupMenu.Option[] optionArr = null;
        if (selectedBuckets.isEmpty()) {
            str = null;
        } else {
            int size = selectedBuckets.size();
            int mediaType = -1;
            str = null;
            for (int i = 0; i < size; i++) {
                MediaBucket mediaBucket = selectedBuckets.get(i);
                if (mediaBucket.mediaItems != null && !mediaBucket.mediaItems.isEmpty()) {
                    arrayList = mediaBucket.mediaItems;
                    numItems = arrayList.size();
                } else if (mediaBucket.mediaSet == null) {
                    numItems = 0;
                    arrayList = null;
                } else {
                    ArrayList<MediaItem> items = mediaBucket.mediaSet.getItems();
                    numItems = mediaBucket.mediaSet.getNumItems();
                    arrayList = items;
                }
                for (int i2 = 0; i2 < numItems; i2++) {
                    MediaItem mediaItem = arrayList.get(i2);
                    String itemType = MediaFormats.resolve(mediaItem.mMimeType, mediaItem.mCaption, null);
                    if (itemType == null) itemType = mediaItem.getMediaType() == MediaItem.MEDIA_TYPE_VIDEO ? "video/*" : "image/*";
                    str = str == null ? itemType : MediaFormats.commonType(str, itemType);
                    if (mediaItem.mContentUri != null) {
                        arrayList2.add(Uri.parse(mediaItem.mContentUri));
                    }
                }
            }
        }
        if (arrayList2.size() != 0) {
            final Intent intent = new Intent();
            if (str == null) {
                str = "image/jpeg";
            }
            if (str.contains("text")) {
                intent.setAction("android.intent.action.SEND");
                intent.setType(str);
                StringBuilder sb = new StringBuilder();
                int size2 = arrayList2.size();
                for (int i3 = 0; i3 < size2; i3++) {
                    sb.append(arrayList2.get(i3));
                    if (i3 != size2 - 1) {
                        sb.append('\n');
                    }
                }
                intent.putExtra("android.intent.extra.TEXT", sb.toString());
            } else {
                if (arrayList2.size() == 1) {
                    intent.setAction("android.intent.action.SEND");
                    intent.putExtra("android.intent.extra.STREAM", (Parcelable) arrayList2.get(0));
                } else {
                    intent.setAction("android.intent.action.SEND_MULTIPLE");
                    intent.putExtra("android.intent.extra.STREAM", arrayList2);
                }
                intent.setType(str);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!str.contains("text")) {
                ClipData clips = ClipData.newRawUri("Media", (Uri) arrayList2.get(0));
                for (int index = 1; index < arrayList2.size(); index++) clips.addItem(new ClipData.Item((Uri) arrayList2.get(index)));
                intent.setClipData(clips);
            }
            PackageManager packageManager = this.mContext.getPackageManager();
            List<ResolveInfo> listQueryIntentActivities = packageManager.queryIntentActivities(intent, 0);
            int size3 = listQueryIntentActivities.size();
            optionArr = new PopupMenu.Option[size3];
            for (int i4 = 0; i4 != size3; i4++) {
                final ResolveInfo resolveInfo = listQueryIntentActivities.get(i4);
                optionArr[i4] = new PopupMenu.Option(resolveInfo.loadLabel(packageManager).toString(), resolveInfo.loadIcon(packageManager), new Runnable() {
                    @Override
                    public void run() {
                        HudLayer.this.startResolvedActivity(intent, resolveInfo);
                    }
                });
            }
        }
        this.mSelectionMenuBottom.getMenus()[0].options = optionArr;
    }

    public void startResolvedActivity(Intent intent, ResolveInfo resolveInfo) {
        final Intent intent2 = new Intent(intent);
        ActivityInfo activityInfo = resolveInfo.activityInfo;
        intent2.setComponent(new ComponentName(activityInfo.applicationInfo.packageName, activityInfo.name));
        App.get(this.mContext).getHandler().post(new Runnable() {
            @Override
            public void run() {
                intent2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                HudLayer.this.mContext.startActivity(intent2);
            }
        });
    }

    public void autoHide(boolean z) {
        this.mAutoHide = z;
    }

    public void swapFullscreenLabel() {
        this.mCachedCurrentLabel = (this.mCachedCurrentLabel == this.mCachedCaption || this.mCachedCaption == null) ? this.mCachedPosition : this.mCachedCaption;
        this.mPathBar.changeLabel(this.mCachedCurrentLabel);
    }

    public void clear() {
    }

    public void enterSelectionMode() {
        if (this.mGridLayer.feedAboutToChange()) {
            return;
        }
        if (this.mGridLayer.getPickIntent()) {
            this.mSingleViewIntentBottomMenu = this.mSingleViewIntentBottomMenuNoShare;
            this.mNormalBottomMenu = this.mNormalBottomMenuNoShare;
        }
        setAlpha(1.0f);
        setMode(1);
        if (this.mGridLayer.noDeleteMode()) {
            this.mSelectionMenuBottom.setMenus(this.mSingleViewIntentBottomMenu);
        } else {
            this.mSelectionMenuBottom.setMenus(this.mNormalBottomMenu);
        }
    }

    public void computeBottomMenu() {
        ArrayList<MediaBucket> selectedBuckets = this.mGridLayer.getSelectedBuckets();
        if (this.mSelectionMenuBottom.getMenus() == this.mSingleViewIntentBottomMenu) {
            return;
        }
        int size = selectedBuckets.size();
        for (int i = 0; i < size; i++) {
            MediaBucket mediaBucket = selectedBuckets.get(i);
            if (mediaBucket != null && mediaBucket.mediaSet != null && mediaBucket.mediaSet.mPicasaAlbumId != -1) {
                this.mSelectionMenuBottom.setMenus(this.mSingleViewIntentBottomMenu);
                return;
            }
        }
    }

    public Layer getMenuBar() {
        return this.mFullscreenMenu;
    }

    public void hideZoomButtons(boolean z) {
        this.mZoomInButton.setHidden(z);
        this.mZoomOutButton.setHidden(z);
    }
    private void openWallpaperPicker() {
        MediaItem item = MediaBucketList.getFirstItemSelection(mGridLayer.getSelectedBuckets());
        if (item == null) return;
        Uri uri = Uri.parse(item.mContentUri);
        mGridLayer.deselectAll();
        Activity activity = (Activity) mContext;
        activity.runOnUiThread(() -> activity.startActivity(Photographs.createIntent(activity, uri)));
    }
}
