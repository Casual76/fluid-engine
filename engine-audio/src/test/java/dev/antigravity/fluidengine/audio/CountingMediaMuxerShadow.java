package dev.antigravity.fluidengine.audio;

import android.media.MediaMuxer;
import java.util.concurrent.atomic.AtomicInteger;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowMediaMuxer;

/**
 * Il contenitore di Robolectric, che conta i {@code release()} e sa fallire a {@code stop()} (un disco pieno sull'indice
 * finale). In Java: i due metodi nativi sono statici, e Kotlin non lascia nascondere uno statico del genitore.
 */
@Implements(MediaMuxer.class)
public class CountingMediaMuxerShadow extends ShadowMediaMuxer {
  public static final AtomicInteger releases = new AtomicInteger();
  public static volatile boolean failStop = false;

  @Implementation
  protected static void nativeRelease(long nativeObject) {
    releases.incrementAndGet();
  }

  @Implementation
  protected static void nativeStop(long nativeObject) {
    if (failStop) throw new IllegalStateException("Failed to stop the muxer");
    ShadowMediaMuxer.nativeStop(nativeObject);
  }
}
