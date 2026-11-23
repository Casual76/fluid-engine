# Optional implementation selected by the 3D viewer through ServiceLoader.
-keepnames interface dev.antigravity.fluidengine.viewer.scene.FluidSceneViewerExtension
-keep,allowoptimization class dev.antigravity.fluidengine.viewer.ar.FluidArSceneExtension {
    public <init>();
}
