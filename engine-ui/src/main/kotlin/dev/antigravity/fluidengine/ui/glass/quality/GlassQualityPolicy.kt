package dev.antigravity.fluidengine.ui.glass.quality

/**
 * F7.26e–g · **Fin dove una GPU di fascia bassa ([GlassGpuClass.Low]) sale senza la prova dei fotogrammi.** Parte
 * Protetta ([GlassQualityPolicy.decision] d'avvio) e da lì sale **solo coi fotogrammi**: alla posa, dopo trenta secondi
 * di moto e tre finestre buone, come voleva F7. È la scelta più prudente sui telefoni che non reggono.
 *
 * La storia: in F7.26e valeva [GlassOptics.Reduced] (la GPU bassa saliva alla prima posa coi soli segnali), perché a
 * 120 Hz la risalita coi fotogrammi non arrivava mai. F7.26g (review R3, D2) ha corretto la regola delle finestre sopra i
 * 60 Hz ([GlassFrameHistory]), e la risalita coi fotogrammi adesso arriva; la scelta l'ha presa l'orchestratore, il
 * 27 novembre. [GlassOptics.Reduced] qui rimette la salita coi segnali alla prima posa.
 */
internal val LowGpuSignalCeiling: GlassOptics = GlassOptics.Protected

/** Politica F7.10. Un solo proprietario serializza letture, frame ed eventi di finestra F7.11.
 * Il costruttore produce la prima [decision] anche prima che esista una finestra. I metodi
 * restituiscono il motivo solo quando cambia lo snapshot. Nessun timer, lavoro Android o lettura nel draw.
 *
 * [shaderCeiling] è il massimo della ricetta realmente utilizzabile (null = sonda ancora ignota).
 * [appCeiling] incorpora le restrizioni già risolte dal chiamante. Un livello consente una spesa,
 * non abilita una ricetta che il tier effettivo ha escluso.
 */
internal class GlassQualityPolicy(
  capacity: GlassDeviceCapacity = GlassDeviceCapacity(),
  conditions: GlassConditions = GlassConditions(),
  shaderCeiling: GlassOptics? = null,
  appCeiling: GlassOptics = GlassOptics.Full,
) {
  private var device = capacity
  private var signals = conditions
  private var technicalLimit = shaderCeiling
  private var appLimit = appCeiling
  private var lowRamSeen = capacity.lowRamDevice == true
  private var budgetDenied = false
  private val frames = GlassFrameHistory()
  private var visible = false
  private var moving = false
  private var display = -1
  private var period = 0L
  private var motionStart = 0L
  private var clock = -1L
  private var lastChange = -1L
  private var promotedAt = -1L
  private var rollbackAt = -1L
  private var retryDelayNs = 0L

  /** F7.26d, solo per la riga dei voti delle build di prova: quanti fotogrammi sono arrivati alla storia, quanti hanno votato. */
  internal val frameCalls get() = frames.calls
  internal val frameVotes get() = frames.votes
  internal fun describeFrames() = "${frames.describe()} tetto=$frameLevel moto=$moving"

  internal fun budgetRollbackAt(now: Long) = now >= 0 && rollbackAt == now
  private fun recentlyPromoted(now: Long) = promotedAt >= 0 && now - promotedAt in 0..5_000_000_000L
  internal val nextOptics get() = minOf(decision.optics.higher(), technicalLimit ?: GlassOptics.Full, appLimit)
  /** Alla posa di recupero la pressione passata si toglie, se non ce n'è una nuova (vedi [publish]). */
  internal val nextMemoryProfile get() = memoryRow(memoryPressured && criticalReason() != null)

  private val validMemoryReading get() = signals.pressureThresholdBytes > 0 && signals.availableBytes >= 0 &&
    (device.totalRamBytes <= 0 || maxOf(signals.pressureThresholdBytes, signals.availableBytes) <= device.totalRamBytes)
  private val validHeap get() = signals.heapFraction in 0.0..1.0
  private val completeMemory get() = device.complete && validMemoryReading && validHeap && signals.lowMemory != null
  private val ampleMemory get() = completeMemory && signals.lowMemory == false && signals.heapFraction < 0.70 &&
    signals.availableBytes - signals.pressureThresholdBytes > signals.pressureThresholdBytes / 2
  private val cool get() = signals.thermal == GlassThermal.None || signals.thermal == GlassThermal.Light
  private fun healthy() = ampleMemory && cool && signals.powerSave == false && !budgetDenied

  private fun criticalReason(): GlassQualityReason? = when {
    signals.lowMemory == true || validMemoryReading && signals.availableBytes <= signals.pressureThresholdBytes ->
      GlassQualityReason.MemoryPressure
    validHeap && signals.heapFraction >= 0.85 -> GlassQualityReason.HeapPressure
    budgetDenied -> GlassQualityReason.VisibleBudgetDenied
    signals.thermal == GlassThermal.SevereOrWorse -> GlassQualityReason.SevereThermal
    else -> null
  }

  private var canRecover = healthy()
  private var prefetch = completeMemory && criticalReason() == null
  private fun physicalProfile() = if (lowRamSeen) GlassMemoryProfile.Protected else device.memoryCeiling

  /**
   * La riga dei tetti di memoria (F7.18). Dipende dalla **fascia del dispositivo** ([GlassDeviceCapacity.memoryCeiling]),
   * dalla pressione vera e dal tetto della sessione, **non** dal livello ottico.
   *
   * Fino a F7.17 la riga seguiva l'ottica ([level]): una cattura visibile negata portava l'ottica a Protetto, e con lei il
   * tetto del vetro da 24 o 48 MiB a 12. Il rifiuto si alimentava da solo: sul Tab S9 (8 GB, `memoryClass` 512 MiB, fascia
   * Pieno) il popover della capsula della data chiedeva 19,8 MB contro un tetto ormai di 12 MiB e non entrava mai, e il
   * pannello restava trasparente (24 novembre 2026, `limit=12582912`). Anche l'avvio Ridotto per la sola classe
   * multimediale assente o per un driver non riconosciuto dimezzava il tetto, e così un gesto con tre fotogrammi in
   * ritardo. Quelle sono ragioni per catture **più piccole** (la scala del livello, [GlassOptics.backgroundScaleCeiling]),
   * non per un tetto più basso: il tetto protegge dalla memoria che manca, e la memoria non manca.
   *
   * Restano: la fascia fisica (la RAM, l'heap e `isLowRamDevice`); la pressione critica del sistema (lowMemory,
   * disponibile sotto la soglia, heap all'85%) e la termica severa, come in F7.10, che tengono la riga Protetta fino alla
   * prossima posa di recupero ([memoryPressured]), come l'ottica; e il tetto della sessione dopo un'uscita per memoria
   * (F7.11), che arriva come [appLimit] e vale per tutta la vita della politica ([sessionMemory]).
   */
  @Suppress("UNUSED_PARAMETER")
  private fun memoryFor(level: GlassOptics): GlassMemoryProfile = memoryRow(memoryPressured)

  private fun memoryRow(pressured: Boolean): GlassMemoryProfile =
    minOf(physicalProfile(), if (pressured) GlassMemoryProfile.Protected else GlassMemoryProfile.Full, sessionMemory)

  private fun GlassOptics.memoryCeiling() = when (this) {
    GlassOptics.Full -> GlassMemoryProfile.Full
    GlassOptics.Reduced -> GlassMemoryProfile.Reduced
    GlassOptics.Protected -> GlassMemoryProfile.Protected
  }

  /** F7.18: il tetto più basso chiesto dalla sessione (l'uscita per memoria di F7.11). Scende soltanto. */
  private var sessionMemory = appCeiling.memoryCeiling()

  /** F7.18: una pressione vera (non un rifiuto visibile) ha portato la riga a Protetto; si toglie alla posa di recupero. */
  private var memoryPressured = criticalReason().let { it != null && it != GlassQualityReason.VisibleBudgetDenied }

  private fun initialOptics(): GlassOptics {
    val cold = when {
      criticalReason() != null || physicalProfile() == GlassMemoryProfile.Protected -> GlassOptics.Protected
      // F7.26: una GPU di fascia bassa (il 778G dell'A52s, le serie A) parte Protetta anche con la memoria che basta, e
      // risale coi fotogrammi (fino a [LowGpuSignalCeiling] anche coi soli segnali). Uno sconosciuto parte Ridotto.
      device.gpu == GlassGpuClass.Low -> GlassOptics.Protected
      physicalProfile() == GlassMemoryProfile.Full && device.recognizedGpuDriver && device.mediaPerformanceClass > 0 &&
        technicalLimit == GlassOptics.Full && canRecover -> GlassOptics.Full
      else -> GlassOptics.Reduced
    }
    return minOf(cold, appLimit, technicalLimit ?: GlassOptics.Full)
  }

  /**
   * F7.26, dalla review R2 (difetto 1) · **Il livello che i fotogrammi hanno concesso.** La politica scende per due
   * ragioni diverse: i fotogrammi in ritardo (la GPU non regge) e i segnali (pressione di memoria, heap, termica,
   * risparmio, un rifiuto del registro, una lettura che manca). Fino a F7.25 anche le discese da segnale risalivano solo
   * con la prova dei fotogrammi (trenta secondi di moto e tre finestre buone): in un'app che si legge e si scorre poco,
   * passata la pressione il vetro restava giù per tutta la vita della finestra.
   *
   * Questo è il tetto che vale per i fotogrammi: parte dal livello prudente del dispositivo (la fascia della memoria, la
   * GPU bassa fino a [LowGpuSignalCeiling], il Pieno solo se la GPU è riconosciuta) e si muove **solo** con i fotogrammi (scende con le discese da
   * ritardi, sale con la risalita misurata). Sotto di lui la distanza è dei segnali, e si recupera alla prima posa con i
   * segnali sani, senza chiedere altro moto ([onWindow]). Una GPU di punta ([GlassGpuClass.High]) ha il Pieno qui fin
   * dall'avvio: il suo avvio Ridotto (verdetti degli shader non ancora dati, classe multimediale assente) è prudenza da
   * segnale, non da fotogrammi.
   */
  private var frameLevel: GlassOptics = when {
    physicalProfile() == GlassMemoryProfile.Protected -> GlassOptics.Protected
    device.gpu == GlassGpuClass.Low -> LowGpuSignalCeiling
    device.gpu == GlassGpuClass.High -> GlassOptics.Full
    // Come il Pieno d'avvio, ma senza i segnali di adesso: una pressione già presente è una discesa da segnale.
    physicalProfile() == GlassMemoryProfile.Full && device.recognizedGpuDriver && device.mediaPerformanceClass > 0 &&
      technicalLimit == GlassOptics.Full -> GlassOptics.Full
    else -> GlassOptics.Reduced
  }

  /** Il livello più alto che i segnali di adesso lascerebbero, sotto il tetto dei fotogrammi e i tetti tecnici. */
  private val signalCeiling get() = minOf(frameLevel, technicalLimit ?: GlassOptics.Full, appLimit)

  /** Una discesa da segnale aspetta ancora la risalita: un livello sotto il tetto, la riga in pressione, il prefetch fermo. */
  private val signalPending get() = decision.optics < signalCeiling || memoryPressured || !prefetch

  var decision: GlassQualityDecision = initialOptics().let {
    GlassQualityDecision(it, memoryFor(it), device.heapReferenceBytes, prefetch)
  }
    private set
  val peakDeadlineOverrunNs get() = frames.peakOverrunNs

  /** Una nuova lettura Android può riaprire la risalita. Il veto del lettore ha un rilascio esplicito. */
  internal val needsRecoveryReading: Boolean
    get() = !budgetDenied && (decision.optics < minOf(technicalLimit ?: GlassOptics.Full, appLimit) || !prefetch ||
      memoryPressured)

  /** Unica base tempi (ns monotoni) per le chiamate. Un riavvio del clock apre una nuova epoca. */
  private fun observeClock(nowNs: Long): Boolean {
    if (nowNs < 0) return false
    if (nowNs < clock) {
      frames.newEpoch()
      motionStart = nowNs
      lastChange = nowNs
      promotedAt = -1L
      if (rollbackAt >= 0) rollbackAt = nowNs
    }
    clock = nowNs
    return true
  }

  private fun publish(level: GlassOptics, reason: GlassQualityReason): GlassQualityReason? {
    if (reason == GlassQualityReason.SettledRecovery && criticalReason() == null) memoryPressured = false
    val memory = memoryFor(level)
    val heap = device.heapReferenceBytes
    if (level == decision.optics && memory == decision.memoryProfile &&
      heap == decision.heapReferenceBytes && prefetch == decision.allowPrefetch
    ) return null
    if (level > decision.optics) {
      // Una promozione precedente che ha retto chiude la sua serie di ricadute.
      if (promotedAt >= 0 && !recentlyPromoted(clock)) retryDelayNs = 0L
      promotedAt = clock
      rollbackAt = -1L
    } else if (level < decision.optics) {
      if (reason == GlassQualityReason.VisibleBudgetDenied && recentlyPromoted(clock)) {
        rollbackAt = clock
        retryDelayNs = if (retryDelayNs == 0L) 60_000_000_000L else minOf(240_000_000_000L, retryDelayNs * 2)
      } else {
        rollbackAt = -1L
        retryDelayNs = 0L
      }
      promotedAt = -1L
    }
    decision = GlassQualityDecision(level, memory, heap, prefetch)
    lastReason = reason
    lastChange = clock
    frames.resetVotes()
    return reason
  }

  /** F7.26: il motivo dell'ultimo cambio di [decision], per la riga di diagnostica dei cambi di livello. */
  internal var lastReason: GlassQualityReason? = null
    private set

  fun updateCapacity(nowNs: Long, value: GlassDeviceCapacity): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    device = value
    lowRamSeen = lowRamSeen || value.lowRamDevice == true
    // Una capacità che scende abbassa anche il tetto dei fotogrammi; una che sale no: quello si guadagna col moto.
    if (physicalProfile() == GlassMemoryProfile.Protected) {
      frameLevel = GlassOptics.Protected
    } else if (value.gpu == GlassGpuClass.Low) {
      frameLevel = minOf(frameLevel, LowGpuSignalCeiling)
    }
    return applyLimits(GlassQualityReason.Capacity)
  }

  fun updateConditions(nowNs: Long, value: GlassConditions): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    signals = value
    return applyLimits(GlassQualityReason.MissingSignals)
  }

  /** Passare false solo dopo una nuova ammissione del visibile, anche di dimensione ridotta. */
  fun setVisibleBudgetDenied(nowNs: Long, denied: Boolean): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    budgetDenied = denied
    return applyLimits(GlassQualityReason.VisibleBudgetDenied)
  }

  fun setCeilings(nowNs: Long, shader: GlassOptics?, app: GlassOptics): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    // Un campione assente non cancella un verdetto già ottenuto per questa ricetta.
    if (shader != null) technicalLimit = shader
    appLimit = app
    sessionMemory = minOf(sessionMemory, app.memoryCeiling())
    return applyLimits(GlassQualityReason.TechnicalCeiling)
  }

  private fun applyLimits(defaultReason: GlassQualityReason): GlassQualityReason? {
    canRecover = healthy()
    if (!canRecover) frames.invalidateRecovery()
    val critical = criticalReason()
    if (critical != null && critical != GlassQualityReason.VisibleBudgetDenied) memoryPressured = true
    if (critical != null || !completeMemory) prefetch = false
    if (critical != null) return publish(GlassOptics.Protected, critical)

    var target = decision.optics
    var reason = defaultReason
    if (target == GlassOptics.Full) {
      val restriction = when {
        signals.thermal == GlassThermal.Moderate -> GlassQualityReason.ModerateThermal
        signals.powerSave == true -> GlassQualityReason.PowerSave
        !completeMemory || signals.powerSave == null || signals.thermal == GlassThermal.Unknown ->
          GlassQualityReason.MissingSignals
        else -> null
      }
      if (restriction != null) {
        target = GlassOptics.Reduced
        reason = restriction
      }
    }
    val shader = technicalLimit ?: GlassOptics.Full
    if (shader < target) {
      target = shader
      reason = GlassQualityReason.TechnicalCeiling
    }
    if (appLimit < target) {
      target = appLimit
      reason = GlassQualityReason.AppCeiling
    }
    return publish(target, reason)
  }

  /** Cambiare refresh effettivo o display apre una nuova serie. Uscire dal moto offre la posa una volta sola. */
  fun onWindow(nowNs: Long, isVisible: Boolean, inMotion: Boolean, displayId: Int, periodNs: Long) =
    onWindow(nowNs, isVisible, inMotion, displayId, periodNs, retryFits = true)

  internal fun onWindow(
    nowNs: Long, isVisible: Boolean, inMotion: Boolean, displayId: Int, periodNs: Long,
    retryFits: Boolean,
  ): GlassQualityReason? {
    if (!observeClock(nowNs)) return null
    val changed = visible != isVisible || display != displayId || period != periodNs
    val wasMoving = moving
    visible = isVisible
    display = displayId
    period = periodNs
    moving = visible && inMotion && display >= 0 && period in 1..1_000_000_000L
    if (changed) frames.newEpoch()
    if (changed || moving != wasMoving) {
      frames.breakSequence()
      motionStart = nowNs
    }
    if (!changed && wasMoving && !moving && canRecover && cooldownOver() &&
      retryFits && (rollbackAt < 0 || nowNs - rollbackAt >= retryDelayNs)) {
      if (frames.recoveryReady) {
        prefetch = true
        val reason = publish(nextOptics, GlassQualityReason.SettledRecovery)
        frameLevel = maxOf(frameLevel, decision.optics)
        return reason
      }
      // F7.26 (R2): la distanza dai segnali si recupera alla posa senza la prova dei fotogrammi. Un livello alla volta,
      // nello stesso punto in cui la risalita misurata cambia la cattura (la posa), con la stessa attesa dopo un cambio.
      if (signalPending) return signalRise(oneStep = true)
    }
    return null
  }

  /**
   * F7.26 (R2) · Quando la finestra di questa politica non si vede (in fondo, chiusa, o è la decisione d'avvio senza
   * finestra) la risalita da segnale non ha niente da mostrare: si applica alla lettura sana, fino al tetto dei
   * fotogrammi. Al ritorno in primo piano la finestra riparte già risalita. Lo chiama il registro dopo ogni lettura.
   */
  internal fun riseUnseen(nowNs: Long): GlassQualityReason? {
    if (!observeClock(nowNs) || visible || !canRecover || !signalPending) return null
    if (rollbackAt >= 0 && nowNs - rollbackAt < retryDelayNs) return null
    return signalRise(oneStep = false)
  }

  private fun signalRise(oneStep: Boolean): GlassQualityReason? {
    val ceiling = signalCeiling
    val target = when {
      decision.optics >= ceiling -> decision.optics
      oneStep -> minOf(decision.optics.higher(), ceiling)
      else -> ceiling
    }
    prefetch = true
    return publish(target, GlassQualityReason.SettledRecovery)
  }

  private fun cooldownOver() = lastChange < 0 || clock - lastChange >= 5_000_000_000L

  /** TOTAL_DURATION e DEADLINE sono durate, workNs è lavoro app misurabile in quel frame.
   * frameTimeNs è il vsync originale, nowNs l'ora di consegna. Un report perso spezza la consecutività.
   */
  fun onFrame(
    nowNs: Long, frameTimeNs: Long, totalDurationNs: Long, deadlineNs: Long, workNs: Long,
    evidence: GlassFrameEvidence, droppedReports: Int = 0,
  ): GlassQualityReason? {
    if (!observeClock(nowNs) || !moving || frameTimeNs !in motionStart..nowNs) return null
    val reason = frames.record(frameTimeNs, period, totalDurationNs, deadlineNs, workNs, evidence, droppedReports, canRecover)
      ?: return null
    if (reason == GlassQualityReason.TwoBadWindows && !cooldownOver()) return null
    val published = publish(decision.optics.lower(), reason)
    // Una discesa da fotogrammi abbassa il tetto dei fotogrammi: da qui si risale solo col moto misurato.
    frameLevel = minOf(frameLevel, decision.optics)
    return published
  }
}
