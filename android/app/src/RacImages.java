package com.tuopzf.tuyin;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 图片工具：解码/缩放/JPEG 阶梯压缩/相册保存（对齐 src/browser.js 语义）。 */
public final class RacImages {
    /** 长边上限（对齐 JS capLongEdge）。 */
    public static final int MAX_LONG = 4096;
    /** 秘密图最长边上限（对齐 JS secretMax 默认）。 */
    public static final int SECRET_MAX = 1280;

    private RacImages() { }

    /* ---------------- 解码 ---------------- */

    /** 解码 Uri 为 ImageData，可选按目标长边缩放；应用 EXIF 旋转。 */
    public static RacCore.ImageData decodeUri(Context ctx, Uri uri, int targetLong) throws IOException {
        Bitmap bitmap = decodeBitmap(ctx, uri);
        if (bitmap == null) throw new IOException("无法解码图片");
        Bitmap oriented = applyExifRotation(ctx, uri, bitmap);
        return capLongEdge(oriented, targetLong > 0 ? targetLong : MAX_LONG);
    }

    public static Bitmap decodeBitmap(Context ctx, Uri uri) throws IOException {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        }
    }

    private static Bitmap applyExifRotation(Context ctx, Uri uri, Bitmap src) {
        try {
            int rotation = 0;
            try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    ExifInterface exif = new ExifInterface(is);
                    int ori = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    switch (ori) {
                        case ExifInterface.ORIENTATION_ROTATE_90: rotation = 90; break;
                        case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                        case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
                        default: rotation = 0;
                    }
                }
            }
            if (rotation == 0) return src;
            Matrix m = new Matrix();
            m.postRotate(rotation);
            return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        } catch (Exception e) {
            return src;
        }
    }

    /** 长边不超过 limit 的等比缩放（对齐 JS capLongEdge）。 */
    public static RacCore.ImageData capLongEdge(Bitmap src, int limit) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (Math.max(w, h) > limit) {
            double scale = limit / (double) Math.max(w, h);
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            src = Bitmap.createScaledBitmap(src, nw, nh, true);
        }
        return bitmapToImageData(src);
    }

    public static RacCore.ImageData bitmapToImageData(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < w * h; i++) {
            int p = pixels[i];
            rgba[i * 4] = (byte) ((p >> 16) & 0xff);
            rgba[i * 4 + 1] = (byte) ((p >> 8) & 0xff);
            rgba[i * 4 + 2] = (byte) (p & 0xff);
            rgba[i * 4 + 3] = (byte) 255;
        }
        return new RacCore.ImageData(rgba, w, h);
    }

    public static Bitmap imageDataToBitmap(RacCore.ImageData img) {
        int w = img.width;
        int h = img.height;
        int[] pixels = new int[w * h];
        byte[] rgba = img.rgba;
        for (int i = 0; i < w * h; i++) {
            int r = rgba[i * 4] & 0xff;
            int g = rgba[i * 4 + 1] & 0xff;
            int b = rgba[i * 4 + 2] & 0xff;
            pixels[i] = 0xff000000 | (r << 16) | (g << 8) | b;
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
    }

    /** 高质量重采样（对齐 JS resizeImageData）。 */
    public static RacCore.ImageData resizeImageData(RacCore.ImageData img, int width, int height) {
        Bitmap src = imageDataToBitmap(img);
        Bitmap scaled = Bitmap.createScaledBitmap(src, width, height, true);
        return bitmapToImageData(scaled);
    }

    /* ---------------- 秘密图 JPEG 阶梯压缩（对齐 JS fitSecretJpeg） ---------------- */

    public static byte[] fitSecretJpeg(RacCore.ImageData source, int maxBytes, int maxLong) {
        int cap = Math.max(32, Math.min(maxLong, Math.max(source.width, source.height)));
        int[] baseDims = { 640, 560, 480, 400, 320, 256, 192, 128, 96, 64, 48, 32 };
        int[] dims = new int[baseDims.length + 1];
        int n = 0;
        boolean hasCap = false;
        for (int d : baseDims) {
            if (d <= cap) {
                dims[n++] = d;
                if (d == cap) hasCap = true;
            }
        }
        if (cap > 640 && !hasCap) {
            System.arraycopy(dims, 0, dims, 1, n);
            dims[0] = cap;
            n++;
        }
        if (n == 0) dims[n++] = cap;

        Bitmap base = imageDataToBitmap(source);
        int[] qualities = { 80, 65, 50, 40, 32, 25 };

        for (int i = 0; i < n; i++) {
            int maxDim = dims[i];
            int width = source.width;
            int height = source.height;
            if (Math.max(width, height) > maxDim) {
                double scale = maxDim / (double) Math.max(width, height);
                width = Math.max(1, (int) Math.round(width * scale));
                height = Math.max(1, (int) Math.round(height * scale));
            }
            // 白底画布（对齐 JS fillStyle '#ffffff'，避免透明变黑）
            Bitmap canvas = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(canvas);
            c.drawColor(0xFFFFFFFF);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(base, null, new android.graphics.Rect(0, 0, width, height), paint);

            for (int quality : qualities) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                boolean ok = canvas.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                if (ok && baos.size() <= maxBytes) {
                    return baos.toByteArray();
                }
            }
            canvas.recycle();
        }
        return null;
    }

    /* ---------------- 保存到相册 ---------------- */

    public static boolean saveBitmapToGallery(Context ctx, Bitmap bitmap, String name, String mime, int quality) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, mime);
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/图隐");
                Uri uri = ctx.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return false;
                try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                    if (os == null) return false;
                    return compressToStream(bitmap, mime, quality, os);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "图隐");
                if (!dir.exists() && !dir.mkdirs()) return false;
                File file = new File(dir, name);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    return compressToStream(bitmap, mime, quality, fos);
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean compressToStream(Bitmap bitmap, String mime, int quality, OutputStream os) {
        Bitmap.CompressFormat format = "image/jpeg".equals(mime)
                ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
        return bitmap.compress(format, quality, os);
    }
}
