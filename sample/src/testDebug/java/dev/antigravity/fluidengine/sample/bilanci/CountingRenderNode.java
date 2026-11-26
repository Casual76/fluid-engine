package dev.antigravity.fluidengine.sample.bilanci;

import static org.robolectric.util.reflector.Reflector.reflector;

import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import org.robolectric.annotation.ClassName;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNativeRenderNode;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;

/**
 * F7.28 · Conta i RenderNode che nascono e ogni registrazione di un RenderNode, senza cambiare niente: il costruttore
 * vero e la registrazione vera girano come sempre (sull'SDK 35 i metodi nativi del grafico sono quelli veri, e le
 * implementazioni di {@link ShadowNativeRenderNode} restano attive dove valgono).
 *
 * <p>Un layer di Compose ({@code GraphicsLayer}) e' un RenderNode creato con il costruttore pubblico: un layer nuovo per
 * fotogramma si vede qui. Una registrazione e' un {@code beginRecording}: lo stesso nodo registrato due volte nello
 * stesso fotogramma e' lavoro buttato (la guardia «stesso campo, stessa revisione»).
 */
@Implements(value = RenderNode.class, minSdk = 29, callNativeMethodsByDefault = true)
public class CountingRenderNode extends ShadowNativeRenderNode {

  @RealObject private RenderNode node;

  @Implementation
  protected void __constructor__(String name) {
    GlassFactoryCounters.renderNodeCreated(node, name);
    Shadow.invokeConstructor(RenderNode.class, node, ClassParameter.from(String.class, name));
  }

  /** Il costruttore delle viste e di {@code RenderNode.create}: non conta (le viste nascono prima della misura), dà il nome. */
  @Implementation
  protected void __constructor__(
      String name, @ClassName("android.graphics.RenderNode$AnimationHost") Object animationHost) {
    GlassFactoryCounters.nameNode(node, name);
    Class<?> host;
    try {
      host = Class.forName("android.graphics.RenderNode$AnimationHost", false, RenderNode.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
    Shadow.invokeConstructor(
        RenderNode.class, node, ClassParameter.from(String.class, name), ClassParameter.from(host, animationHost));
  }

  @Implementation
  protected RecordingCanvas beginRecording(int width, int height) {
    GlassFactoryCounters.recorded(node);
    return reflector(RenderNodeReflector.class, node).beginRecording(width, height);
  }

  @Implementation
  protected void endRecording() {
    reflector(RenderNodeReflector.class, node).endRecording();
    GlassFactoryCounters.recordingEnded(node);
  }

  @ForType(RenderNode.class)
  interface RenderNodeReflector {
    @Direct
    RecordingCanvas beginRecording(int width, int height);

    @Direct
    void endRecording();
  }
}
