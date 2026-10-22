package com.zoeykl.simplegallery;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** MIME resolution shared by MediaStore, document providers and incoming intents. */
public final class MediaFormats {
    private MediaFormats() {}

    public static String normalize(String mime) {
        if (mime == null) return null;
        String type = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "image/jpg": case "image/pjpeg": return "image/jpeg";
            case "image/x-webp": return "image/webp";
            case "image/x-png": return "image/png";
            case "video/x-webm": case "application/webm": return "video/webm";
            case "video/matroska": case "application/x-matroska": return "video/x-matroska";
            case "application/mp4": return "video/mp4";
            default: return type.isEmpty() ? null : type;
        }
    }

    public static boolean isSpecificMediaType(String mime) {
        String type = normalize(mime);
        return type != null && (type.startsWith("image/") || type.startsWith("video/"))
                && type.indexOf('*') < 0;
    }

    public static String resolve(String declared, String name, byte[] header) {
        String type = normalize(declared);
        if (isSpecificMediaType(type)) return type;
        String detected = sniff(header);
        if (detected != null) return detected;
        return fromName(name);
    }

    public static String fromName(String name) {
        if (name == null) return null;
        String path = name.split("[?#]", 2)[0].toLowerCase(Locale.ROOT);
        int dot = path.lastIndexOf('.');
        if (dot < 0) return null;
        switch (path.substring(dot + 1)) {
            case "jpg": case "jpeg": case "jpe": return "image/jpeg";
            case "png": return "image/png";
            case "webp": return "image/webp";
            case "gif": return "image/gif";
            case "avif": case "avifs": return "image/avif";
            case "heic": case "heics": return "image/heic";
            case "heif": case "heifs": return "image/heif";
            case "bmp": return "image/bmp";
            case "dng": return "image/x-adobe-dng";
            case "webm": return "video/webm";
            case "mkv": return "video/x-matroska";
            case "mp4": case "m4v": return "video/mp4";
            case "mov": return "video/quicktime";
            case "3gp": case "3gpp": return "video/3gpp";
            case "3g2": return "video/3gpp2";
            case "ts": case "m2ts": return "video/mp2t";
            default: return null;
        }
    }

    public static String sniff(byte[] bytes) {
        if (bytes == null) return null;
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) return "image/jpeg";
        if (bytes.length >= 8 && (bytes[0] & 255) == 137 && ascii(bytes, 1, "PNG\r\n\u001a\n")) return "image/png";
        if (ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a")) return "image/gif";
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "image/webp";
        if (bytes.length >= 4 && (bytes[0] & 255) == 26 && (bytes[1] & 255) == 69
                && (bytes[2] & 255) == 223 && (bytes[3] & 255) == 163) {
            String ebml = new String(bytes, StandardCharsets.ISO_8859_1);
            if (ebml.contains("webm")) return "video/webm";
            if (ebml.contains("matroska")) return "video/x-matroska";
        }
        if (bytes.length >= 16 && ascii(bytes, 4, "ftyp")) {
            long size = ((long)(bytes[0] & 255) << 24) | ((long)(bytes[1] & 255) << 16)
                    | ((long)(bytes[2] & 255) << 8) | (bytes[3] & 255);
            if (size < 16) return null;
            int end = (int)Math.min(size, bytes.length);
            String fallback = null;
            for (int i = 8; i + 4 <= end; i += 4) {
                if (i == 12) continue; // Minor version is not a compatible brand.
                String brand = new String(bytes, i, 4, StandardCharsets.US_ASCII);
                if (brand.equals("avif") || brand.equals("avis")) return "image/avif";
                if (brand.equals("heic") || brand.equals("heix") || brand.equals("hevc") || brand.equals("hevx")) return "image/heic";
                if (brand.equals("mif1") || brand.equals("msf1")) fallback = "image/heif";
                else if (brand.equals("qt  ")) fallback = "video/quicktime";
                else if (fallback == null && (brand.startsWith("iso") || brand.equals("mp41")
                        || brand.equals("mp42") || brand.equals("M4V ") || brand.equals("avc1"))) fallback = "video/mp4";
            }
            return fallback;
        }
        return null;
    }

    private static boolean ascii(byte[] bytes, int offset, String text) {
        if (offset < 0 || offset + text.length() > bytes.length) return false;
        for (int i = 0; i < text.length(); i++) if ((bytes[offset + i] & 255) != text.charAt(i)) return false;
        return true;
    }

    public static boolean isVideo(String mime) {
        String type = normalize(mime);
        return type != null && type.startsWith("video/");
    }

    public static int[] fitSize(int width, int height, int maxWidth, int maxHeight) {
        if (width <= 0 || height <= 0 || maxWidth <= 0 || maxHeight <= 0) throw new IllegalArgumentException("Invalid image dimensions");
        double scale = Math.min(1.0, Math.min((double)maxWidth / width, (double)maxHeight / height));
        return new int[]{Math.max(1, (int)(width * scale)), Math.max(1, (int)(height * scale))};
    }

    public static String commonType(String left, String right) {
        left = normalize(left); right = normalize(right);
        if (left == null || right == null) return "*/*";
        if (left.equals(right)) return left;
        if (left.startsWith("image/") && right.startsWith("image/")) return "image/*";
        if (left.startsWith("video/") && right.startsWith("video/")) return "video/*";
        return "*/*";
    }
}
