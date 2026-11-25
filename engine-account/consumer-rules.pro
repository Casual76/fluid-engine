# engine-account (F10.4) · Bouncy Castle, come in Codex (app/proguard-rules.pro), dove gira gia' sui telefoni veri.
# L'engine usa solo l'API leggera, ma alcune classi si caricano per nome (i motori nativi, il registro dei servizi):
# tenerle intere costa qualche centinaio di KB e toglie il rischio di una cifratura che si rompe solo in release.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
