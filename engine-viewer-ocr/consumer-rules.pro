# F8.17 · engine-viewer-ocr: JavaCPP lega i metodi nativi e legge le annotazioni delle classi dei collegamenti per
# riflessione. Si tengono il caricatore di JavaCPP (le classi del suo pacchetto, le annotazioni e gli indicizzatori) e
# i collegamenti di Tesseract e Leptonica; gli strumenti di build di JavaCPP (javacpp.tools, il plugin di Maven) non
# servono sul telefono.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class org.bytedeco.javacpp.* { *; }
-keep class org.bytedeco.javacpp.annotation.** { *; }
-keep class org.bytedeco.javacpp.indexer.** { *; }
-keep class org.bytedeco.tesseract.** { *; }
-keep class org.bytedeco.leptonica.** { *; }
# Le classi che JavaCPP nomina solo sulla JVM del computer (gli strumenti, JMX, Maven, SLF4J, Unsafe).
-dontwarn org.bytedeco.javacpp.tools.**
-dontwarn org.apache.maven.**
-dontwarn org.slf4j.**
-dontwarn javax.management.**
-dontwarn sun.misc.Unsafe
-dontwarn java.awt.**
-dontwarn javax.imageio.**
# Le annotazioni OSGi dei pacchetti di JavaCPP (solo metadati per la JVM del computer).
-dontwarn org.osgi.**
# L'estensione dei visori, trovata con ServiceLoader.
-keep class dev.antigravity.fluidengine.viewer.ocr.FluidLiveTextExtension { <init>(); }
