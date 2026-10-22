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

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;
import com.zoeykl.simplegallery.R;

public class MovieViewControl implements MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    private static final String CMDNAME = "command";
    private static final String CMDPAUSE = "pause";
    private static final int HALF_MINUTE = 30000;
    private static final String SERVICECMD = "com.android.music.musicservicecommand";
    private static final String TAG = "MovieViewControl";
    private static final int TWO_MINUTES = 120000;
    private final ContentResolver mContentResolver;
    Handler mHandler = new Handler();
    Runnable mPlayingChecker = new Runnable() {
        @Override
        public void run() {
            if (MovieViewControl.this.mVideoView.isPlaying()) {
                MovieViewControl.this.mProgressView.setVisibility(View.GONE);
            } else {
                MovieViewControl.this.mHandler.postDelayed(MovieViewControl.this.mPlayingChecker, 250L);
            }
        }
    };
    private final View mProgressView;
    private final Uri mUri;
    private final VideoView mVideoView;

    public static String formatDuration(Context context, int i) {
        int i2 = i / 1000;
        int i3 = i2 / 3600;
        int i4 = i3 * 3600;
        int i5 = (i2 - i4) / 60;
        int i6 = i2 - (i4 + (i5 * 60));
        if (i3 == 0) {

            return String.format(context.getString(R.string.details_ms), Integer.valueOf(i5), Integer.valueOf(i6));
        }

        return String.format(context.getString(R.string.details_hms), Integer.valueOf(i3), Integer.valueOf(i5), Integer.valueOf(i6));
    }

    public MovieViewControl(View view, Context context, Uri uri) {
        this.mContentResolver = context.getContentResolver();

        this.mVideoView = (VideoView) view.findViewById(R.id.surface_view);

        this.mProgressView = view.findViewById(R.id.progress_indicator);
        this.mUri = uri;
        String scheme = this.mUri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "rtsp".equalsIgnoreCase(scheme)) {
            this.mHandler.postDelayed(this.mPlayingChecker, 250L);
        } else {
            this.mProgressView.setVisibility(View.GONE);
        }
        this.mVideoView.setOnErrorListener(this);
        this.mVideoView.setOnCompletionListener(this);
        this.mVideoView.setVideoURI(this.mUri);
        this.mVideoView.setMediaController(new MediaController(context));
        this.mVideoView.requestFocus();
        Intent intent = new Intent(SERVICECMD);
        intent.putExtra(CMDNAME, CMDPAUSE);
        context.sendBroadcast(intent);
        final Integer bookmark = getBookmark();
        if (bookmark != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            builder.setTitle(R.string.resume_playing_title);

            builder.setMessage(String.format(context.getString(R.string.resume_playing_message), formatDuration(context, bookmark.intValue())));
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    MovieViewControl.this.onCompletion();
                }
            });

            builder.setPositiveButton(R.string.resume_playing_resume, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    MovieViewControl.this.mVideoView.seekTo(bookmark.intValue());
                    MovieViewControl.this.mVideoView.start();
                }
            });

            builder.setNegativeButton(R.string.resume_playing_restart, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    MovieViewControl.this.mVideoView.start();
                }
            });
            builder.show();
            return;
        }
        this.mVideoView.start();
    }

    private static boolean uriSupportsBookmarks(Uri uri) {
        return "content".equalsIgnoreCase(uri.getScheme()) && "media".equalsIgnoreCase(uri.getAuthority());
    }

    private Integer getBookmark() {
        if (!uriSupportsBookmarks(this.mUri)) {
            return null;
        }
        try {
            Cursor cursorQuery = this.mContentResolver.query(this.mUri, new String[]{"duration", "bookmark"}, null, null, null);
            if (cursorQuery != null) {
                try {
                    if (cursorQuery.moveToFirst()) {
                        int cursorInteger = getCursorInteger(cursorQuery, 0);
                        int cursorInteger2 = getCursorInteger(cursorQuery, 1);
                        if (cursorInteger2 >= HALF_MINUTE && cursorInteger >= TWO_MINUTES && cursorInteger2 <= cursorInteger - HALF_MINUTE) {
                            return Integer.valueOf(cursorInteger2);
                        }
                        return null;
                    }
                    cursorQuery.close();
                } finally {
                    cursorQuery.close();
                }
            }
        } catch (SQLiteException e) {
        }
        return null;
    }

    private static int getCursorInteger(Cursor cursor, int i) {
        try {
            return cursor.getInt(i);
        } catch (SQLiteException e) {
            return 0;
        } catch (NumberFormatException e2) {
            return 0;
        }
    }

    private void setBookmark(int i, int i2) {
        if (!uriSupportsBookmarks(this.mUri)) {
            return;
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put("bookmark", Integer.toString(i));
        contentValues.put("duration", Integer.toString(i2));
        try {
            this.mContentResolver.update(this.mUri, contentValues, null, null);
        } catch (SQLiteException e) {
        } catch (SecurityException e2) {
        } catch (UnsupportedOperationException e3) {
        }
    }

    public void onPause() {
        this.mHandler.removeCallbacksAndMessages(null);
        setBookmark(this.mVideoView.getCurrentPosition(), this.mVideoView.getDuration());
        this.mVideoView.suspend();
    }

    public void onResume() {
        this.mVideoView.resume();
    }

    public void onDestroy() {
        this.mVideoView.stopPlayback();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i2) {
        this.mHandler.removeCallbacksAndMessages(null);
        this.mProgressView.setVisibility(View.GONE);
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        onCompletion();
    }

    public void onCompletion() {
    }
}
