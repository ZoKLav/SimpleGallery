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

import android.database.Cursor;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import com.zoeykl.simplegallery.legacy.app.App;
import java.io.InputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.media.ExifInterface;
import android.media.FaceDetector;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.zoeykl.simplegallery.BitmapManager;
import com.zoeykl.simplegallery.R;
import com.zoeykl.simplegallery.legacy.app.App;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class CropImage extends MonitoredActivity {
    public static final int CROP_MSG = 10;
    public static final int CROP_MSG_INTERNAL = 100;
    private static final String TAG = "CropImage";
    private static final HashMap<Context, MediaScannerConnection> mConnectionMap = new HashMap<>();
    private int mAspectX;
    private int mAspectY;
    private Bitmap mBitmap;
    private ContentResolver mContentResolver;
    HighlightView mCrop;
    private CropImageView mImageView;
    private MediaItem mItem;
    private int mOutputX;
    private int mOutputY;
    boolean mSaving;
    private boolean mScale;
    boolean mWaitingToPick;
    private App mApp = null;
    private Bitmap.CompressFormat mOutputFormat = Bitmap.CompressFormat.JPEG;
    private Uri mSaveUri = null;
    private boolean mDoFaceDetection = true;
    private boolean mCircleCrop = false;
    private final Handler mHandler = new Handler();
    private boolean mScaleUp = true;
    private final BitmapManager.ThreadSet mDecodingThreads = new BitmapManager.ThreadSet();
    Runnable mRunFaceDetection = new FaceDetectionTask();

    public static void launchCropperOrFinish(final Context context, final MediaItem mediaItem) {
        Activity activity = (Activity) context;
        final Bundle extras = activity.getIntent().getExtras();
        String string = extras != null ? extras.getString("crop") : null;
        final String str = mediaItem.mContentUri;
        if (str == null) {
            return;
        }
        if (string != null) {
            Bundle bundle = new Bundle();
            if (string.equals("circle")) {
                bundle.putString("circleCrop", "true");
            }
            Intent intent = new Intent();
            intent.setData(Uri.parse(str));
            intent.setClass(context, CropImage.class);
            intent.putExtras(bundle);
            intent.putExtras(extras);
            activity.startActivityForResult(intent, 10);
            return;
        }
        if (str.startsWith("http://")) {
            Resources resources = context.getResources();

            String string2 = resources.getString(R.string.initializing);
            Resources resources2 = context.getResources();

            final ProgressDialog progressDialogShow = ProgressDialog.show(context, string2, resources2.getString(R.string.running_face_detection), true, false);
            if (str != null) {
                MediaScannerConnection mediaScannerConnection = new MediaScannerConnection(context, new MediaScannerConnection.MediaScannerConnectionClient() {
                    @Override
                    public void onMediaScannerConnected() {
                        MediaScannerConnection mediaScannerConnection2 = (MediaScannerConnection) CropImage.mConnectionMap.get(context);
                        if (mediaScannerConnection2 != null) {
                            try {
                                String strWriteHttpDataInDirectory = UriTexture.writeHttpDataInDirectory(context, str, LocalDataSource.DOWNLOAD_BUCKET_NAME);
                                if (strWriteHttpDataInDirectory != null) {
                                    mediaScannerConnection2.scanFile(strWriteHttpDataInDirectory, mediaItem.mMimeType);
                                } else {
                                    shutdown("");
                                }
                            } catch (Exception e) {
                                shutdown("");
                            }
                        }
                    }

                    @Override
                    public void onScanCompleted(String str2, Uri uri) {
                        shutdown(uri.toString());
                    }

                    public void shutdown(String str2) {
                        progressDialogShow.dismiss();
                        CropImage.performReturn(context, extras, str2.toString());
                        MediaScannerConnection mediaScannerConnection2 = (MediaScannerConnection) CropImage.mConnectionMap.get(context);
                        if (mediaScannerConnection2 != null) {
                            mediaScannerConnection2.disconnect();
                            CropImage.mConnectionMap.put(context, null);
                        }
                    }
                });
                mConnectionMap.put(context, mediaScannerConnection);
                mediaScannerConnection.connect();
                return;
            }
            return;
        }
        performReturn(context, extras, str);
    }

    public static void performReturn(Context context, Bundle extras, String source) {
        Uri sourceUri = Uri.parse(source);
        Intent result = new Intent((String) null, sourceUri);
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        result.setClipData(ClipData.newRawUri("Photo", sourceUri));
        boolean copiedToOutput = false;
        Uri output = extras == null ? null : (Uri) extras.getParcelable("output");
        if (output != null) {
            OutputStream stream = null;
            try {
                stream = context.getContentResolver().openOutputStream(output);
                if (stream != null) {
                    InputStream input = context.getContentResolver().openInputStream(sourceUri);
                    Utils.copyStream(input, stream);
                    Util.closeSilently(input);
                }
                ((Activity) context).setResult(Activity.RESULT_OK,
                        new Intent(output.toString()).putExtras(new Bundle()));
                copiedToOutput = true;
            } catch (Exception exception) {
                Log.e(TAG, "Cannot save to uri " + output.toString());
            } finally {
                Util.closeSilently(stream);
            }
        }
        if (!copiedToOutput && extras != null && extras.getBoolean("return-data")) {
            Bitmap bitmap = null;
            try {
                bitmap = UriTexture.createFromUri(context, source, 1024, 1024, 0L, null);
            } catch (IOException | URISyntaxException exception) {
                // The original app still returns the source URI if decoding fails.
            }
            if (bitmap != null) result.putExtra("data", bitmap);
        }
        if (!copiedToOutput) ((Activity) context).setResult(Activity.RESULT_OK, result);
        ((Activity) context).finish();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mApp = new App(CropImage.this);
        mContentResolver = getContentResolver();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.cropimage);

        mImageView = (CropImageView) findViewById(R.id.image);

        // CR: remove TODO's.
        // TODO: we may need to show this indicator for the main gallery
        // application
        // MenuHelper.showStorageToast(this);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (extras != null) {
            if (extras.getString("circleCrop") != null) {
                mCircleCrop = true;
                mAspectX = 1;
                mAspectY = 1;
            }
            mSaveUri = (Uri) extras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (mSaveUri != null) {
                String outputFormatString = extras.getString("outputFormat");
                if (outputFormatString != null) {
                    mOutputFormat = Bitmap.CompressFormat.valueOf(outputFormatString);
                }
            }
            mBitmap = (Bitmap) extras.getParcelable("data");
            mAspectX = extras.getInt("aspectX");
            mAspectY = extras.getInt("aspectY");
            mOutputX = extras.getInt("outputX");
            mOutputY = extras.getInt("outputY");
            mScale = extras.getBoolean("scale", true);
            mScaleUp = extras.getBoolean("scaleUpIfNeeded", true);
            mDoFaceDetection = extras.containsKey("noFaceDetection") ? !extras.getBoolean("noFaceDetection") : true;
        }

        if (mBitmap == null) {
            // Create a MediaItem representing the URI.
            Uri target = intent.getData();
            String targetScheme = target.getScheme();
            int rotation = 0;

            if (targetScheme.equals("content")) {
                mItem = LocalDataSource.createMediaItemFromUri(this, target, MediaItem.MEDIA_TYPE_IMAGE);
            }
            try {
                if (mItem != null) {
                    mBitmap = UriTexture.createFromUri(this, mItem.mContentUri, 1024, 1024, 0, null);
                    rotation = (int) mItem.mRotation;
                } else {
                    mBitmap = UriTexture.createFromUri(this, target.toString(), 1024, 1024, 0, null);
                    if (targetScheme.equals("file")) {
                        ExifInterface exif = new ExifInterface(target.getPath());
                        rotation = (int) Shared.exifOrientationToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL));
                    }
                }
            } catch (IOException e) {
            } catch (URISyntaxException e) {
            }

            if (mBitmap != null && rotation != 0f) {
                mBitmap = Util.rotate(mBitmap, rotation);
            }
        }

        if (mBitmap == null) {
            Log.e(TAG, "Cannot load bitmap, exiting.");
            finish();
            return;
        }

        // Make UI fullscreen.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        findViewById(R.id.discard).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSaveClicked();
            }
        });

        startFaceDetection();
    }

    private void startFaceDetection() {
        if (isFinishing()) {
            return;
        }

        mImageView.setImageBitmapResetBase(mBitmap, true);

        Util.startBackgroundJob(this, null, getResources().getString(R.string.running_face_detection), new Runnable() {
            public void run() {
                final CountDownLatch latch = new CountDownLatch(1);
                final Bitmap b = mBitmap;
                mHandler.post(new Runnable() {
                    public void run() {
                        if (b != mBitmap && b != null) {
                            mImageView.setImageBitmapResetBase(b, true);
                            mBitmap.recycle();
                            mBitmap = b;
                        }
                        if (mImageView.getScale() == 1.0f) {
                            mImageView.center(true, true);
                        }
                        latch.countDown();
                    }
                });
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                mRunFaceDetection.run();
            }
        }, mHandler);
    }

    private void onSaveClicked() {
        // CR: TODO!
        // TODO this code needs to change to use the decode/crop/encode single
        // step api so that we don't require that the whole (possibly large)
        // bitmap doesn't have to be read into memory
        if (mSaving)
            return;

        if (mCrop == null) {
            return;
        }

        mSaving = true;

        Rect r = mCrop.getCropRect();

        int width = r.width(); // CR: final == happy panda!
        int height = r.height();

        // If we are circle cropping, we want alpha channel, which is the
        // third param here.
        Bitmap croppedImage = Bitmap.createBitmap(width, height, mCircleCrop ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        {
            Canvas canvas = new Canvas(croppedImage);
            Rect dstRect = new Rect(0, 0, width, height);
            canvas.drawBitmap(mBitmap, r, dstRect, null);
        }

        if (mCircleCrop) {
            // OK, so what's all this about?
            // Bitmaps are inherently rectangular but we want to return
            // something that's basically a circle. So we fill in the
            // area around the circle with alpha. Note the all important
            // PortDuff.Mode.CLEARes.
            Canvas c = new Canvas(croppedImage);
            Path p = new Path();
            p.addCircle(width / 2F, height / 2F, width / 2F, Path.Direction.CW);
            c.clipPath(p, Region.Op.DIFFERENCE);
            c.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
        }

        // If the output is required to a specific size then scale or fill.
        if (mOutputX != 0 && mOutputY != 0) {
            if (mScale) {
                // Scale the image to the required dimensions.
                Bitmap old = croppedImage;
                croppedImage = Util.transform(new Matrix(), croppedImage, mOutputX, mOutputY, mScaleUp);
                if (old != croppedImage) {
                    old.recycle();
                }
            } else {

                /*
                 * Don't scale the image crop it to the size requested. Create
                 * an new image with the cropped image in the center and the
                 * extra space filled.
                 */

                // Don't scale the image but instead fill it so it's the
                // required dimension
                Bitmap b = Bitmap.createBitmap(mOutputX, mOutputY, Bitmap.Config.RGB_565);
                Canvas canvas = new Canvas(b);

                Rect srcRect = mCrop.getCropRect();
                Rect dstRect = new Rect(0, 0, mOutputX, mOutputY);

                int dx = (srcRect.width() - dstRect.width()) / 2;
                int dy = (srcRect.height() - dstRect.height()) / 2;

                // If the srcRect is too big, use the center part of it.
                srcRect.inset(Math.max(0, dx), Math.max(0, dy));

                // If the dstRect is too big, use the center part of it.
                dstRect.inset(Math.max(0, -dx), Math.max(0, -dy));

                // Draw the cropped bitmap in the center.
                canvas.drawBitmap(mBitmap, srcRect, dstRect, null);

                // Set the cropped bitmap as the new bitmap.
                croppedImage.recycle();
                croppedImage = b;
            }
        }

        // Return the cropped image directly or save it to the specified URI.
        Bundle myExtras = getIntent().getExtras();
        if (myExtras != null && (myExtras.getParcelable("data") != null || myExtras.getBoolean("return-data"))) {
            Bundle extras = new Bundle();
            extras.putParcelable("data", croppedImage);
            setResult(RESULT_OK, (new Intent()).setAction("inline-data").putExtras(extras));
            finish();
        } else {
            final Bitmap b = croppedImage;
            final Runnable save = new Runnable() {
                public void run() {
                    saveOutput(b);
                }
            };
            Util.startBackgroundJob(this, null, getResources().getString(R.string.saving_image), save, mHandler);
        }
    }

    public void saveOutput(Bitmap bitmap) {
        Uri output = this.mSaveUri;
        boolean created = false;
        try {
            if (output == null) {
                ContentValues values = new ContentValues();
                String name = "SimpleGallery-" + System.currentTimeMillis() + ".jpg";
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SimpleGallery");
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                } else {
                    File directory = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES), "SimpleGallery");
                    if (!directory.isDirectory() && !directory.mkdirs()) {
                        throw new IOException("Cannot create Pictures directory");
                    }
                    values.put(MediaStore.MediaColumns.DATA, new File(directory, name).getAbsolutePath());
                }
                output = this.mContentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                created = true;
            }
            if (output == null) throw new IOException("Cannot create image");
            try (OutputStream stream = this.mContentResolver.openOutputStream(output)) {
                if (stream == null || !bitmap.compress(this.mSaveUri == null
                        ? Bitmap.CompressFormat.JPEG : this.mOutputFormat, 95, stream)) {
                    throw new IOException("Cannot save crop");
                }
            }
            if (created && Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                this.mContentResolver.update(output, values, null, null);
            }
            Intent result = new Intent().setData(output).setAction(output.toString());
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            result.setClipData(ClipData.newRawUri("Cropped photo", output));
            setResult(RESULT_OK, result);
        } catch (Exception exception) {
            if (created && output != null) this.mContentResolver.delete(output, null, null);
            runOnUiThread(() -> Toast.makeText(this, "Unable to save cropped photo", Toast.LENGTH_LONG).show());
            setResult(RESULT_CANCELED);
        } finally {
            bitmap.recycle();
        }
        runOnUiThread(this::finish);
    }

     void showSaveFailure() {
        Toast.makeText(this, "Unable to save cropped photo", Toast.LENGTH_LONG).show();
    }

     void finishSaving() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.mApp.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        BitmapManager.instance().cancelThreadDecoding(this.mDecodingThreads);
        this.mApp.onPause();
    }

    @Override
    protected void onDestroy() {
        this.mApp.shutdown();
        super.onDestroy();
    }

    class FaceDetectionTask implements Runnable {
        Matrix mImageMatrix;
        int mNumFaces;
        float mScale = 1.0f;
        FaceDetector.Face[] mFaces = new FaceDetector.Face[3];

        FaceDetectionTask() {
        }

        public void handleFace(FaceDetector.Face face) {
            PointF pointF = new PointF();
            int iEyesDistance = ((int) (face.eyesDistance() * this.mScale)) * 2;
            face.getMidPoint(pointF);
            pointF.x *= this.mScale;
            pointF.y *= this.mScale;
            int i = (int) pointF.x;
            int i2 = (int) pointF.y;
            HighlightView highlightView = new HighlightView(CropImage.this.mImageView);
            boolean z = false;
            Rect rect = new Rect(0, 0, CropImage.this.mBitmap.getWidth(), CropImage.this.mBitmap.getHeight());
            float f = i;
            float f2 = i2;
            RectF rectF = new RectF(f, f2, f, f2);
            float f3 = -iEyesDistance;
            rectF.inset(f3, f3);
            if (rectF.left < 0.0f) {
                rectF.inset(-rectF.left, -rectF.left);
            }
            if (rectF.top < 0.0f) {
                rectF.inset(-rectF.top, -rectF.top);
            }
            if (rectF.right > rect.right) {
                rectF.inset(rectF.right - rect.right, rectF.right - rect.right);
            }
            if (rectF.bottom > rect.bottom) {
                rectF.inset(rectF.bottom - rect.bottom, rectF.bottom - rect.bottom);
            }
            Matrix matrix = this.mImageMatrix;
            boolean z2 = CropImage.this.mCircleCrop;
            if (CropImage.this.mAspectX != 0 && CropImage.this.mAspectY != 0) {
                z = true;
            }
            highlightView.setup(matrix, rect, rectF, z2, z);
            CropImage.this.mImageView.add(highlightView);
        }

        public void makeDefault() {
            int i;
            HighlightView highlightView = new HighlightView(CropImage.this.mImageView);
            int width = CropImage.this.mBitmap.getWidth();
            int height = CropImage.this.mBitmap.getHeight();
            boolean z = false;
            Rect rect = new Rect(0, 0, width, height);
            int iMin = (Math.min(width, height) * 4) / 5;
            if (CropImage.this.mAspectX != 0 && CropImage.this.mAspectY != 0) {
                if (CropImage.this.mAspectX > CropImage.this.mAspectY) {
                    i = (CropImage.this.mAspectY * iMin) / CropImage.this.mAspectX;
                } else {
                    i = iMin;
                    iMin = (CropImage.this.mAspectX * iMin) / CropImage.this.mAspectY;
                }
            } else {
                i = iMin;
            }
            int left = (width - iMin) / 2;
            int top = (height - i) / 2;
            RectF rectF = new RectF(left, top, left + iMin, top + i);
            Matrix matrix = this.mImageMatrix;
            boolean z2 = CropImage.this.mCircleCrop;
            if (CropImage.this.mAspectX != 0 && CropImage.this.mAspectY != 0) {
                z = true;
            }
            highlightView.setup(matrix, rect, rectF, z2, z);
            CropImage.this.mImageView.add(highlightView);
        }

        private Bitmap prepareBitmap() {
            if (CropImage.this.mBitmap != null) {
                if (CropImage.this.mBitmap.getWidth() > 256) {
                    this.mScale = 256.0f / CropImage.this.mBitmap.getWidth();
                }
                Matrix matrix = new Matrix();
                matrix.setScale(this.mScale, this.mScale);
                return Bitmap.createBitmap(CropImage.this.mBitmap, 0, 0, CropImage.this.mBitmap.getWidth(), CropImage.this.mBitmap.getHeight(), matrix, true);
            }
            return null;
        }

        @Override
        public void run() {
            this.mImageMatrix = CropImage.this.mImageView.getImageMatrix();
            Bitmap bitmapPrepareBitmap = prepareBitmap();
            this.mScale = 1.0f / this.mScale;
            if (bitmapPrepareBitmap != null && CropImage.this.mDoFaceDetection) {
                this.mNumFaces = new FaceDetector(bitmapPrepareBitmap.getWidth(), bitmapPrepareBitmap.getHeight(), this.mFaces.length).findFaces(bitmapPrepareBitmap, this.mFaces);
            }
            if (bitmapPrepareBitmap != null && bitmapPrepareBitmap != CropImage.this.mBitmap) {
                bitmapPrepareBitmap.recycle();
            }
            CropImage.this.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    CropImage.this.mWaitingToPick = FaceDetectionTask.this.mNumFaces > 1;
                    if (FaceDetectionTask.this.mNumFaces <= 0) {
                        FaceDetectionTask.this.makeDefault();
                    } else {
                        for (int i = 0; i < FaceDetectionTask.this.mNumFaces; i++) {
                            FaceDetectionTask.this.handleFace(FaceDetectionTask.this.mFaces[i]);
                        }
                    }
                    CropImage.this.mImageView.invalidate();
                    if (CropImage.this.mImageView.mHighlightViews.size() == 1) {
                        CropImage.this.mCrop = CropImage.this.mImageView.mHighlightViews.get(0);
                        CropImage.this.mCrop.setFocus(true);
                    }
                    if (FaceDetectionTask.this.mNumFaces > 1) {
                        CropImage cropImage = CropImage.this;

                        Toast.makeText(cropImage, R.string.multiface_crop_help, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
}
