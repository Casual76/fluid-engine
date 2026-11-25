# engine-account (F10.4) · Bouncy Castle, come in Codex (app/proguard-rules.pro), dove gira gia' sui telefoni veri.
# L'engine usa solo l'API leggera, ma alcune classi si caricano per nome (i motori nativi, il registro dei servizi):
# tenerle intere costa qualche centinaio di KB e toglie il rischio di una cifratura che si rompe solo in release.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# F10.8 · L'aspetto comune: `EngineSettingsStore` (engine-storage) trova la sorgente con ServiceLoader, dal file
# META-INF/services di questo modulo. R8 riscrive `ServiceLoader.load(X::class.java, X::class.java.classLoader)`, ma
# senza ottimizzazioni il file resta letto per nome: interfaccia e classe tengono il nome e il costruttore.
-keep interface dev.antigravity.fluidengine.storage.EngineSharedAppearance
-keep class dev.antigravity.fluidengine.account.PampaSharedAppearance { public <init>(); }
