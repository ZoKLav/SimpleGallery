package com.zoeykl.simplegallery.legacy.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Toast;
import com.zoeykl.simplegallery.ReverseGeocoder;
import java.util.HashMap;
import java.util.TimeZone;

public class App {
    private final Context mContext;
    private final Handler mHandler;
    private final HandlerThread mHandlerThread = new HandlerThread("AppHandlerThread");
    private boolean mPaused = false;
    private ReverseGeocoder mReverseGeocoder;
    private static final HashMap<Context, App> mMap = new HashMap<>();
    public static final TimeZone CURRENT_TIME_ZONE = TimeZone.getDefault();
    public static float PIXEL_DENSITY = 0.0f;

    public static App get(Context context) {
        return mMap.get(context);
    }

    public App(Context context) {
        this.mReverseGeocoder = null;
        mMap.put(context, this);
        this.mContext = context;
        ((Activity) context).getWindow().getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public final WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                return App.applyWindowInsets(view, windowInsets);
            }
        });
        if (PIXEL_DENSITY == 0.0f) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((Activity) this.mContext).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            PIXEL_DENSITY = displayMetrics.density;
        }
        this.mHandlerThread.start();
        this.mHandler = new Handler(this.mHandlerThread.getLooper());
        this.mReverseGeocoder = new ReverseGeocoder(this.mContext);
    }

    static  WindowInsets applyWindowInsets(View view, WindowInsets windowInsets) {
        view.setPadding(windowInsets.getSystemWindowInsetLeft(), windowInsets.getSystemWindowInsetTop(), windowInsets.getSystemWindowInsetRight(), windowInsets.getSystemWindowInsetBottom());
        return windowInsets;
    }

    public void shutdown() {
        this.mReverseGeocoder.shutdown();
        mMap.remove(this.mContext);
        this.mHandlerThread.quitSafely();
    }

    public Context getContext() {
        return this.mContext;
    }

    public Handler getHandler() {
        while (this.mHandler == null) {
        }
        return this.mHandler;
    }

    public ReverseGeocoder getReverseGeocoder() {
        return this.mReverseGeocoder;
    }

    public boolean isPaused() {
        return this.mPaused;
    }

    public void onResume() {
        this.mPaused = false;
    }

    public void onPause() {
        this.mReverseGeocoder.flushCache();
        this.mPaused = true;
    }

    public void showToast(final String str, final int i) {
        this.mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(App.this.mContext, str, i).show();
            }
        });
    }
}
