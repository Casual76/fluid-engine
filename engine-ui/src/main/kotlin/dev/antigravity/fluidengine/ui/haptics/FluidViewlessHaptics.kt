package dev.antigravity.fluidengine.ui.haptics

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Gli effetti "di fabbrica" del vibratore, per chi non ha un `View` da cui chiedere una costante di
 * tocco (`View.performHapticFeedback`) ne' un `HapticFeedback` di Compose.
 *
 * Gli `effectId` sono i valori numerici di `VibrationEffect.EFFECT_*`, copiati qui perche' la mappa
 * si prova sul computer, dove `android.os.VibrationEffect` non esiste: un test confronta ogni id
 * con la costante vera, cosi' una copia che diverge si vede.
 */
internal enum class PredefinedHaptic(val effectId: Int) {
  /** `EFFECT_TICK`: il colpo piu' leggero e secco. */
  Tick(2),

  /** `EFFECT_CLICK`: un colpo pieno. */
  Click(0),

  /** `EFFECT_DOUBLE_CLICK`: due colpi, la voce di un "no". */
  DoubleClick(1),

  /** `EFFECT_HEAVY_CLICK`: un colpo pieno e lungo, per cio' che deve farsi sentire. */
  HeavyClick(5),
}

/**
 * Quale effetto di fabbrica suona una costante di tocco di Compose, quando non c'e' un `View`.
 *
 * Con un `View` la costante e' quella che il produttore del telefono ha tarato (su Samsung `Reject`
 * e `ContextClick` non sono un tick generico); senza, resta solo l'effetto di fabbrica piu'
 * somigliante, e i quattro che esistono (tick, click, doppio click, click pesante) bastano a
 * tenere in piedi il vocabolario: **la forza relativa e' quella dei pattern a composizione**
 * ([FluidHapticPatterns.composition]). Un `Tap` (tick a 0,5) resta piu' leggero di un `Open` (click a 0,6) e
 * di un `Success`; un `Reject` e un `Error` restano due colpi, e un'allerta pesa.
 *
 * Non e' la mappa del sistema operativo, e non dice di esserlo: e' la piu' vicina alle intenzioni
 * dell'engine, scritta evento per evento sulle costanti che [FluidHapticPatterns.fallback] usa.
 * Un tipo che non conosce (uno aggiunto da una versione futura di Compose) suona un click:
 * meglio un colpo sensato che niente.
 */
internal fun predefinedHapticFor(type: HapticFeedbackType): PredefinedHaptic = when (type) {
  // I colpi leggeri: un aggancio, un tocco, la fine di un gesto, uno spegnimento.
  HapticFeedbackType.SegmentTick,
  HapticFeedbackType.SegmentFrequentTick,
  HapticFeedbackType.TextHandleMove,
  HapticFeedbackType.KeyboardTap,
  HapticFeedbackType.VirtualKey,
  HapticFeedbackType.GestureEnd,
  HapticFeedbackType.ToggleOff,
  -> PredefinedHaptic.Tick

  // I colpi pieni: un esito, una soglia, un accendersi, qualcosa che si apre.
  HapticFeedbackType.Confirm,
  HapticFeedbackType.GestureThresholdActivate,
  HapticFeedbackType.ToggleOn,
  HapticFeedbackType.ContextClick,
  -> PredefinedHaptic.Click

  // Un no: due colpi.
  HapticFeedbackType.Reject -> PredefinedHaptic.DoubleClick

  // Cio' che deve farsi sentire: un avviso, un'allerta.
  HapticFeedbackType.LongPress -> PredefinedHaptic.HeavyClick

  else -> PredefinedHaptic.Click
}

/**
 * Lo stesso effetto per un telefono con Android 8-9 (API 26-28), dove `VibrationEffect.createPredefined`
 * non c'e' (API 29): una forma d'onda `[attesa, acceso, attesa, acceso...]` in millisecondi, all'ampiezza
 * di serie del vibratore. Poco piu' di un ronzio, ma e' il meglio che quei telefoni sanno fare senza
 * primitive, e sono gli unici a passare di qui.
 */
internal fun legacyWaveformFor(effect: PredefinedHaptic): LongArray = when (effect) {
  PredefinedHaptic.Tick -> longArrayOf(0, 10)
  PredefinedHaptic.Click -> longArrayOf(0, 20)
  PredefinedHaptic.DoubleClick -> longArrayOf(0, 15, 70, 15)
  PredefinedHaptic.HeavyClick -> longArrayOf(0, 40)
}
