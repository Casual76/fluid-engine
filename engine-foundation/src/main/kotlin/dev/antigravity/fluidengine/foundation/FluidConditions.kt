package dev.antigravity.fluidengine.foundation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// F8.20 · Pochi dati, poca batteria (`pochi-dati-poca-batteria`).
//
// Un posto solo che sa in che condizioni e' il telefono: la rete (c'e', porta a internet, costa, e' in roaming), il
// risparmio dati, il risparmio energetico, il calore e la carica. E una regola uguale per tutti: un lavoro pesante chiede
// il permesso prima di partire ([FluidConditions.allows]) e, se la risposta e' no, aspetta, dice il perche' e offre
// «Fallo comunque».
//
// Il riferimento Apple e' la coppia «Modalita' dati ridotti» e «Risparmio energetico»: `NWPath.isExpensive` (rete
// cellulare o hotspot: qui [FluidConditions.metered] e [FluidConditions.roaming]), `NWPath.isConstrained` (Modalita' dati
// ridotti: qui [FluidConditions.dataSaver] e [FluidConditions.lowData]), `ProcessInfo.isLowPowerModeEnabled`
// ([FluidConditions.powerSave]) e `ProcessInfo.thermalState` ([FluidThermal]). Fonte: la documentazione di Network
// framework e di Foundation (fiducia alta sui nomi e sul significato; le regole per lavoro sono nostre, sotto).
//
// Qui solo i tipi e la regola, puri: la lettura di Android sta in `engine-net` (`EngineConditions`), che pubblica in
// [FluidDeviceConditions]. I pezzi dell'engine che si regolano da soli (la luce del vetro che segue l'inclinazione,
// l'aptica continua, gli stati pronti che riprovano) leggono da [FluidDeviceConditions].

/**
 * Quanto scalda il telefono: i quattro gradini di `ProcessInfo.ThermalState` di Apple (nominal, fair, serious,
 * critical). Su Android li legge `engine-net` da `PowerManager.getCurrentThermalStatus` (API 29): nessuno e leggero →
 * [Nominal], moderato → [Fair], severo → [Serious], critico e oltre → [Critical]. Sotto l'API 29 resta [Nominal].
 *
 * Non e' un `enum` (regola 3.11): un gradino nuovo non rompe i `when` delle app.
 */
class FluidThermal private constructor(
  /** Il gradino, da 0 ([Nominal]) a 3 ([Critical]): si confrontano. */
  val level: Int,
  private val label: String,
) : Comparable<FluidThermal> {
  /**
   * Il telefono e' caldo: [Serious] o oltre. Apple chiede, da `serious`, di ridurre il lavoro che si puo' rimandare; e'
   * quello che fa la regola dei lavori ([FluidWork.avoidsHeat]).
   */
  val hot: Boolean get() = level >= Serious.level

  override fun compareTo(other: FluidThermal): Int = level.compareTo(other.level)
  override fun equals(other: Any?): Boolean = other is FluidThermal && other.level == level
  override fun hashCode(): Int = level
  override fun toString(): String = "FluidThermal($label)"

  companion object {
    val Nominal: FluidThermal = FluidThermal(0, "nominal")
    val Fair: FluidThermal = FluidThermal(1, "fair")
    val Serious: FluidThermal = FluidThermal(2, "serious")
    val Critical: FluidThermal = FluidThermal(3, "critical")

    /** Il gradino [level] (fuori scala si ferma ai bordi). */
    fun of(level: Int): FluidThermal = when {
      level <= 0 -> Nominal
      level == 1 -> Fair
      level == 2 -> Serious
      else -> Critical
    }
  }
}

/**
 * Le condizioni del telefono adesso. Si legge da [FluidDeviceConditions] (o da `EngineConditions` di `engine-net`) e si
 * interroga con [allows].
 *
 * @param online c'e' una rete che porta davvero a internet (`NET_CAPABILITY_VALIDATED`, la VPN compresa).
 * @param connected c'e' una rete, anche senza internet (un Wi-Fi d'albergo prima del login): [online] falso e questo
 *   vero e' «rete senza internet».
 * @param metered la rete costa (dati mobili, hotspot): `isExpensive` di Apple.
 * @param roaming la rete e' in roaming: costa moltissimo.
 * @param dataSaver il risparmio dati di Android limita quest'app su una rete a consumo: `isConstrained` di Apple. Su una
 *   rete che non costa il risparmio dati non conta, e qui e' falso.
 * @param powerSave il risparmio energetico e' acceso: `isLowPowerModeEnabled`.
 * @param thermal quanto scalda.
 * @param charging il telefono e' attaccato alla corrente.
 * @param lowData l'utente ha chiesto all'app di usare pochi dati (l'interruttore «Usa pochi dati» dell'app): vale come il
 *   risparmio dati, su qualsiasi rete.
 * @param known falso finche' nessuno ha letto le condizioni: allora [allows] non ferma niente (non si blocca un lavoro su
 *   un sospetto).
 */
data class FluidConditions(
  val online: Boolean = true,
  val connected: Boolean = true,
  val metered: Boolean = false,
  val roaming: Boolean = false,
  val dataSaver: Boolean = false,
  val powerSave: Boolean = false,
  val thermal: FluidThermal = FluidThermal.Nominal,
  val charging: Boolean = false,
  val lowData: Boolean = false,
  val known: Boolean = true,
) {
  /** Il telefono e' caldo ([FluidThermal.hot]). */
  val hot: Boolean get() = thermal.hot

  /** La rete c'e' e non costa (Wi-Fi o Ethernet): e' il «solo Wi-Fi» di un trasferimento e il `NetworkType.UNMETERED` di WorkManager. */
  val unmetered: Boolean get() = online && !metered && !roaming

  /**
   * Il permesso per [work], con i vincoli del singolo lavoro ([needs]: solo Wi-Fi, solo in carica). Con [force] (il tasto
   * «Fallo comunque») passano tutti i motivi che si possono forzare; la mancanza di rete no: senza rete non si scarica.
   *
   * La regola, in ordine (i motivi escono tutti, il primo e' il piu' importante):
   * - un lavoro di rete senza rete: [FluidConditionReason.Offline], o [FluidConditionReason.NoInternet] se c'e' una rete
   *   che non porta a internet;
   * - [FluidWork.avoidsExpensive] o [FluidWork.avoidsConstrained] in roaming: [FluidConditionReason.Roaming];
   * - [FluidWork.avoidsExpensive] su rete a consumo: [FluidConditionReason.Metered];
   * - [FluidWork.avoidsConstrained] col risparmio dati: [FluidConditionReason.DataSaver]; con «Usa pochi dati»:
   *   [FluidConditionReason.LowData];
   * - [needs] solo Wi-Fi su una rete che costa: [FluidConditionReason.NotWifi]; solo in carica senza corrente:
   *   [FluidConditionReason.NotCharging];
   * - [FluidWork.avoidsLowPower] col risparmio energetico e senza corrente: [FluidConditionReason.PowerSave] (attaccato
   *   alla corrente la batteria non si consuma); con [FluidWork.lowPowerWhileCharging] anche in carica (gli effetti:
   *   chi accende il risparmio energetico non li vuole nemmeno col cavo, F8.20b);
   * - [FluidWork.avoidsHeat] col telefono caldo: [FluidConditionReason.Hot].
   */
  fun allows(work: FluidWork, needs: FluidWorkNeeds = FluidWorkNeeds.None, force: Boolean = false): FluidPermit {
    if (!known) return FluidPermit.Allowed
    val reasons = ArrayList<FluidConditionReason>(2)
    if (work.needsNetwork && !online) {
      reasons += if (connected) FluidConditionReason.NoInternet else FluidConditionReason.Offline
    }
    val networkUp = online || !work.needsNetwork
    if (work.needsNetwork && networkUp) {
      if (roaming && (work.avoidsExpensive || work.avoidsConstrained)) reasons += FluidConditionReason.Roaming
      if (metered && work.avoidsExpensive && !roaming) reasons += FluidConditionReason.Metered
      if (work.avoidsConstrained && dataSaver) reasons += FluidConditionReason.DataSaver
      if (work.avoidsConstrained && lowData) reasons += FluidConditionReason.LowData
      if (needs.wifiOnly && !unmetered && FluidConditionReason.Metered !in reasons) reasons += FluidConditionReason.NotWifi
    }
    if (needs.chargingOnly && !charging) reasons += FluidConditionReason.NotCharging
    if (work.avoidsLowPower && powerSave && (!charging || work.lowPowerWhileCharging)) reasons += FluidConditionReason.PowerSave
    if (work.avoidsHeat && hot) reasons += FluidConditionReason.Hot
    if (reasons.isEmpty()) return FluidPermit.Allowed
    return FluidPermit(reasons, forced = force)
  }

  companion object {
    /** Nessuno ha ancora letto le condizioni: [allows] lascia passare tutto. */
    val Unknown: FluidConditions = FluidConditions(known = false)

    /** Senza rete (le altre condizioni tranquille): per le prove e per la simulazione della galleria. */
    val Offline: FluidConditions = FluidConditions(online = false, connected = false)
  }
}

/**
 * I vincoli che un singolo lavoro aggiunge alla regola di [FluidWork]: «solo Wi-Fi» e «solo in carica», le due spunte di
 * un trasferimento (e di WorkManager: `NetworkType.UNMETERED`, `setRequiresCharging`).
 */
data class FluidWorkNeeds(
  /** Solo su una rete che non costa ([FluidConditions.unmetered]). */
  val wifiOnly: Boolean = false,
  /** Solo attaccato alla corrente. */
  val chargingOnly: Boolean = false,
) {
  companion object {
    /** Nessun vincolo in piu'. */
    val None: FluidWorkNeeds = FluidWorkNeeds()
  }
}

/**
 * Un genere di lavoro, con quello che evita. Si chiede il permesso con [FluidConditions.allows] (o
 * [FluidDeviceConditions.allows]) prima di cominciarlo.
 *
 * Non e' un `enum` (regola 3.11): [custom] fa un lavoro su misura e i generi nuovi si aggiungono senza rompere niente.
 * Due lavori con lo stesso [name] sono lo stesso.
 *
 * @property needsNetwork serve la rete.
 * @property avoidsExpensive evita le reti che costano (a consumo, roaming): `isExpensive` di Apple. E' il caso dei
 *   download grandi: l'App Store chiede prima di scaricare un'app grande coi dati mobili.
 * @property avoidsConstrained evita il risparmio dati e «Usa pochi dati»: `isConstrained` di Apple (Modalita' dati
 *   ridotti mette in pausa gli aggiornamenti in sottofondo e abbassa la qualita' dello streaming).
 * @property avoidsLowPower si ferma col risparmio energetico (se il telefono non e' in carica).
 * @property avoidsHeat si ferma col telefono caldo ([FluidThermal.hot]).
 * @property lowPowerWhileCharging col risparmio energetico si ferma **anche in carica** (F8.20b): gli effetti
 *   ([MotionEffects], [ContinuousHaptics]) seguono la scelta dell'utente, non la batteria; i lavori grandi no, attaccati
 *   alla corrente non consumano la batteria.
 */
class FluidWork private constructor(
  val name: String,
  val needsNetwork: Boolean,
  val avoidsExpensive: Boolean,
  val avoidsConstrained: Boolean,
  val avoidsLowPower: Boolean,
  val avoidsHeat: Boolean,
  val lowPowerWhileCharging: Boolean = false,
) {
  override fun equals(other: Any?): Boolean = other is FluidWork && other.name == name
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = "FluidWork($name)"

  companion object {
    /**
     * Scaricare un file grande (un APK, una mappa, un brano per l'ascolto offline): evita le reti che costano, il
     * risparmio dati, il risparmio energetico e il calore. E' `condizioni.consente(Lavoro.ScaricaGrande)` della scheda.
     */
    val LargeDownload: FluidWork = FluidWork("large-download", true, true, true, true, true)

    /** Caricare un file grande (le foto e i video della galleria): come [LargeDownload]. */
    val LargeUpload: FluidWork = FluidWork("large-upload", true, true, true, true, true)

    /** Scaricare in anticipo qualcosa che l'utente non ha chiesto ancora (le prossime puntate, le miniature): come [LargeDownload]. */
    val Prefetch: FluidWork = FluidWork("prefetch", true, true, true, true, true)

    /**
     * Media in qualita' alta o in riproduzione automatica (un video che parte da solo, lo streaming migliore): evita il
     * risparmio dati, «Usa pochi dati», il roaming e il risparmio energetico; su una rete a consumo normale si', come
     * su iOS fuori dalla Modalita' dati ridotti.
     */
    val RichMedia: FluidWork = FluidWork("rich-media", true, false, true, true, false)

    /** Una sincronizzazione piccola (le modifiche di una nota): serve solo la rete. */
    val Sync: FluidWork = FluidWork("sync", true, false, false, false, false)

    /**
     * Gli effetti di movimento che consumano senza servire (la luce del vetro che segue l'inclinazione, che tiene acceso
     * un sensore e ridisegna il vetro): si fermano col risparmio energetico e col telefono caldo.
     */
    val MotionEffects: FluidWork = FluidWork("motion-effects", false, false, false, true, true, lowPowerWhileCharging = true)

    /** L'aptica continua (la grana di un cursore, i tick di una ruota): si ferma col risparmio energetico, anche in carica. */
    val ContinuousHaptics: FluidWork = FluidWork("continuous-haptics", false, false, false, true, false, lowPowerWhileCharging = true)

    /** Un lavoro su misura. */
    fun custom(
      name: String,
      needsNetwork: Boolean = true,
      avoidsExpensive: Boolean = false,
      avoidsConstrained: Boolean = false,
      avoidsLowPower: Boolean = false,
      avoidsHeat: Boolean = false,
      lowPowerWhileCharging: Boolean = false,
    ): FluidWork = FluidWork(name, needsNetwork, avoidsExpensive, avoidsConstrained, avoidsLowPower, avoidsHeat, lowPowerWhileCharging)
  }
}

/**
 * Perche' un lavoro aspetta. [forceable] dice se «Fallo comunque» lo supera: tutti tranne la mancanza di rete.
 *
 * Non e' un `enum` (regola 3.11). Due motivi con lo stesso [name] sono lo stesso.
 */
class FluidConditionReason private constructor(val name: String, val forceable: Boolean) {
  override fun equals(other: Any?): Boolean = other is FluidConditionReason && other.name == name
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = "FluidConditionReason($name)"

  companion object {
    /** Non c'e' rete. */
    val Offline: FluidConditionReason = FluidConditionReason("offline", forceable = false)

    /** C'e' una rete ma non porta a internet (`NET_CAPABILITY_VALIDATED` assente). */
    val NoInternet: FluidConditionReason = FluidConditionReason("no-internet", forceable = false)

    /** La rete e' in roaming. */
    val Roaming: FluidConditionReason = FluidConditionReason("roaming", forceable = true)

    /** La rete e' a consumo. */
    val Metered: FluidConditionReason = FluidConditionReason("metered", forceable = true)

    /** Il risparmio dati di Android. */
    val DataSaver: FluidConditionReason = FluidConditionReason("data-saver", forceable = true)

    /** «Usa pochi dati» dell'app. */
    val LowData: FluidConditionReason = FluidConditionReason("low-data", forceable = true)

    /** Il risparmio energetico. */
    val PowerSave: FluidConditionReason = FluidConditionReason("power-save", forceable = true)

    /** Il telefono e' caldo. */
    val Hot: FluidConditionReason = FluidConditionReason("hot", forceable = true)

    /** Il lavoro vuole il Wi-Fi e la rete costa. */
    val NotWifi: FluidConditionReason = FluidConditionReason("not-wifi", forceable = true)

    /** Il lavoro vuole la corrente e il telefono non e' in carica. */
    val NotCharging: FluidConditionReason = FluidConditionReason("not-charging", forceable = true)

    /** Tutti i motivi, nell'ordine in cui [FluidConditions.allows] li dice. */
    val all: List<FluidConditionReason> by lazy {
      listOf(Offline, NoInternet, Roaming, Metered, DataSaver, LowData, NotWifi, NotCharging, PowerSave, Hot)
    }

    /** Il motivo col nome [name], o null: per rileggere un motivo salvato. */
    fun named(name: String): FluidConditionReason? = all.firstOrNull { it.name == name }
  }
}

/**
 * La risposta di [FluidConditions.allows]: si puo' ([allowed]) o si aspetta, con i motivi ([reasons], il primo e' il piu'
 * importante). [forced] e' il «Fallo comunque» gia' dato.
 */
class FluidPermit(
  val reasons: List<FluidConditionReason>,
  val forced: Boolean = false,
) {
  /** Si puo' partire: nessun motivo, o solo motivi superati da «Fallo comunque». */
  val allowed: Boolean get() = reasons.isEmpty() || (forced && reasons.all { it.forceable })

  /** Si aspetta. */
  val waiting: Boolean get() = !allowed

  /** «Fallo comunque» servirebbe: si aspetta solo per motivi che si possono forzare. */
  val canForce: Boolean get() = waiting && reasons.isNotEmpty() && reasons.all { it.forceable }

  /** Il motivo principale, o null. */
  val reason: FluidConditionReason? get() = reasons.firstOrNull()

  override fun toString(): String = if (allowed) "FluidPermit(allowed${if (forced) ", forced" else ""})" else "FluidPermit(waiting $reasons)"

  companion object {
    val Allowed: FluidPermit = FluidPermit(emptyList())
  }
}

/**
 * Le parole dei motivi, di serie in italiano ([english] per l'inglese): [reason] e' il perche' breve («Rete a consumo»),
 * [waitingFor] la frase di chi aspetta («Aspetto il Wi-Fi»). Le usano la pagina dei trasferimenti, la loro notifica e la
 * scena della galleria; un'app le passa tradotte.
 */
data class FluidConditionPhrases(
  val offline: String = "Senza rete",
  val noInternet: String = "La rete non porta a internet",
  val roaming: String = "Sei in roaming",
  val metered: String = "Rete a consumo",
  val dataSaver: String = "Risparmio dati attivo",
  val lowData: String = "«Usa pochi dati» attivo",
  val powerSave: String = "Risparmio energetico attivo",
  val hot: String = "Il telefono è caldo",
  val notWifi: String = "Solo col Wi-Fi",
  val notCharging: String = "Solo in carica",
  val waitNetwork: String = "Aspetto la rete",
  val waitWifi: String = "Aspetto il Wi-Fi",
  val waitCharging: String = "Aspetto la carica",
  val waitCool: String = "Aspetto che si raffreddi",
  val waitPower: String = "Aspetto la fine del risparmio energetico",
) {
  /** Il perche' breve di [reason]. */
  fun reason(reason: FluidConditionReason): String = when (reason) {
    FluidConditionReason.Offline -> offline
    FluidConditionReason.NoInternet -> noInternet
    FluidConditionReason.Roaming -> roaming
    FluidConditionReason.Metered -> metered
    FluidConditionReason.DataSaver -> dataSaver
    FluidConditionReason.LowData -> lowData
    FluidConditionReason.PowerSave -> powerSave
    FluidConditionReason.Hot -> hot
    FluidConditionReason.NotWifi -> notWifi
    FluidConditionReason.NotCharging -> notCharging
    else -> reason.name
  }

  /** Che cosa si aspetta, per il motivo principale di [reasons]: «Aspetto il Wi-Fi». Null senza motivi. */
  fun waitingFor(reasons: List<FluidConditionReason>): String? = when (reasons.firstOrNull()) {
    null -> null
    FluidConditionReason.Offline, FluidConditionReason.NoInternet -> waitNetwork
    FluidConditionReason.Roaming, FluidConditionReason.Metered, FluidConditionReason.DataSaver,
    FluidConditionReason.LowData, FluidConditionReason.NotWifi -> waitWifi
    FluidConditionReason.NotCharging -> waitCharging
    FluidConditionReason.Hot -> waitCool
    FluidConditionReason.PowerSave -> waitPower
    else -> reason(reasons.first())
  }

  companion object {
    /** Le parole inglesi. */
    fun english(): FluidConditionPhrases = FluidConditionPhrases(
      offline = "No network",
      noInternet = "The network has no internet",
      roaming = "You're roaming",
      metered = "Metered network",
      dataSaver = "Data Saver is on",
      lowData = "“Use less data” is on",
      powerSave = "Battery Saver is on",
      hot = "The phone is hot",
      notWifi = "Wi-Fi only",
      notCharging = "Only while charging",
      waitNetwork = "Waiting for a network",
      waitWifi = "Waiting for Wi-Fi",
      waitCharging = "Waiting to charge",
      waitCool = "Waiting to cool down",
      waitPower = "Waiting for Battery Saver to end",
    )
  }
}

/**
 * **Il posto solo** delle condizioni del telefono nel processo: chi le legge (`EngineConditions.install` di `engine-net`)
 * le pubblica qui con [publish], chi le usa le legge da [state] o da [current]. Di serie [FluidConditions.Unknown]: finche'
 * nessuno le legge, nessuno si ferma.
 *
 * Sopra la lettura vera stanno due cose:
 * - [lowData], l'interruttore «Usa pochi dati» dell'app (`FluidConditions.lowData`);
 * - la **simulazione** ([simulate]), per le prove e per l'interruttore di sviluppo della galleria: trasforma la lettura
 *   vera (per esempio «come se la rete costasse»). Non tocca il telefono.
 *
 * Thread-safe: si pubblica da qualsiasi thread.
 */
object FluidDeviceConditions {
  private val lock = Any()
  private var real: FluidConditions = FluidConditions.Unknown
  private var lowDataPreference: Boolean = false
  private var simulation: ((FluidConditions) -> FluidConditions)? = null
  private val _state = MutableStateFlow(FluidConditions.Unknown)

  /** Le condizioni di adesso, con «Usa pochi dati» e la simulazione. */
  val state: StateFlow<FluidConditions> = _state.asStateFlow()

  /** Lo stesso di `state.value`. */
  val current: FluidConditions get() = _state.value

  /** Le condizioni lette davvero, senza simulazione. */
  val measured: FluidConditions get() = synchronized(lock) { real }

  /** Vero se c'e' una simulazione attiva. */
  val simulated: Boolean get() = synchronized(lock) { simulation != null }

  /** «Usa pochi dati» dell'app: l'app lo mette dalle sue impostazioni. */
  var lowData: Boolean
    get() = synchronized(lock) { lowDataPreference }
    set(value) = synchronized(lock) {
      lowDataPreference = value
      recompute()
    }

  /** Pubblica la lettura vera (la chiama `engine-net`). */
  fun publish(conditions: FluidConditions) = synchronized(lock) {
    real = conditions
    recompute()
  }

  /**
   * Simula le condizioni sopra quelle vere ([transform] le riceve e le cambia), o toglie la simulazione con null. Una
   * condizione simulata e' sempre «saputa» ([FluidConditions.known]): la simulazione vale anche prima della prima lettura.
   */
  fun simulate(transform: ((FluidConditions) -> FluidConditions)?) = synchronized(lock) {
    simulation = transform
    recompute()
  }

  /** Il permesso per [work] adesso: vedi [FluidConditions.allows]. */
  fun allows(work: FluidWork, needs: FluidWorkNeeds = FluidWorkNeeds.None, force: Boolean = false): FluidPermit =
    current.allows(work, needs, force)

  /** Torna come all'avvio: nessuna lettura, nessuna simulazione, «Usa pochi dati» spento. Per le prove. */
  fun reset() = synchronized(lock) {
    real = FluidConditions.Unknown
    lowDataPreference = false
    simulation = null
    recompute()
  }

  private fun recompute() {
    val base = if (lowDataPreference && !real.lowData) real.copy(lowData = true) else real
    _state.value = simulation?.invoke(base)?.let { if (it.known) it else it.copy(known = true) } ?: base
  }
}
