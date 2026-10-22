# SimpleGallery

SimpleGallery is a modern rework of the Android Eclair/Froyo **Gallery3D**, with fixes and support for newer Android versions and media formats. It keeps the old 3D gallery because some of us actually liked having a gallery instead of a fucking service platform.

It opens your pictures, plays your videos, and lets you manage your files. If you need an account, a subscription, and a quarterly product announcement before you can look at a JPEG, you’re in the wrong repository.

## Your photos aren't a business opportunity

Google Photos can keep its cloud backup and Google One storage plans. SimpleGallery doesn't need you enrolled in an ecosystem to show you a picture already sitting on your phone.

Your private photo collection shouldn't be an invitation to upload everything, analyze everything, and sell you somewhere to put the results. Showing photos is the point here, and the cloud can fuck off.

## Features

- **Classic Gallery3D interface:** the old 3D wall, albums, navigation, animations, and gestures.
- **Local photo and video browsing:** no account or internet connection required to view files stored on your device.
- **Modern image formats:** WebP, GIF, HEIC/HEIF, and AVIF recognition, alongside familiar formats such as JPEG and PNG.
- **Animated images:** animated WebP and GIF playback in the original-image viewer on Android 9 and newer.
- **Modern video handling:** WebM and MKV recognition, video thumbnails, and playback through Android’s media support.
- **Open file:** choose individual images and videos through Android’s document picker.
- **Open original:** view the actual file beyond the gallery’s static thumbnail.
- **Sharing:** share original files with appropriate media types and read permissions.
- **Photo tools:** crop, rotate, delete, and view slideshows.
- **In-app wallpaper picker:** preview your photo, drag and zoom the crop, then apply it to your home screen, lock screen, or both.
- **Modern Android permissions:** support for current photo-access controls and system consent when modifying media.

## Read this before complaining

Recognizing a file format doesn't magically install a decoder in your phone. HEIF, AVIF, and video playback depend on what your Android version and device actually support.

The 3D wall uses still previews, and animated images become still wallpapers. This is a gallery with a wallpaper picker, so don't feed it a video and demand a live wallpaper engine.

## Changelog

### 1.0.4 — Wallpaper setting stays in the app

- Replaced the external **Set As** chooser with an in-app wallpaper picker.
- Added **Home**, **Lock**, and **Both** destinations.
- Added a crop preview with dragging, pinch-to-zoom, zoom buttons, and reset.
- Added wallpaper access from the original-image viewer.
- Preserved picker state across rotation and prevented duplicate application during rotation.
- Added readable errors when wallpaper changes fail or are restricted.

### 1.0.3 — Files from this century

- Added **Open file** and **Open original**.
- Expanded recognition for WebP, GIF, HEIF/HEIC, AVIF, WebM, MKV, and other common media formats.
- Added animated WebP and GIF viewing on supported Android versions.
- Improved handling of missing or incorrect file types.
- Improved video thumbnails and sharing across mixed media formats.

### 1.0.2 — Recovered and repaired

- Reconstructed the app using the original Gallery3D source and recovered modern Android integration.
- Restored the legacy renderer, navigation, gestures, animations, and menus.
- Repaired decompilation damage and Android compatibility issues.
- Established **SimpleGallery** under `com.zoeykl.simplegallery`.
