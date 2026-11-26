package dev.antigravity.fluidengine.sample.bilanci;

import android.graphics.Bitmap;
import org.robolectric.annotation.ClassName;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNativeBitmap;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * F7.28 · Conta le bitmap che nascono. Ogni bitmap, creata da Java ({@code createBitmap}) o dal nativo (decodifica,
 * copia), passa dal costruttore a otto argomenti: lo si conta e poi lo si esegue com'e'.
 */
@Implements(value = Bitmap.class, minSdk = 26, isInAndroidSdk = false, callNativeMethodsByDefault = true)
public class CountingBitmap extends ShadowNativeBitmap {

  @RealObject private Bitmap bitmap;

  @Implementation(minSdk = 26)
  protected void __constructor__(
      long nativeBitmap,
      int width,
      int height,
      int density,
      boolean requestPremultiplied,
      byte[] ninePatchChunk,
      @ClassName("android.graphics.NinePatch$InsetStruct") Object ninePatchInsets,
      boolean fromMalloc) {
    GlassFactoryCounters.bitmapCreated(width, height);
    Class<?> insets;
    try {
      insets = Class.forName("android.graphics.NinePatch$InsetStruct", false, Bitmap.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
    Shadow.invokeConstructor(
        Bitmap.class,
        bitmap,
        ClassParameter.from(long.class, nativeBitmap),
        ClassParameter.from(int.class, width),
        ClassParameter.from(int.class, height),
        ClassParameter.from(int.class, density),
        ClassParameter.from(boolean.class, requestPremultiplied),
        ClassParameter.from(byte[].class, ninePatchChunk),
        ClassParameter.from(insets, ninePatchInsets),
        ClassParameter.from(boolean.class, fromMalloc));
  }
}
