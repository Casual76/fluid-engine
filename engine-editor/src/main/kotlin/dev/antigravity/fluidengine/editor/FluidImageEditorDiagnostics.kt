package dev.antigravity.fluidengine.editor

/**
 * Gli interruttori di misura dell'editor (F7.24): **non sono API per le app**, come `FluidRulerDialDiagnostics`. Servono
 * alla bench della galleria per confrontare sul tablet due modi di disegnare, nella stessa finestra.
 */
object FluidImageEditorDiagnostics {
  /**
   * Vero: mentre il dito muove una regolazione, la foto dello shader si disegna a metà risoluzione e torna piena al
   * rilascio. Falso (di serie): sempre piena, com'era prima di F7.24. Resta spenta finché la misura del tablet non mostra
   * un guadagno, perché la foto meno nitida sotto il dito si vede (indicazioni di F7: niente perdite visibili).
   */
  @Volatile
  var lightPreview: Boolean = false
}
