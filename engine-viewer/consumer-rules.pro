# engine-viewer compila contro Media3 senza portarlo (compileOnly): un'app che non lo include non chiama mai gli
# adattatori FluidMedia3*, e R8 non deve fermarsi sulle classi mancanti.
-dontwarn androidx.media3.**
