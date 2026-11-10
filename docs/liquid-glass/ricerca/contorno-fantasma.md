# Il contorno che resta dopo una trasformazione di Fluid-physics

Ricerca del 2026-11-10 sul worktree `t3-8663befb` (engine 3.1.2, Compose BOM `2026.03.00` → `ui-graphics` 1.10.5,
`versions.gradle:33`). Sola lettura, nessuna build, nessun dispositivo. Le prove fatte sul PC (misure sullo screenshot,
modello della cache, sovrapposizione) stanno in `artifacts/liquid-glass/bench/contorno/`.

Abbreviazioni: `fp/` = `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluidphysics/`;
`ui/` = `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/`; `pg/` = `sample/src/main/kotlin/dev/antigravity/fluidengine/sample/playground/`.

## In breve

Il contorno è **l'ombra della forma di prima, disegnata dal posto dell'ombra che appartiene alla forma di adesso**.
`PhysicsShadowLayers` tiene due layer di ombra, uno per forma, e li registra una volta sola. La lambda di registrazione
però non disegna una sagoma sua: legge il `Path` e il `Paint` della classe, **condivisi dai due posti** e riscritti a ogni
registrazione (`fp/FluidPhysicsShadow.kt:199-201`, `:282-300`). Quando un posto resta un fotogramma senza essere
disegnato (a riposo se ne disegna uno solo), HWUI ne butta la display list. Alla volta dopo Compose 1.10.5 la rifà da sé
rieseguendo quella lambda (`GraphicsLayer.recreateDisplayListIfNeeded`, verificato nel bytecode), e la lambda disegna la
sagoma che il `Path` condiviso contiene in quel momento, cioè **l'ultima forma registrata**. Il posto del cerchio contiene
così l'ombra scavata del quadrato. A riposo sul cerchio la si disegna nel riquadro del cerchio senza stirarla: un quadrato
di vetro fantasma, grande quanto il preset «Quadrato», con l'angolo in alto a sinistra su quello del riquadro del cerchio,
e senza più l'ombra vera del cerchio. Resta finché quel posto non viene sfrattato da una terza forma.

Lo screenshot lo conferma al pixel (sezione 1). Succede ai livelli `Full` e `Balanced`, non a `Lite`. Colpisce chi va e
torna fra due forme con `morphTo` (Playground, demo pratica, scene «Misure», le app che usano `morphTo`). Nei menu, nella
finestra che si espande e nella barra fra pagine, nelle sequenze normali, il codice non lo fa scattare (sezione 4). La
correzione è di poche righe: ogni posto tiene il proprio `Path` e il proprio `Paint` (sezione 6).

Il solo anello non provato in questa sessione è il comportamento di HWUI. Che un `RenderNode` uscito dall'albero perda la
display list lo so dal sorgente AOSP (`RenderNode::prepareTree` con `MarkAndSweepRemoved` → `onRemovedFromTree` →
`destroyHardwareResources`) e l'ho citato a memoria. È proprio il caso per cui Compose ha scritto
`recreateDisplayListIfNeeded`. La prova sul dispositivo della sezione 7 lo chiude in una sequenza di tre tocchi.

---

## 1. Cosa mostra lo screenshot

Misure fatte su `artifacts/liquid-glass/segnalazioni/2026-11-10-alone-fluid-physics.png` (442 × 340). Gli script sono in
`artifacts/liquid-glass/bench/contorno/` (`circle.py`, `profile.py`, `enhance.py`, `crop.py`, `overlay.py`).

**Il cerchio.** Fit sul bordo chiaro riga per riga: centro (228, 203), raggio 144 px, quindi riquadro da 84 a 372 in x e
da 59 in su in y. Il preset «Cerchio» ha raggio `0,30 · lato corto` del palco (`pg/PlaygroundTab.kt:370`), e il palco è
quadrato (`aspectRatio = 1`). Il lato del palco è quindi 480 px nella scala dello screenshot, e il preset «Quadrato», che
ha lato `0,55 · lato corto` ed è centrato (`pg/PlaygroundTab.kt:371`), ha lato **264 px**.

**Il riquadro.** I profili medi lungo righe e colonne si prendono dove il fondale è a 45°: le righe del fondale si annullano
nella media e resta un segno fermo.

| lato | dove sta il gradino | quadrato centrato (dove stava davvero) | quadrato posato sull'angolo del cerchio |
|---|---|---|---|
| sinistro | x ≈ 83,5 (scuro da 72 a 83, salto a 84) | 96 | **84** |
| alto | y ≈ 58,5 (scuro da 52 a 58, salto a 59) | 71 | **59** |
| destro | x ≈ 347,5 (salto fra 347 e 348, scuro fino a 356) | 360 | **348** |

Il riquadro ha il lato del quadrato (347,5 − 83,5 = 264) e sta **sull'angolo in alto a sinistra del riquadro del cerchio**,
non dove stava il quadrato. È la descrizione dell'utente («a sinistra coincide con il bordo del cerchio, a destra circa
22 px più dentro, in alto poco sotto la cima del cerchio»), con il numero che la spiega: 372 − 348 = 24.
`sovrapposizione.png` mette sul passa-alto le due ipotesi: il rosso (posato sull'angolo) cade sulle righe, l'azzurro
(centrato) no.

**Il profilo.** Su ogni lato il fondale si scurisce andando *verso* il riquadro da fuori, tocca il minimo sul bordo e
torna di colpo al livello del fondale appena dentro (in alto: 152,8 → 148,6 → 153,1; a destra: 156,5 → 148,8 → 155,5).
Non è una riga e nemmeno un bordo di luce, che starebbe *dentro* la sagoma. È un'ombra sfumata scavata sotto la sagoma:
`recordShadow` disegna la forma sfocata e poi la cancella con `BlendMode.Clear` nel contorno esatto
(`fp/FluidPhysicsShadow.kt:292-300`). Anche l'ampiezza torna. Il ruolo `Floating` ha ombra di 20 dp al peso 0,7
(`ui/fluid/GlassMaterial.kt:650-651`), il colore è nero al 16% sul lato chiaro (`:502-503`), e sul bordo una maschera
sfocata copre circa metà: 0,7 · 0,16 · 0,45 ≈ 5%, cioè 7–8 livelli su 156. A destra se ne misurano 7,7.

**L'ombra del cerchio manca.** A destra del cerchio (righe 185–215 e 245–268, dove il fantasma non passa) il fondale resta
piatto entro 1–4 livelli, in uno spazio di 2–3 px che è l'antialias del bordo. In basso a sinistra, fra il bordo del
fantasma (84) e quello del cerchio (≈ 91), non c'è scurimento. Un'ombra del cerchio col peso del fantasma darebbe 7–11
livelli, più forti in basso perché l'ombra scende di un sesto del raggio. Il posto del cerchio, quindi, non disegna il
cerchio. Disegna il quadrato.

Indizio, non necessario alla causa: dentro il cerchio le righe del fondale non si piegano verso il bordo, come farebbe una
lente di 24/40 dp con la cupola. Lo screenshot è probabilmente a `Balanced`. Il meccanismo è lo stesso ai due livelli.

## 2. La catena degli eventi

### 2.1 Chi disegna l'ombra

`fluidPhysicsSurface` disegna la propria ombra fuori dal layer del vetro, in un `drawBehind` che precede `drawBackdrop`
(`fp/FluidPhysicsSurface.kt:294-313`, agganciato a `:346`). Lo tace solo se qualcuno ha dichiarato un'ombra esterna
(`state.externalShadows`, `:301`). Il disegno è `PhysicsShadowLayers.draw` (`fp/FluidPhysicsShadow.kt:213-236`):

- a riposo (`restFormNow() != null`, `fp/FluidPhysicsState.kt:425-432`) prende il layer della forma ferma e lo disegna
  nel suo riquadro, **senza stirarlo** (`drawStretched(layer, rest.frame, rest.frame)`, `:219`; con `sx = sy = 1`
  `:245-246`);
- in viaggio prende prima il layer del **bersaglio**, poi quello della **partenza**, e li stira dal loro riquadro a
  quello della sagoma corrente, sfumati col progresso (`:222-235`).

I layer sono due posti (`Slot`, `:190-197`), creati con `rememberGraphicsLayer()` nella superficie
(`fp/FluidPhysicsSurface.kt:296-297`). `obtain` (`:255-273`) cerca un posto con la stessa forma, raggio e colore. Se lo
trova lo restituisce **senza registrare niente** (`:258-262`). Se no sfratta il posto usato meno di recente e ci registra
la forma nuova (`:264-272`).

### 2.2 La lambda che legge lo stato di tutti

```kotlin
// fp/FluidPhysicsShadow.kt
199  private val paint = Paint()
200  private val clearPaint = Paint().apply { blendMode = BlendMode.Clear }
201  private val path = Path()
...
282    path.rewind()
283    path.addPath(scratchPlan.silhouette, Offset(-frame.left, -frame.top))
...
286    paint.color = color
287    paint.blur(radiusPx)
...
292    slot.layer.record(size) {
293      translate(radiusPx * 2f, radiusPx * 2f + offsetY) {
294        val canvas = drawContext.canvas
295        canvas.drawPath(path, paint)
296        canvas.translate(0f, -offsetY)
297        canvas.drawPath(path, clearPaint)
```

`path` e `paint` sono campi di `PhysicsShadowLayers`, uno per due posti. La lambda li legge **quando viene eseguita**, non
quando viene passata a `record`. Eseguita subito, come pensa il codice, disegna la forma giusta. Rieseguita più tardi,
disegna l'ultima forma che `recordShadow` ha scritto in `path`, in uno qualsiasi dei due posti, dentro un layer che
conserva la taglia e l'origine della forma del posto.

### 2.3 Chi la riesegue

Compose UI 1.10.5 (`androidx.compose.ui:ui-graphics-android:1.10.5`, AAR in cache Gradle, letto con `javap`):

- `GraphicsLayer.record(density, dir, size, block)` salva `block` nel campo `drawBlock` e lo esegue
  (`recordInternal → clipDrawBlock → drawWithChildTracking → drawBlock.invoke`).
- `GraphicsLayer.draw$ui_graphics(canvas, parent)`, prima di tutto, chiama `recreateDisplayListIfNeeded()`: **se
  `impl.getHasDisplayList()` è falso, riesegue `recordInternal()`**, cioè l'ultima lambda passata a `record`.
- `GraphicsLayerV29.getHasDisplayList()` è `RenderNode.hasDisplayList()`.
- `AndroidGraphicsContext.createGraphicsLayer()` in questa versione costruisce `GraphicsLayer(impl)` e basta. Non c'è più
  il `LayerManager` che teneva vivi i layer disegnandoli in una superficie nascosta. Un layer che non si disegna è un
  `RenderNode` senza genitori.

HWUI (sorgente AOSP, citato a memoria): a ogni fotogramma `RenderNode::prepareTree` sincronizza le display list. Un nodo
che il fotogramma prima era disegnato da un genitore e adesso non lo è più perde l'ultimo riferimento: `MarkAndSweepRemoved`
lo segna e, a fine giro, `onRemovedFromTree → destroyHardwareResources` gli mette una display list vuota.
Da lì `hasDisplayList()` è falso. Un nodo registrato e mai disegnato non ha mai avuto un genitore e la conserva.

### 2.4 La sequenza dello screenshot

Playground, ruolo `Floating` con ombra (`pg/PlaygroundStage.kt:93-111`), da riposo sul Cerchio:

1. **Riposo sul Cerchio.** `obtain(C)` registra C nel posto 0. `path` = C. Si disegna il posto 0.
2. **Cerchio → Quadrato** (`morphTo`, `fp/FluidPhysicsState.kt:331-353`). `obtain(Q)` sfratta il posto 1 e ci registra Q,
   e adesso `path` = Q. `obtain(C)` trova il posto 0, ancora nell'albero, e non registra. Si disegnano tutti e due.
3. **Riposo sul Quadrato.** Si disegna solo il posto 1. Il posto 0 esce dall'albero e HWUI ne butta la display list.
4. **Quadrato → Cerchio.** `obtain(C)` trova il posto 0 (`slot.form == C`) e non registra. `drawLayer` → `recreateDisplayListIfNeeded`
   → la lambda del posto 0 si riesegue con `path` = Q, e **il posto del Cerchio ora contiene l'ombra scavata del
   Quadrato**, all'origine (2r, 2r + r/6) di un layer grande quanto il Cerchio. In viaggio quel posto si stira dal riquadro
   del Cerchio a quello della sagoma corrente, col peso `t`: un quadrato d'ombra che viaggia dentro la forma che cambia
   («un alone della figura in trasformazione»).
5. **Riposo sul Cerchio.** `obtain(C)` → posto 0, disegnato nel riquadro del Cerchio con `sx = sy = 1`. Si vede l'ombra
   del Quadrato, lato 264, con l'angolo sull'angolo del riquadro del Cerchio. L'ombra del Cerchio non c'è.

Perché non sparisce: a riposo il posto 0 si disegna a ogni fotogramma, resta nell'albero, e nessuno lo registra di nuovo.
Il loop «Quadrato» del Playground (`pg/PlaygroundTab.kt:127-135`) lo rifà a ogni giro. Va via solo quando una terza forma
sfratta quel posto, oppure quando la superficie lascia la composizione e i layer vengono rilasciati. Con un'altra figura
di mezzo il fantasma è quella figura: Cerchio → Rettangolo → Cerchio lascia il Rettangolo, tagliato a destra dal bordo del
layer del Cerchio. «In altri casi mi capita con altre figure.»

### 2.5 Il modello

`artifacts/liquid-glass/bench/contorno/slot_model.py` rifà `obtain`/`draw` riga per riga (stampa, vittima, ordine
bersaglio-poi-partenza, `alpha ≤ 0,001` non disegna) con le due regole di piattaforma: HWUI scarta chi esce dall'albero,
Compose riesegue la lambda al disegno dopo. Risultati con il codice di oggi:

| sequenza | a riposo |
|---|---|
| Cerchio → Quadrato → Cerchio (chip, o il loop «Quadrato») | **ombra del Quadrato sul Cerchio**, a ogni ritorno |
| Cerchio → Rettangolo → Cerchio | **ombra del Rettangolo sul Cerchio** |
| giro completo coi tocchi (ogni forma nuova, fino al Cerchio) | giusto: ogni bersaglio è nuovo e si registra |
| demo pratica Tasti → Menu → Tasti | **ombra del Menu sui Tasti** |
| guida esterna, un viaggio solo (menu: apertura con oltrepasso, riposo, chiusura) | giusto |
| guida esterna riusata 1 → 2, 2 → 1, 1 → 2 con riposo a 1 | **al terzo viaggio, ombra di 1 su 2** |

Con la correzione (ogni posto ridisegna la propria forma) tutte le righe tornano giuste.

## 3. I candidati esclusi

| candidato | perché no |
|---|---|
| bordo a path (`fp/FluidPhysicsRim.kt`) | il bordo è una fascia **dentro** il contorno (tratto doppio ritagliato da `clipOutline`, `:174-182`), mentre il segno misurato è scuro **fuori** con il gradino dentro. Il layer a riposo si rifà quando cambia la lastra (`:143-147`) e la sua lambda tiene l'`Outline` come valore locale, quindi rieseguita ridisegna la sagoma giusta. Ha un parente del difetto, ma senza contorno: vedi 5.2 |
| anelli (`fp/FluidPhysicsRings.kt`) | geometria pura (accoppiamento dei vertici): non disegnano niente |
| opacità che non torna a zero | il peso dell'ombra a riposo è quello del materiale. Il fantasma ha il peso **pieno** di un'ombra `Floating` (sezione 1), non un residuo che sfuma, e il cerchio non ha la sua |
| ombra a bitmap (`fluidPhysicsShadow`, `PhysicsShadowCache`) | non è usata dal Playground. Dove c'è (la finestra), il bitmap è chiave della taglia di riposo e si stira sulla sagoma di adesso: nessuna forma vecchia |
| clip fermo del layer, cattura, sfocatura, pellicola | a `Full` il clip è il rettangolo del nodo e la sagoma la scolpisce l'alfa dello shader. A `Balanced` il clip è `Outline.Generic(plan.silhouette)` (`fp/FluidPhysicsShape.kt:69-70`). In entrambi i casi un clip vecchio **taglierebbe** il vetro, non disegnerebbe un alone fuori. Una cattura ricampionata o vecchia sposterebbe le righe del fondale. Nessuno dei due dà un'ombra scavata con il lato della forma di prima |
| `Shape`/`Outline` che restano quelli vecchi al passaggio viaggio→riposo | il piano crea un'istanza nuova di `FluidPhysicsSilhouetteShape` a ogni ricostruzione (`fp/FluidPhysicsState.kt:1006`, `:1068`), e a riposo ricostruisce quando cambia la forma (`:522-526`). L'outline del layer serve solo al clip (riga sopra) |
| la demo del Playground | il palco disegna fondale, superficie, bersaglio del tocco e il tratto solo in modalità disegno (`pg/PlaygroundStage.kt:84-160`). Il bersaglio passa `pressState`, quindi `fluidPressable` non disegna la pressione (`ui/fluid/FluidInteractions.kt:144`). Nessuna guida, nessuna forma d'arrivo disegnata sotto |

## 4. Dove si presenta

**Livelli.** L'ombra si crea per ogni livello tranne `Lite`, che esce prima con `physicsFallback`
(`fp/FluidPhysicsSurface.kt:155-157`). Quindi il difetto c'è a **`Full` e a `Balanced`** allo stesso modo, e a **`Lite`
no**: lì non c'è nessuna ombra. Le app pubblicate (3.0.0–3.1.2, a `Balanced`) hanno lo stesso codice dell'ombra:
`PhysicsShadowLayers` è entrato con la 3.0.0 (commit `833369f`, CHANGELOG 3.0.0 riga 216: «stessa ombra di
`glassSurface`… Prima non aveva ombra»). Se l'utente ha visto un contorno **prima** della 3.0.0, è un'altra causa.

**Condizione.** Un posto viene ritrovato in cache dopo essere rimasto almeno un fotogramma senza disegno, e nel frattempo
`recordShadow` ha scritto un'altra forma nel `Path` condiviso. Con `morphTo` è il ritorno alla forma da cui si era
partiti: A → B → A. Con una guida esterna succede quando la stessa coppia di posti serve due guide diverse una dopo l'altra.

| componente | come guida la fisica | il difetto, secondo il codice |
|---|---|---|
| Playground, scheda «Fisica» (`pg/PlaygroundTab.kt`) | `morphTo` | **sì**, a ogni A → B → A (chip, loop «Quadrato» e «Blob»). No nel giro completo coi tocchi |
| demo pratica «In pratica» (`pg/PracticalDemos.kt:101`, sul `PlaygroundStage`) | `morphTo` | **sì**, Tasti → Menu → Tasti e simili |
| scene di «Misure» (`sample/…/measure/MeasureScenes.kt:823-845`, `Floating`) | `morphTo` in loop | **sì**, dal secondo ritorno in poi |
| app che usano `fluidPhysicsSurface` + `morphTo` da sole | `morphTo` | **sì**, stesse condizioni |
| menu che si trasforma (`fp/FluidMorphMenu.kt:474-490`) | `driveExternally` | **no** nelle sequenze normali. La superficie esiste solo finché il menu è a schermo (`isOnScreen`), quindi i layer sono nuovi a ogni apertura. Andata e ritorno usano la stessa guida, e l'ordine bersaglio-poi-partenza lascia nel `Path` la partenza, che è proprio il posto che al ritorno si rifà |
| menu delle azioni e contestuale (`ui/fluid/FluidMenuLayer.kt:366`, `:600`) | `driveExternally`, una voce per apertura | **no**, per le stesse ragioni |
| finestra che si espande (`ui/fluid/FluidGlassModal.kt:1299-1306`) | `driveExternally` | **no** per questa via: chiama `fluidPhysicsShadow` (bitmap), e la superficie tace la propria ombra (`fp/FluidPhysicsSurface.kt:301`) |
| popover di vetro (`ui/fluid/FluidGlassModal.kt:1701-1726`) | `driveExternally`, una voce | **no** nelle sequenze normali |
| barra fra pagine (`ui/page/FluidPageAnchoredBar.kt:195-211`, `:455-459`) | `driveExternally`, guida rifatta per coppia di pagine | **possibile, non dimostrato**. La superficie esiste solo durante il movimento (`if (moving)`) ed è nuova a ogni gesto. Dentro uno stesso gesto che va e torna fra tre pagine con il progresso esattamente a 1 a metà (riga 6 del modello) il caso c'è, ma non so se il dito lo produce davvero |

Se sul tablet il contorno si vede **nei menu o nella finestra** dell'engine, questa causa non lo spiega nelle sequenze che
ho modellato: o c'è una sequenza che non ho visto (riaperture veloci, cambio di voce a menu aperto), o è un'altra causa.
La sessione sul tablet lo deve distinguere con la prova del punto 7.3.

## 5. Perché i test non se ne accorgono, e le tracce nel CHANGELOG

### 5.1 I test

- Nessun test tocca `PhysicsShadowLayers`, `PhysicsRimRenderer` o `PhysicsShadowCache`. Ho cercato i tre nomi e
  `fluidPhysicsSurface` in `engine-ui/src/test` e `sample/src/testDebug`, e non compaiono nei test di Fluid-physics.
- Il difetto vive fra due fotogrammi del `RenderThread`: un nodo che esce dall'albero in `prepareTree`, e una
  riregistrazione al disegno dopo. I test JVM non hanno un `RenderThread`. Robolectric, anche in `GraphicsMode.NATIVE`,
  registra display list ma non fa girare la sincronizzazione dell'albero che le butta, quindi `hasDisplayList()` non
  diventa mai falso. `AllGlassTest` lo dice da sé: «il pixel non si prova qui».
- `RestStateDisciplineTest` prova l'identità del piano e della `Shape`, non cosa contiene un layer registrato.
- Il banco di render (`tools/glassbench/`) non rende bordo e ombra a path.

### 5.2 Un parente nello stesso sottosistema: il bordo a riposo

`PhysicsRimRenderer.drawRestRim` registra con un `paint` di campo (`fp/FluidPhysicsRim.kt:84`) a cui dà un
`RuntimeShader` preso da `SharedRuntimeShaders` (`:166-172`). Quel `RuntimeShader` è **un'istanza per processo**
(`ui/glass/backdrop/RuntimeShaderCache.kt:39-51`), e la sua garanzia è scritta lì: «Nothing holds the live instance across
another surface's set-and-snapshot». Il `paint` del bordo invece la tiene. Il layer del bordo a riposo non si disegna in
viaggio, quindi perde la display list. Al riposo successivo sulla stessa lastra non si registra (`needsRecord` è falso), e
Compose riesegue la lambda con lo shader nello stato lasciato dall'ultima superficie che l'ha usato. Il contorno è giusto
(l'`Outline` è un valore locale). Può essere sbagliata la distribuzione della luce lungo il bordo. Non è il difetto
segnalato, ma è la stessa trappola: la correzione è in 6.2.

### 5.3 Il CHANGELOG

Non c'è traccia di questo difetto. Ci sono tre aloni diversi, già chiusi:

- 1.9.3 (riga 875): l'alone della fusione, cioè `smin` che gonfiava il materiale di k/4 oltre il bordo. Corretto
  spegnendo il raggio di fusione con la compenetrazione.
- 2.12.0 (riga 348): il rettangolo chiaro dentro la notifica, l'ombra di piattaforma della `Surface` trasparente.
- 3.0.0 (riga 276): «via il rettangolo pallido che restava a mezz'aria» nel menu contestuale, un seme fermo che sfumava da
  solo.

Lo schema «registra una volta, poi ridisegna il layer» nasce con la 1.6.0 (riga 961: «i layer di bordo e ombra non si
ri-registrano a ogni draw»). È giusto, purché la lambda registrata sia una funzione del solo layer, e le regole non lo
dicono da nessuna parte.

## 6. La correzione proposta

### 6.1 L'ombra (la causa)

Ogni posto tiene la propria sagoma e il proprio pennello. La lambda li legge dal posto, e il posto li riscrive solo
insieme a una registrazione nuova:

```kotlin
// fp/FluidPhysicsShadow.kt, dentro PhysicsShadowLayers
private class Slot(val layer: GraphicsLayer) {
  var form: FluidForm? = null
  var radiusPx: Float = Float.NaN
  var color: Color? = null
  var usedAt: Long = -1L
  // La sagoma e il pennello di QUESTO posto. Compose riesegue la lambda di `record` quando HWUI butta
  // la display list di un layer rimasto un fotogramma senza disegno: la lambda deve ridisegnare la
  // forma del posto, non l'ultima registrata da chiunque.
  val path = Path()
  val paint = Paint()
}

private fun DrawScope.recordShadow(slot: Slot, form: FluidForm, radiusPx: Float, color: Color) {
  ...
  slot.path.rewind()
  slot.path.addPath(scratchPlan.silhouette, Offset(-frame.left, -frame.top))
  slot.paint.color = color
  slot.paint.blur(radiusPx)
  val silhouette = slot.path
  val shadowPaint = slot.paint
  slot.layer.record(size) {
    translate(radiusPx * 2f, radiusPx * 2f + offsetY) {
      val canvas = drawContext.canvas
      canvas.drawPath(silhouette, shadowPaint)
      canvas.translate(0f, -offsetY)
      canvas.drawPath(silhouette, clearPaint)   // `clearPaint` non cambia mai: condividerlo va bene
      canvas.translate(0f, offsetY)
    }
  }
  ...
}
```

Via i campi `path` e `paint` della classe. `radiusPx`, `offsetY` e `size` sono già valori catturati.

- **Costo**: due `Path` e due `Paint` in più per superficie, una volta sola. Niente per fotogramma, e niente in più per
  registrazione (si riusa il `Path` del posto).
- **Rischi**: a riposo, senza scarti, il disegno è identico al pixel a oggi. Lo scarto di HWUI continua a costare una
  rasterizzazione della maschera sfocata al primo disegno dopo il rientro, come oggi; solo che adesso rasterizza la forma
  giusta. Non cambia l'ordine dei posti né la vittima. Il solo rischio vero è dimenticare un'altra lettura di stato
  condiviso dentro la lambda: con questa riscrittura non ce ne sono.
- **Cosa non fare**: «riregistrare a ogni ritrovamento in cache» per paura dello scarto. Compose non espone
  `hasDisplayList` e il contatore `stamp` conta i disegni, non i fotogrammi. Sarebbe una regola fragile che paga la maschera
  sfocata a ogni viaggio. La lambda pura rende lo scarto innocuo, ed è quello che Compose si aspetta.

### 6.2 Il bordo (il parente)

In `drawRestRim` la creazione dello shader entra **dentro** la lambda di `record`, con forma, taglia e densità catturate
come valori. Così ogni riesecuzione rimette gli uniform della propria lastra subito prima di disegnare, e torna il patto
«set-and-snapshot» di `RuntimeShaderCacheImpl`. Costo: nessuno in più alla registrazione normale. Rischio: nessuno
visibile, perché lo shader è lo stesso e cambia solo il momento in cui si impostano gli uniform.

### 6.3 Il test che l'avrebbe fermato

Due livelli, perché il difetto ha una metà di logica e una metà di piattaforma.

1. **Robolectric (`GraphicsMode.NATIVE`), in `engine-ui/src/test/…/fluidphysics/PhysicsShadowSlotsTest.kt`.** Con la
   correzione la sagoma di un posto è un oggetto del posto, quindi la si può interrogare (un accessor `internal` di test,
   `slotPathBounds(form)`). Sequenza: `draw` a riposo su C, poi un viaggio C → Q, riposo su Q, viaggio Q → C, riposo su C.
   Asserzione: dopo ogni registrazione la sagoma del posto di C ha i limiti di C (traslati all'origine) e quella del posto
   di Q i limiti di Q. Il nome del test dice il contratto: «la lambda di un posto, rieseguita dopo che l'altro ha
   registrato, ridisegna la propria forma». Contro il codice di oggi il test equivalente fallisce subito, perché le due
   lambda leggono lo **stesso** `path`, e lo stesso vale per il `paint`.
2. **Test strumentale sul dispositivo** (il solo che vede HWUI). Una `fluidPhysicsSurface` `Floating` su fondo bianco
   pieno, `snapTo(C)`, `morphTo(Q)`, attesa del riposo, `morphTo(C)`, attesa del riposo, cattura della radice
   (`captureToImage`). Asserzioni: (a) 2 px fuori dal lato destro del «quadrato posato sull'angolo di C», all'altezza in cui
   quel punto è fuori dal cerchio, il pixel è il bianco del fondo ± 1 livello; (b) 4 px sotto il fondo del cerchio il pixel
   è più scuro del fondo di almeno 4 livelli (l'ombra del cerchio c'è). Senza correzione falliscono tutte e due. Va nella
   suite che gira nelle sessioni sul dispositivo, e nella lista di S2.

Una regola per `ref/regole.md`: **la lambda passata a `GraphicsLayer.record` è una funzione del solo layer.** Compose la
riesegue da sé quando HWUI butta la display list (un layer non disegnato per un fotogramma, un'app tornata dallo sfondo),
quindi non può leggere campi riscritti da altri: `Path`, `Paint` e `RuntimeShader` condivisi sono vietati, i valori si
catturano.

## 7. La prova sul dispositivo

Una sequenza di tre tocchi conferma o smentisce senza toccare il codice. Livello `Auto`, `Full` o `Bilanciato`, tema
chiaro (l'ombra pesa di più), scheda «Fisica», loop «No».

1. **Stato iniziale.** Aprire la scheda e aspettare il riposo sul Cerchio. Screenshot: nessun riquadro, e **sotto e a
   destra del cerchio un'ombra** (fondale più scuro di qualche livello, più forte in basso).
2. **Il ritorno.** Chip «Quadrato», attendere il riposo, chip «Cerchio», attendere il riposo. Screenshot. Se la causa è
   questa: un riquadro arrotondato con il **lato del Quadrato** (0,55 del lato del palco) e l'**angolo in alto a sinistra
   sull'angolo del riquadro del cerchio** (non centrato), e **nessuna ombra sotto il cerchio**. Il riquadro resta dopo
   minuti di attesa.
3. **I controlli**, che la smentirebbero se andassero diversamente:
   - Chip «Rettangolo», poi «Cerchio»: il riquadro diventa il Rettangolo, sempre con l'angolo su quello del cerchio,
     tagliato a destra in verticale dal bordo del layer.
   - Giro completo toccando il pezzo, Quadrato → … → Tre pezzi → Cerchio: al riposo finale **nessun** riquadro, e l'ombra
     del cerchio c'è.
   - Livello «Lite»: nessun riquadro e nessuna ombra.

Il valore da leggere è la posizione. Ogni altra ipotesi (clip vecchio, bordo, cattura, guida della demo) metterebbe il
contorno **dove stava** la forma di prima, cioè centrato sul palco. Questa lo mette sull'angolo della forma di adesso, con
la taglia di quella di prima. Se il passo 2 mostra il riquadro centrato, la causa è un'altra e questo documento è
smentito.

Per i menu e la finestra (sezione 4), la stessa sessione apre e chiude tre volte di fila lo stesso menu e la stessa
finestra, e fotografa a riposo aperto. Se lì c'è un contorno, va misurato come in sezione 1: lato, angolo e profilo
(ombra scavata o riga) dicono se è questo meccanismo in una sequenza che non ho modellato o un'altra cosa.

## 8. Cosa deve fare il motore nuovo (piano 3.5, 3.7, 3.8, F1B)

Il piano sostituisce `PhysicsShadowLayers` e `fluidPhysicsShadow` con l'ombra unica (3.5, 3.8): in viaggio nello shader,
a riposo «due maschere sfumate in un nodo solo» in layer, registrate in sola alfa con colore e alfa al disegno. In
viaggio il problema sparisce per costruzione: lo shader valuta il campo dagli uniform del piano, e non c'è un layer per
forma da ritrovare in cache. A riposo il rischio torna, ed è anche più largo, perché il piano moltiplica i layer che si
spengono e si riaccendono. F1B.2, che scrive `fp/FluidPhysicsShadow.kt`, `fp/FluidPhysicsRim.kt` e la luce a riposo, deve
tenere quattro cose:

1. **Lambda di `record` pure** per le maschere dell'ombra, lo strato della luce a riposo (3.5) ed eventuali layer della
   luce ferma (F7, leva 1). Si cattura per valore la geometria, il pennello e gli uniform. Uno shader di processo
   (`SharedRuntimeShaders`) si imposta **dentro** la lambda, mai tenuto in un `Paint` di campo.
2. **Il passaggio riposo↔palco (3.7)** è il caso peggiore: i pezzi coinvolti «spengono il proprio vetro» per tutto il
   viaggio, quindi le loro maschere e la loro luce a riposo restano senza disegno per decine di fotogrammi e perdono di
   sicuro la display list. Alla riaccensione si rifanno dalla lambda, e la regola 1 è l'unica cosa che le fa tornare
   uguali.
3. **Una cache «forma → layer»** (se F1B ne tiene una per non rifare le maschere a ogni ritorno) ha come chiave tutto ciò
   che la lambda disegna, e la lambda non legge niente fuori dalla chiave.
4. **Parità e ritorno** fra i test di F1B.6 e le voci di S2: oltre allo «scalino viaggio→riposo dell'ombra» già in lista,
   l'andata e ritorno A → B → A con l'ombra a riposo confrontata al pixel con quella del primo riposo su A, e lo stesso per
   la luce a riposo. È il test strumentale di 6.3 generalizzato, e va anche nella pagina «Parità».

Finché F1B non arriva, la correzione di 6.1 è piccola e indipendente e si può portare nella 3.1.x. Le app a `Balanced` hanno
il difetto quanto il worktree a `Full`.

---

## 9. Correzione fatta (2026-11-10)

Decisione dell'orchestratore: si corregge subito nel disegno di oggi, perché resta come ripiego sotto Android 13 e le app
lo mostrano. Nessun commit (lo fa l'orchestratore).

### 9.1 Cosa è cambiato

- **Ombra** (`fp/FluidPhysicsShadow.kt`). Ogni `Slot` ha ora il proprio `path` e il proprio `paint`. `recordShadow` li
  riempie e la lambda di `record` cattura solo loro, lo scavo (`clearPaint`, che non cambia dopo la costruzione) e
  valori (`radiusPx`, `offsetY`). Sono spariti i campi `path` e `paint` della classe. Il KDoc della classe e del posto
  dice perché: una lambda di `record` può essere rieseguita dalla piattaforma molto dopo, quindi dipende solo da ciò che
  appartiene a quel layer. Non cambia niente a riposo né per fotogramma: due `Path` e due `Paint` in più per
  superficie, creati una volta.
- **Bordo a riposo** (`fp/FluidPhysicsRim.kt`, `drawRestRim`). Il rischio è reale, e la lettura lo conferma.
  `HighlightStyle.Default.createShader` (`bd/highlight/HighlightStyle.kt:93-113`) restituisce l'istanza di processo di
  `SharedRuntimeShaders` dopo averci scritto taglia, raggi, colore e angolo. Il `paint` del bordo la teneva dalla
  registrazione in poi, e il layer del bordo a riposo perde la display list a ogni viaggio, perché in viaggio si disegna
  il bordo «in viaggio» o quello della goccia, non quel layer. Al riposo successivo sulla **stessa** lastra
  (`needsRecord` falso) Compose rieseguiva la lambda con gli uniform dell'ultimo che aveva disegnato un riflesso: il
  bordo stesso in viaggio, la goccia, o un'altra lastra del processo. Ora colore, spessore, sfocatura e shader si
  impostano dentro la lambda, e si registrano nello stesso istante. Il contorno del bordo era già giusto, perché
  l'`Outline` è un valore locale: cambia solo la luce. Casi in cui la riesecuzione accade davvero: un ritorno indietro
  predittivo annullato su una finestra (il progresso scende sotto 1 e torna a 1), l'oltrepasso della molla che
  oscilla attorno all'1, e l'app che torna dallo sfondo con un pannello aperto.

### 9.2 Il test

`engine-ui/src/test/kotlin/dev/antigravity/fluidengine/ui/fluidphysics/PhysicsShadowSlotsTest.kt`, JVM puro, senza
Robolectric (`engine-ui` non lo ha, e `build.gradle` non si tocca).

- **Come prova la riesecuzione.** Due `GraphicsLayer` **veri** di Compose 1.10.5 costruiti su un doppio di
  `GraphicsLayerImpl` (interno, per riflessione) che dice di non avere display list e di non saper rendere in software,
  disegnati su un canvas non accelerato. In quel caso `GraphicsLayer.draw` **riesegue la lambda registrata a ogni
  disegno**: è la riesecuzione tardiva del dispositivo, solo che qui avviene sempre. Il canvas di piattaforma annota quale
  `android.graphics.Path` riceve ogni `drawPath`. Lo stato fisico si guida da fuori (`driveExternally`, progresso in un
  campo del banco), quindi riposo e viaggio sono un numero, senza orologio.
- **Cosa asserisce.** Sul JVM il contenuto di un `Path` non si legge, quindi asserisce la proprietà sulla struttura: dopo
  Cerchio → Quadrato → Cerchio, il riposo sul Cerchio non disegna nessun `Path` disegnato a riposo sul Quadrato, e
  ridisegna esattamente quello del primo riposo sul Cerchio. A metà del ritorno, partenza e arrivo disegnano due
  sagome distinte.
- **Un ostacolo dell'ambiente.** `GraphicsLayer.<clinit>` fa `Build.FINGERPRINT.lowercase()`, e negli stub quel campo è
  null. Il test gli dà un valore (non «robolectric») con `sun.misc.Unsafe` prima di caricare la classe. Se un
  aggiornamento di Compose rinomina le interne usate dal doppio, il test si rompe ad alta voce: va riscritto il doppio,
  non tolto il test.

Uscita **prima** della correzione (`:engine-ui:testDebugUnitTest --tests …PhysicsShadowSlotsTest`, codice di oggi;
rapporto in `artifacts/liquid-glass/bench/contorno/test-prima-della-correzione.xml`): 2 test, **2 falliti** sulle
asserzioni.

```
inFlightTheTwoSlotsDrawTwoSilhouettes FAILED
  AssertionError: a meta' del ritorno partenza e arrivo disegnano lo stesso Path: un posto mostra la forma
  dell'altro (disegnati 4, sagome distinte 1)
backOnTheCircleTheShadowRedrawsTheCircleNotTheSquare FAILED
  AssertionError: tornata sul Cerchio, l'ombra ridisegna il Path che ha registrato il Quadrato: e' il contorno
  fantasma (lambda di record che legge una sagoma condivisa fra i posti)
```

Uscita **dopo** la correzione: vedi 9.3.

Il bordo non ha un test sul JVM: lo shader del riflesso esiste solo da API 33 (`isRuntimeShaderSupported()` è falso con
gli stub), quindi il ramo corretto non gira. Si verifica sul dispositivo (9.4).

### 9.3 Verifica

Con lo script del brief (`build-gradle.ps1`, posto `lg-contorno`):

| comando | codice | esito |
|---|---|---|
| `:engine-ui:testDebugUnitTest --tests …PhysicsShadowSlotsTest` | di oggi | 2 test, **2 falliti** sulle asserzioni (sopra). Un primo giro era caduto prima, sul banco (`Build.FINGERPRINT` null): corretto il banco, non il codice |
| `:engine-ui:testDebugUnitTest` | corretto | **verde**: 128 classi, **1413 test, 0 falliti, 0 saltati**, compreso `PhysicsShadowSlotsTest` 2 su 2 (rapporto in `artifacts/liquid-glass/bench/contorno/test-dopo-la-correzione.xml`). 71 s |

`:sample:testDebugUnitTest` non serve: il test sta in `engine-ui`, e le due correzioni non cambiano nessuna API.

### 9.4 Da guardare sul dispositivo

- **Ombra** (la prova della sezione 7): Fisica, tema chiaro, Cerchio → Quadrato → Cerchio, screenshot a riposo. Atteso:
  nessun riquadro, e l'ombra del cerchio presente sotto e a destra. Controllo: anche Cerchio → Rettangolo → Cerchio e il
  loop «Quadrato» per un minuto.
- **Bordo**: aprire una finestra che si espande, cominciare un ritorno indietro predittivo e annullarlo, poi
  fotografare il bordo a riposo. Altra prova: mandare l'app in background con la finestra aperta, aspettare,
  riprenderla. Atteso: il filo del bordo identico a quello di prima del gesto, chiaro in alto e in basso e spento sui
  fianchi, senza archi fuori posto.

### 9.5 Lo stesso schema altrove (non corretto: fuori perimetro)

Ho cercato ogni `GraphicsLayer.record` e `recordLayer` dell'engine e ho letto che cosa legge la lambda.

| dove | che cosa legge la lambda | rischio | effetto se rieseguita |
|---|---|---|---|
| `bd/highlight/HighlightModifier.kt:206-215` (anello di ogni `glassSurface`), con `configurePaint` a `:254-297` | il `paint` del nodo, a cui `configurePaint` dà **fuori dalla lambda** lo shader di processo (`SharedRuntimeShaders`) | **medio**: è lo stesso difetto del bordo appena corretto, su ogni lastra di vetro dell'engine | se il layer dell'anello perde la display list (nodo che smette di disegnarsi, superfici nascoste a intensità zero, app tornata dallo sfondo) e il nodo non deve registrare, l'anello si rifà con taglia e raggi di un'altra superficie: archi chiari fuori posto. Correzione: chiamare `configurePaint` dentro la lambda |
| `bd/backdrops/LayerBackdropModifier.kt:109` (sorgente del fondale) | `this@draw`, il `ContentDrawScope` di un passaggio di disegno passato | **medio-basso**, teorico | il layer della sorgente non si disegna a schermo, solo dentro le catture del vetro. Se nessun vetro lo campiona per un fotogramma, perde la display list. Rieseguita fuori dal suo passaggio, la lambda chiama `drawContent()` su uno scope che Compose condivide nella finestra: disegna il contenuto del nodo in disegno in quel momento, oppure lancia, e Compose inghiotte l'eccezione lasciando il layer vuoto (vetro senza fondale) finché la pagina non si ridisegna |
| `ui/fluid/FluidGlassModal.kt:587` (istantanea della riga che si espande, ridisegnata dall'overlay a `:1371`) | `this@drawWithContent` | **medio-basso** | stesso meccanismo: se l'istantanea resta un fotogramma senza disegno (finestra aperta a riposo) e riappare nel ritorno, la riesecuzione è fuori passaggio, e l'istantanea esce vuota o con il contenuto sbagliato in chiusura. `:1371` ha già un `runCatching`. Da guardare: chiudere una finestra rimasta aperta a lungo e controllare che la riga riappaia lungo il ritorno |
| `ui/fluid/FluidGlassModal.kt:2276` (anteprima del menu contestuale) | `this@drawWithContent` | basso | registrata e disegnata nello stesso draw, quindi resta nell'albero finché il nodo si disegna |
| `bd/GlassNesting.kt:345` (materiale di un contenitore) | `this@draw` | basso | disegnato a schermo nello stesso draw. Lo stesso caveat della sorgente vale se il nodo smette di disegnarsi mentre un vetro annidato lo campiona |
| `bd/shadow/ShadowModifier.kt:144` e `glassShadow` (`ui/fluid/GlassMaterial.kt:1603` → `ShadowElement`) | il `paint` del nodo, configurato solo alla registrazione; l'`Outline` come valore; `ShadowMaskPaint` costante | basso | ridisegna la stessa cosa. Caveat: un `Outline.Generic` il cui `Path` appartiene a una `Shape` che lo riscrive (per esempio la sagoma del piano di Fluid-physics) cambierebbe sotto la lambda. Oggi nessuno lo passa: `fluidPhysicsSurface` chiama `drawBackdrop` con `shadow = null` |
| `bd/shadow/InnerShadowModifier.kt:188` | come sopra; `clipPath` del nodo riscritto da `clipOutline` a ogni chiamata, dentro e fuori | basso | ridisegna la stessa cosa |
| `bd/DrawBackdropModifier.kt:824` (la cattura) | campi del nodo: `padding`, `effectiveScale`, `sampledBackdrop`, coordinate | basso | rieseguita è una cattura nuova della pagina di adesso. Un cambio di padding sporca la cattura e uno di scala cambia la taglia, quindi la ri-registrazione arriva comunque. Non passa dalla contabilità (`sampled`, `recordedSourceOffsets`) |
| `bd/DrawBackdropModifier.kt:922` (export del materiale) | lambda e stati correnti della superficie | basso | ridisegna il materiale di adesso, che è quello che un export deve mostrare |

Nel pacchetto `fluidphysics/` le sole registrazioni di layer sono l'ombra e il bordo, tutte e due corrette. La regola
proposta in 6.3 per `ref/regole.md` resta da scrivere (fuori perimetro): **la lambda passata a `GraphicsLayer.record`
è una funzione del solo layer.**

---

### Prove e file di questa ricerca

- `artifacts/liquid-glass/bench/contorno/circle.py`: fit del cerchio nello screenshot.
- `artifacts/liquid-glass/bench/contorno/profile.py`: profili medi lungo righe e colonne (le tabelle della sezione 1).
- `artifacts/liquid-glass/bench/contorno/enhance.py`, `crop.py`, `hp.png`, `tl.png`, `tr.png`: passa-alto e ingrandimenti
  degli angoli.
- `artifacts/liquid-glass/bench/contorno/overlay.py`, `sovrapposizione.png`: previsione contro misura (rosso: quadrato
  posato sull'angolo del cerchio; azzurro: quadrato centrato; verde: cerchio).
- `artifacts/liquid-glass/bench/contorno/slot_model.py`: il modello dei due posti con lo scarto di HWUI e la
  riregistrazione di Compose, con e senza correzione.
- Bytecode di Compose: `ui-graphics-android-1.10.5` dalla cache Gradle, `javap -c` su `GraphicsLayer`,
  `GraphicsLayer$clipDrawBlock$1`, `GraphicsLayerV29`, `AndroidGraphicsContext` (estratti nello scratchpad della sessione,
  non nel repo).
