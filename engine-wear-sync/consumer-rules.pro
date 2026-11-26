# engine-wear-sync compila contro il Data Layer di Play services senza portarlo (compileOnly): un'app che non lo include
# non usa mai FluidWearDataLayer ne' FluidWearListenerService, e R8 non deve fermarsi sulle classi mancanti.
-dontwarn com.google.android.gms.wearable.**
-dontwarn com.google.android.gms.tasks.**
