package dev.antigravity.fluidengine.sample.bilanci;

import android.graphics.RenderEffect;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNativeRenderEffect;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * F7.28 · Conta i filtri ({@code RenderEffect}) che nascono: tutte le fabbriche statiche passano dal costruttore privato
 * col puntatore nativo. Un filtro e' immutabile, quindi un numero che cambia vuole un filtro nuovo: in moto stabile il
 * conto per fotogramma va dichiarato nel bilancio.
 */
@Implements(value = RenderEffect.class, minSdk = 31, isInAndroidSdk = false, callNativeMethodsByDefault = true)
public class CountingRenderEffect extends ShadowNativeRenderEffect {

  @RealObject private RenderEffect effect;

  @Implementation
  protected void __constructor__(long nativeRenderEffect) {
    GlassFactoryCounters.renderEffectCreated();
    Shadow.invokeConstructor(RenderEffect.class, effect, ClassParameter.from(long.class, nativeRenderEffect));
  }
}
