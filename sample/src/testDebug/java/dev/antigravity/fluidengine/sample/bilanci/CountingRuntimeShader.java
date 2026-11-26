package dev.antigravity.fluidengine.sample.bilanci;

import android.graphics.RuntimeShader;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowNativeRuntimeShader;

/**
 * F7.28 · Conta i programmi AGSL che nascono ({@code new RuntimeShader(sorgente)}): il vetro li compila una volta e li
 * condivide, quindi uno nuovo per fotogramma e' un difetto. Il costruttore di {@link ShadowNativeRuntimeShader} (che
 * sostituisce il programma delle increspature) resta quello di sempre.
 */
@Implements(value = RuntimeShader.class, minSdk = 30, isInAndroidSdk = false, callNativeMethodsByDefault = true)
public class CountingRuntimeShader extends ShadowNativeRuntimeShader {

  @Override
  @Implementation
  protected void __constructor__(String sksl) {
    GlassFactoryCounters.runtimeShaderCreated();
    super.__constructor__(sksl);
  }
}
