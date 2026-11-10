package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.fluid.ClosedProfilePool.Stage
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

/** La macchina a stati dei profili ([ClosedProfilePool]) su una piattaforma finta: ogni transizione e ogni errore. */
class FluidWebProfilesTest {
  private val main = FakeMainThread()
  private val provider = FakeProfileProvider(main)
  private val pool = ClosedProfilePool(provider)

  private fun name(id: String) = ClosedProfilePool.Prefix + id

  /** Chiude il documento di [profile] come fa una chiusura confermata, e fa rispondere la pulizia. */
  private fun recycleAndClean(profile: String) {
    pool.recycle(profile)
    provider.answerClear(profile)
  }

  // ---- isolate ----

  @Test fun firstDocumentGetsANewProfileInUse() {
    val profile = pool.isolate("vista-1")
    assertEquals(name("n1"), profile)
    assertEquals(Stage.InUse, pool.stage(profile))
    assertEquals(profile, provider.assigned["vista-1"])
    assertNull(pool.stage(name("mai-creato")))
  }

  @Test fun withoutMultiProfileIsolateFailsClosedAndTouchesNothing() {
    provider.multi = false
    provider.store += name("avanzo")
    assertThrows(IsolationUnavailable::class.java) { pool.isolate("vista-1") }
    assertTrue(provider.log.toString(), provider.log.isEmpty())
    assertEquals(0, provider.listings)
    assertEquals(0, main.pending)
  }

  @Test fun failedAssignmentLeavesTheProfileRetiredForGood() {
    provider.assignFailure = IllegalStateException("provider")
    assertThrows(IllegalStateException::class.java) { pool.isolate("vista-1") }
    assertEquals(Stage.Retired, pool.stage(name("n1")))
    provider.assignFailure = null
    val next = pool.isolate("vista-2")
    assertEquals(name("n2"), next)
    // Nemmeno una riconsegna tardiva lo rimette in gioco.
    pool.recycle(name("n1"))
    assertEquals(Stage.Retired, pool.stage(name("n1")))
    assertTrue(provider.clearing.isEmpty())
  }

  @Test fun failedAssignmentOfACleanProfileRetiresIt() {
    val first = pool.isolate("vista-1")
    recycleAndClean(first)
    provider.assignFailure = IllegalStateException("provider")
    assertThrows(IllegalStateException::class.java) { pool.isolate("vista-2") }
    assertEquals(Stage.Retired, pool.stage(first))
    provider.assignFailure = null
    assertEquals(name("n2"), pool.isolate("vista-3"))
  }

  // ---- recycle: InUse -> Cleaning -> Clean -> InUse ----

  @Test fun recycledProfileIsReusedOnlyAfterTheCleaningAnswers() {
    val first = pool.isolate("vista-1")
    pool.recycle(first)
    assertEquals(Stage.Cleaning, pool.stage(first))
    assertEquals(listOf("clear $first"), provider.log.filter { it.startsWith("clear") })
    // In pulizia non e' disponibile: il documento che arriva subito ne riceve un altro.
    val second = pool.isolate("vista-2")
    assertNotEquals(first, second)
    provider.answerClear(first)
    assertEquals(Stage.Clean, pool.stage(first))
    assertEquals(first, pool.isolate("vista-3"))
    assertEquals(Stage.InUse, pool.stage(first))
    assertEquals(first, provider.assigned["vista-3"])
  }

  @Test fun cleanProfilesAreHandedOutOldestFirst() {
    val a = pool.isolate("vista-1")
    val b = pool.isolate("vista-2")
    val c = pool.isolate("vista-3")
    pool.recycle(b); pool.recycle(a); pool.recycle(c)
    // L'ordine e' quello delle risposte, non delle chiusure.
    provider.answerClear(c); provider.answerClear(b); provider.answerClear(a)
    assertEquals(listOf(c, b, a), listOf(pool.isolate("vista-4"), pool.isolate("vista-5"), pool.isolate("vista-6")))
    assertEquals(name("n4"), pool.isolate("vista-7"))
  }

  @Test fun cleaningThatNeverAnswersKeepsTheProfileOutForever() {
    val stuck = pool.isolate("vista-1")
    pool.recycle(stuck)
    main.advanceBy(3_600_000)
    val handed = (2..20).map { pool.isolate("vista-$it").also(::recycleAndClean) }
    assertFalse(stuck in handed)
    assertEquals(Stage.Cleaning, pool.stage(stuck))
  }

  @Test fun lateCleaningAnswerMakesTheProfileAvailableThen() {
    val late = pool.isolate("vista-1")
    pool.recycle(late)
    val other = pool.isolate("vista-2")
    recycleAndClean(other)
    assertEquals(other, pool.isolate("vista-3"))
    main.advanceBy(60_000)
    provider.answerClear(late)
    assertEquals(late, pool.isolate("vista-4"))
  }

  @Test fun repeatedCleaningAnswerDoesNotOfferTheProfileTwice() {
    val profile = pool.isolate("vista-1")
    pool.recycle(profile)
    val done = provider.clearing.single().second
    done(); done()
    assertEquals(profile, pool.isolate("vista-2"))
    // Una terza risposta, con il profilo di nuovo in uso, non lo rende disponibile a un'altra vista.
    done()
    assertEquals(Stage.InUse, pool.stage(profile))
    assertEquals(name("n2"), pool.isolate("vista-3"))
  }

  @Test fun recycleIsIgnoredUnlessTheProfileIsInUse() {
    val profile = pool.isolate("vista-1")
    pool.recycle(name("sconosciuto"))
    assertNull(pool.stage(name("sconosciuto")))
    pool.recycle(profile)
    pool.recycle(profile) // in pulizia: non parte una seconda pulizia
    assertEquals(1, provider.clearing.size)
    provider.answerClear(profile)
    pool.recycle(profile) // pulito: resta disponibile, una volta sola
    assertEquals(Stage.Clean, pool.stage(profile))
    assertTrue(provider.clearing.isEmpty())
    assertEquals(profile, pool.isolate("vista-2"))
    assertEquals(name("n2"), pool.isolate("vista-3"))
  }

  // ---- recycle: i percorsi che ritirano ----

  @Test fun withoutDeleteBrowsingDataNoProfileIsEverReused() {
    provider.canClear = false
    val handed = (1..10).map { view ->
      pool.isolate("vista-$view").also { pool.recycle(it); assertEquals(Stage.Retired, pool.stage(it)) }
    }
    assertEquals(10, handed.toSet().size)
    assertTrue(provider.log.none { it.startsWith("clear") })
    // Il ritiro per pulizia mancata non prova nemmeno a eliminare il profilo: e' caricato.
    assertTrue(provider.deleted().isEmpty())
  }

  @Test fun cleaningThatThrowsRetiresTheProfile() {
    val profile = pool.isolate("vista-1")
    provider.clearFailure = IllegalStateException("provider")
    pool.recycle(profile)
    assertEquals(Stage.Retired, pool.stage(profile))
    provider.clearFailure = null
    assertEquals(name("n2"), pool.isolate("vista-2"))
  }

  @Test fun cleaningOfAProfileTheProviderNoLongerHasRetiresIt() {
    val profile = pool.isolate("vista-1")
    provider.store -= profile
    pool.recycle(profile)
    assertEquals(Stage.Retired, pool.stage(profile))
    assertTrue(provider.clearing.isEmpty())
    assertEquals(name("n2"), pool.isolate("vista-2"))
  }

  // ---- retire ----

  @Test fun retiredProfileNeverComesBack() {
    val profile = pool.isolate("vista-1")
    pool.retire(profile)
    assertEquals(Stage.Retired, pool.stage(profile))
    // Il provider rifiuta di eliminare un profilo caricato: l'errore resta qui.
    assertEquals(listOf(profile), provider.deleted())
    assertTrue(profile in provider.store)
    pool.recycle(profile)
    assertEquals(Stage.Retired, pool.stage(profile))
    val handed = (2..10).map { pool.isolate("vista-$it").also(::recycleAndClean) }
    assertFalse(profile in handed)
  }

  @Test fun retireDuringCleaningWinsOverALateAnswer() {
    val profile = pool.isolate("vista-1")
    pool.recycle(profile)
    pool.retire(profile)
    provider.answerClear(profile)
    assertEquals(Stage.Retired, pool.stage(profile))
    assertEquals(name("n2"), pool.isolate("vista-2"))
  }

  @Test fun retireOfACleanProfileTakesItOutOfTheQueue() {
    val a = pool.isolate("vista-1")
    val b = pool.isolate("vista-2")
    recycleAndClean(a); recycleAndClean(b)
    pool.retire(a)
    assertEquals(b, pool.isolate("vista-3"))
    assertEquals(name("n3"), pool.isolate("vista-4"))
  }

  @Test fun retireLetsOnlyTheProvidersRefusalThrough() {
    val profile = pool.isolate("vista-1")
    // Solo il rifiuto del provider (IllegalStateException) e' atteso; un altro errore non viene nascosto,
    // ma il profilo e' gia' ritirato.
    provider.deleteFailure = UnsupportedOperationException("altro")
    assertThrows(UnsupportedOperationException::class.java) { pool.retire(profile) }
    assertEquals(Stage.Retired, pool.stage(profile))
  }

  @Test fun retireWithoutMultiProfileOnlyRecordsTheState() {
    val profile = pool.isolate("vista-1")
    provider.multi = false
    pool.retire(profile)
    assertEquals(Stage.Retired, pool.stage(profile))
    assertTrue(provider.deleted().isEmpty())
  }

  // ---- gli avanzi del processo precedente ----

  @Test fun leftoversArePrunedOneAtATimeAfterTheFirstDocument() {
    val leftovers = (1..4).map { name("avanzo-$it") }
    provider.store += leftovers
    provider.store += "profilo-dell-app"
    provider.store += "Default"
    val first = pool.isolate("vista-1")
    // Il primo documento ha gia' il suo profilo e nessun avanzo e' stato toccato.
    assertEquals(first, provider.assigned["vista-1"])
    assertTrue(provider.deleted().isEmpty())
    main.advanceBy(ClosedProfilePool.PruneStartMillis - 1)
    assertTrue(provider.deleted().isEmpty())
    main.advanceBy(1)
    assertEquals(leftovers.take(1), provider.deleted())
    main.advanceBy(ClosedProfilePool.PrunePauseMillis - 1)
    assertEquals(leftovers.take(1), provider.deleted())
    main.advanceBy(1)
    assertEquals(leftovers.take(2), provider.deleted())
    main.runAll()
    assertEquals(leftovers, provider.deleted())
    assertEquals(ClosedProfilePool.PruneStartMillis + 3 * ClosedProfilePool.PrunePauseMillis, main.now)
    // Restano il profilo di questo processo e quelli che non sono del contenitore.
    assertEquals(setOf("profilo-dell-app", "Default", first), provider.store)
    assertEquals(0, main.pending)
  }

  @Test fun pruningNeverTouchesAProfileOfThisProcess() {
    // Un avanzo con lo stesso nome di un profilo creato qui (con nomi casuali non succede: e' la seconda difesa).
    provider.forcedIds += listOf("uguale-in-uso", "uguale-ritirato", "uguale-in-pulizia", "uguale-pulito")
    val clashing = listOf("uguale-in-uso", "uguale-ritirato", "uguale-in-pulizia", "uguale-pulito").map(::name)
    provider.store += name("avanzo-1")
    provider.store += clashing
    provider.store += name("avanzo-2")
    val inUse = pool.isolate("vista-1")
    val retired = pool.isolate("vista-2")
    val cleaning = pool.isolate("vista-3")
    val clean = pool.isolate("vista-4")
    assertEquals(clashing, listOf(inUse, retired, cleaning, clean))
    provider.canClear = false
    pool.recycle(retired) // ritirato senza passare dal provider
    provider.canClear = true
    pool.recycle(cleaning)
    recycleAndClean(clean)
    main.runAll()
    assertEquals(listOf(name("avanzo-1"), name("avanzo-2")), provider.deleted())
    assertEquals(listOf(Stage.InUse, Stage.Retired, Stage.Cleaning, Stage.Clean), clashing.map(pool::stage))
    assertTrue(provider.store.containsAll(clashing))
  }

  @Test fun leftoversAreListedOnceBeforeTheFirstProfileExists() {
    provider.store += name("avanzo-1")
    val first = pool.isolate("vista-1")
    pool.retire(first)
    repeat(5) { pool.isolate("vista-${it + 2}").also(pool::retire) }
    assertEquals(1, provider.listings)
    main.runAll()
    // Restano i sei profili ritirati in questo processo (il provider rifiuta di eliminarli): se ne va solo l'avanzo.
    assertEquals(6, provider.store.size)
    assertFalse(name("avanzo-1") in provider.store)
    assertEquals(1, provider.deleted().count { it == name("avanzo-1") })
  }

  @Test fun aLeftoverTheProviderRefusesToDeleteDoesNotStopTheOthers() {
    provider.store += listOf(name("avanzo-1"), name("avanzo-caricato"), name("avanzo-3"))
    provider.loaded += name("avanzo-caricato") // caricato da qualcun altro in questo processo
    pool.isolate("vista-1")
    main.runAll()
    assertEquals(listOf(name("avanzo-1"), name("avanzo-caricato"), name("avanzo-3")), provider.deleted())
    assertTrue(name("avanzo-caricato") in provider.store)
    assertFalse(name("avanzo-1") in provider.store)
    assertFalse(name("avanzo-3") in provider.store)
  }

  @Test fun withoutLeftoversNothingIsScheduled() {
    provider.store += "profilo-dell-app"
    pool.isolate("vista-1")
    assertEquals(1, provider.listings)
    assertEquals(0, main.pending)
  }

  @Test fun documentsOpenedWhilePruningAreNotDelayedNorTouched() {
    provider.store += (1..30).map { name("avanzo-$it") }
    val handed = ArrayList<String>()
    handed += pool.isolate("vista-0")
    main.advanceBy(ClosedProfilePool.PruneStartMillis)
    repeat(10) { step ->
      main.advanceBy(ClosedProfilePool.PrunePauseMillis)
      handed += pool.isolate("vista-${step + 1}")
    }
    main.runAll()
    assertEquals(30, provider.deleted().size)
    assertTrue(provider.deleted().none { it in handed })
    assertTrue(handed.all { pool.stage(it) == Stage.InUse })
  }

  // ---- il thread principale ----

  @Test fun everyEntryChecksTheMainThread() {
    provider.store += name("avanzo-1")
    val profile = pool.isolate("vista-1")
    val other = pool.isolate("vista-2")
    pool.recycle(other)
    main.onMain = false
    assertThrows(IllegalStateException::class.java) { pool.isolate("vista-3") }
    assertThrows(IllegalStateException::class.java) { pool.recycle(profile) }
    assertThrows(IllegalStateException::class.java) { pool.retire(profile) }
    assertThrows(IllegalStateException::class.java) { provider.answerClear(other) }
    assertThrows(IllegalStateException::class.java) { main.runAll() } // l'eliminazione di un avanzo
    // Nessuna di quelle chiamate ha cambiato qualcosa.
    assertEquals(Stage.InUse, pool.stage(profile))
    assertEquals(Stage.Cleaning, pool.stage(other))
    assertTrue(provider.deleted().isEmpty())
    assertNull(provider.assigned["vista-3"])
  }

  // ---- la regola intera, su sequenze a caso ----

  @Test fun randomSequencesNeverShareARetiredOrUncleanedProfile() {
    val random = Random(20261110)
    repeat(300) { round ->
      val main = FakeMainThread()
      val provider = FakeProfileProvider(main)
      val pool = ClosedProfilePool(provider)
      provider.canClear = round % 7 != 0
      val live = HashSet<String>()           // profili con una vista viva
      val retired = HashSet<String>()        // ritirati almeno una volta
      val awaiting = HashSet<String>()       // riconsegnati, pulizia non ancora risposta
      var views = 0
      repeat(200) {
        when (random.nextInt(6)) {
          0, 1 -> {
            val profile = pool.isolate("vista-${views++}")
            assertFalse("profilo condiviso fra due viste vive", profile in live)
            assertFalse("profilo ritirato tornato in uso", profile in retired)
            assertFalse("profilo riusato prima della risposta della pulizia", profile in awaiting)
            live += profile
          }
          2 -> live.randomOrNull(random)?.let { profile ->
            live -= profile
            pool.recycle(profile)
            if (provider.canClear) awaiting += profile else retired += profile
          }
          3 -> live.randomOrNull(random)?.let { profile ->
            live -= profile
            pool.retire(profile)
            retired += profile
          }
          4 -> provider.clearing.randomOrNull(random)?.let { (profile, _) ->
            provider.answerClear(profile)
            awaiting -= profile
          }
          else -> main.advanceBy(random.nextInt(500).toLong())
        }
      }
    }
  }

  private fun <T> Collection<T>.randomOrNull(random: Random): T? =
    if (isEmpty()) null else elementAt(random.nextInt(size))
}
