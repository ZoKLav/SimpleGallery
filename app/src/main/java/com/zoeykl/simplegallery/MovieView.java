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
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import com.zoeykl.simplegallery.R;
import com.zoeykl.simplegallery.legacy.app.App;

public class MovieView extends Activity {
    private static final String TAG = "MovieView";
    private App mApp = null;
    private MovieViewControl mControl;
    private boolean mFinishOnCompletion;

    @Override
    public void onCreate(Bundle bundle) {
        int intExtra;
        super.onCreate(bundle);
        this.mApp = new App(this);

        setContentView(R.layout.movie_view);

        View viewFindViewById = findViewById(R.id.root);
        Intent intent = getIntent();
        this.mControl = new MovieViewControl(viewFindViewById, this, intent.getData()) {
            @Override
            public void onCompletion() {
                if (MovieView.this.mFinishOnCompletion) {
                    MovieView.this.finish();
                }
            }
        };
        if (intent.hasExtra("android.intent.extra.screenOrientation") && (intExtra = intent.getIntExtra("android.intent.extra.screenOrientation", -1)) != getRequestedOrientation()) {
            setRequestedOrientation(intExtra);
        }
        this.mFinishOnCompletion = intent.getBooleanExtra("android.intent.extra.finishOnCompletion", true);
        Window window = getWindow();
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.buttonBrightness = 0.0f;
        window.setAttributes(attributes);
    }

    @Override
    public void onPause() {
        this.mControl.onPause();
        super.onPause();
        this.mApp.onPause();
    }

    @Override
    public void onResume() {
        this.mControl.onResume();
        super.onResume();
        this.mApp.onResume();
    }

    @Override
    public void onDestroy() {
        this.mControl.onDestroy();
        this.mApp.shutdown();
        super.onDestroy();
    }
}
