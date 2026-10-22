package com.zoeykl.simplegallery;

/** Source-pixel crop shared by preview and wallpaper output. Android-independent for tests. */
final class WallpaperCrop {
    private WallpaperCrop() {}

    static float[] bounds(int width, int height, float aspect, float zoom, float centerX, float centerY) {
        if (width <= 0 || height <= 0 || !Float.isFinite(aspect) || aspect <= 0
                || !Float.isFinite(zoom) || !Float.isFinite(centerX) || !Float.isFinite(centerY)) {
            throw new IllegalArgumentException("Invalid crop dimensions");
        }
        zoom = Math.max(1, Math.min(4, zoom));
        float w = Math.min(width, height * aspect) / zoom;
        float h = w / aspect;
        float x = Math.max(w / 2, Math.min(width - w / 2, centerX * width));
        float y = Math.max(h / 2, Math.min(height - h / 2, centerY * height));
        return new float[]{x - w / 2, y - h / 2, x + w / 2, y + h / 2};
    }
}
