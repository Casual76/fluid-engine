package dev.antigravity.fluidengine.search

import kotlin.random.Random

/**
 * Un documento di prova e l'app finta a cui appartiene (le prove e la galleria lo mettono nell'indice di quell'app).
 */
class FluidSearchSample(val app: String, val document: FluidSearchDocument)

/**
 * I dati di prova della ricerca (F8.22), tutti inventati: note, mail, comunicazioni della scuola, foto con la loro
 * descrizione e conversazioni con l'assistente, in italiano, come li avrebbero Pampa Notes, Pampa Mail, ClasseViva,
 * MyCloud Gallery e PampAI. Servono alle prove (errori di battitura, accenti, plurali, pertinenza, significato) e alle
 * pagine di prova; un'app non li usa.
 */
object FluidSearchSamples {
  /** Le app finte, con il loro nome. */
  val apps: Map<String, String> = linkedMapOf(
    "notes" to "Pampa Notes",
    "mail" to "Pampa Mail",
    "classeviva" to "ClasseViva",
    "gallery" to "MyCloud Gallery",
    "pampai" to "PampAI",
  )

  /** I tipi, con il nome del gruppo nei risultati. */
  val kinds: Map<String, String> = linkedMapOf(
    "nota" to "Note",
    "trascrizione" to "Trascrizioni",
    "allegato" to "Allegati",
    "mail" to "Mail",
    "comunicazione" to "Comunicazioni",
    "compito" to "Agenda",
    "voto" to "Voti",
    "foto" to "Foto",
    "conversazione" to "Conversazioni",
  )

  /** Lo schema di tutti gli indici di prova: titolo (peso 3), testo, persone (peso 2). */
  val schema: FluidSearchSchema = FluidSearchSchema(
    name = "prova",
    version = 1,
    fields = listOf(
      FluidSearchField("title", weight = 3f),
      FluidSearchField("body"),
      FluidSearchField("people", weight = 2f),
    ),
  )

  private const val Day = 86_400_000L

  /** Il 25 novembre 2026 alle 9, il «adesso» dei dati di prova. */
  const val Now: Long = 1_795_597_200_000L

  private fun doc(app: String, id: String, kind: String, title: String, body: String, daysAgo: Int, people: String = "") =
    FluidSearchSample(
      app,
      FluidSearchDocument(
        id = id,
        kind = kind,
        fields = buildMap {
          put("title", title)
          put("body", body)
          if (people.isNotEmpty()) put("people", people)
        },
        timestamp = Now - daysAgo * Day,
        link = "pampa-prova://$app/$id",
      ),
    )

  /** I 32 documenti di prova. */
  val all: List<FluidSearchSample> = listOf(
    doc("notes", "n1", "nota", "Riunione di lunedì",
      "Il verbale della riunione con le decisioni sul calendario delle uscite didattiche e sulla gita di primavera.", 2),
    doc("notes", "n2", "nota", "Da fare questa settimana",
      "Preparare l'ordine del giorno per la riunione dei genitori, comprare il regalo per Giulia, chiamare l'idraulico.", 1),
    doc("notes", "n3", "nota", "Ciambellone della nonna",
      "Tre uova, 250 g di farina, 200 g di zucchero, un bicchiere di latte e la scorza di un limone. Forno a 180 gradi per 40 minuti.", 40),
    doc("notes", "n4", "nota", "Domande per il controllo",
      "Chiedere dell'apparecchio per i denti di Marco e se serve un'altra pulizia prima dell'estate.", 5),
    doc("notes", "n5", "nota", "Idee per le vacanze",
      "Una settimana in Sardegna a luglio, oppure i Paesi Bassi in bicicletta. Controllare i traghetti da Livorno.", 20),
    doc("notes", "n6", "nota", "Lezione di storia: la Rivoluzione francese",
      "Le cause: crisi finanziaria, Stati generali, presa della Bastiglia il 14 luglio 1789.", 12),
    doc("notes", "n8", "nota", "Città da visitare",
      "Perché non Matera? Le città più belle secondo la guida: Matera, Lecce, Urbino.", 30),
    doc("notes", "n9", "trascrizione", "Registrazione del 18 novembre",
      "Allora la riunione è spostata a giovedì, alle cinque, nell'aula magna.", 7),
    doc("notes", "n10", "allegato", "Ordine del giorno.pdf, pagina 2",
      "Varie ed eventuali della riunione di classe; elezione dei rappresentanti.", 7),
    doc("mail", "m1", "mail", "Conferma appuntamento del 28/11",
      "Gentile cliente, le confermiamo l'appuntamento per l'igiene e il controllo di venerdì 28 novembre alle 16:30. Via Roma 12, Pisa.",
      3, people = "Studio Odontoiatrico Rossi"),
    doc("mail", "m2", "mail", "Fattura n. 341",
      "In allegato la fattura per l'igiene e il controllo di ottobre.", 25, people = "Studio Odontoiatrico Rossi"),
    doc("mail", "m3", "mail", "Bolletta dell'energia elettrica",
      "La bolletta di novembre è di 84,20 euro, con scadenza il 15 dicembre. Puoi pagarla con l'addebito diretto.", 4,
      people = "Servizio clienti energia"),
    doc("mail", "m4", "mail", "Carta d'imbarco",
      "Volo Pisa – Parigi Charles de Gaulle, sabato 6 dicembre alle 7:05, posto 14C. L'imbarco chiude 20 minuti prima.", 1,
      people = "Compagnia aerea"),
    doc("mail", "m5", "mail", "Iscrizione al corso di nuoto",
      "La piscina comunale riapre le iscrizioni ai corsi per ragazzi dal 1° dicembre.", 6, people = "Piscina comunale"),
    doc("mail", "m6", "mail", "Newsletter della biblioteca",
      "Questo mese: incontro con l'autrice e i nuovi arrivi di narrativa.", 9, people = "Biblioteca civica"),
    doc("mail", "m7", "mail", "Ritiro del pacco",
      "Il tuo pacco è in consegna oggi tra le 10 e le 13. Se non sei in casa lo lasceremo all'edicola di via Garibaldi.", 0,
      people = "Corriere espresso"),
    doc("mail", "m8", "mail", "Prenotazione del ristorante",
      "Tavolo per quattro sabato sera alle 20:30 alla Trattoria da Bruno.", 2, people = "Trattoria da Bruno"),
    doc("classeviva", "c1", "comunicazione", "Uscita anticipata 28/11",
      "Lo studente esce alle 11:10 per una visita odontoiatrica.", 3),
    doc("classeviva", "c2", "comunicazione", "Circolare n. 87: sciopero del personale",
      "Venerdì 5 dicembre potrebbero non essere garantite le lezioni.", 2),
    doc("classeviva", "c3", "compito", "Compiti di algebra",
      "Esercizi a pagina 112, numeri dal 4 al 9: equazioni di secondo grado.", 1),
    doc("classeviva", "c4", "compito", "Verifica di inglese",
      "Giovedì verifica sul present perfect e sul lessico dei viaggi.", 2),
    doc("classeviva", "c5", "voto", "Voto di storia: 8",
      "Interrogazione sulla Rivoluzione francese.", 10),
    doc("classeviva", "c6", "comunicazione", "Colloqui con i genitori",
      "I colloqui pomeridiani si tengono il 10 dicembre dalle 15 alle 18.", 8),
    doc("gallery", "g1", "foto", "Spiaggia di Castiglioncello",
      "Tramonto sul mare, agosto 2025. Album: Estate.", 110),
    doc("gallery", "g2", "foto", "Ricevuta dello studio",
      "Foto di una ricevuta su carta nella sala d'attesa.", 25),
    doc("gallery", "g3", "foto", "Torta di compleanno",
      "Candeline e cioccolato, il compleanno di Giulia.", 60),
    doc("gallery", "g4", "foto", "Duomo di Firenze",
      "La cupola del Brunelleschi vista da Piazzale Michelangelo.", 200),
    doc("gallery", "g5", "foto", "Gatto sul divano",
      "Il micio dorme sulla coperta a quadri.", 15),
    doc("gallery", "g6", "foto", "Neve all'Abetone",
      "Pista da sci e seggiovia, gennaio.", 310),
    doc("pampai", "p1", "conversazione", "Come si cucina il risotto ai funghi",
      "Tosta il riso, sfuma col vino bianco, aggiungi il brodo un mestolo alla volta.", 14),
    doc("pampai", "p2", "conversazione", "Ripasso per la verifica di inglese",
      "Esempi di frasi al present perfect: I have already finished my homework.", 3),
    doc("pampai", "p3", "conversazione", "Consigli per dormire meglio",
      "Evitare gli schermi prima di dormire e tenere la camera fresca.", 18),
  )

  /** I documenti dell'app [app]. */
  fun of(app: String): List<FluidSearchDocument> = all.filter { it.app == app }.map { it.document }

  /** Tutti i documenti, in un indice solo. */
  val documents: List<FluidSearchDocument> get() = all.map { it.document }

  /**
   * Le dieci domande per significato delle prove, con il documento che deve arrivare fra i primi tre. I vettori del
   * modello vero (EmbeddingGemma 2 270M) per documenti e domande stanno nelle risorse delle prove.
   */
  val meaningQueries: List<Pair<String, String>> = listOf(
    "la mail del dentista" to "m1",
    "quando scade la luce" to "m3",
    "compiti di matematica" to "c3",
    "foto al mare" to "g1",
    "volo per la Francia" to "m4",
    "ricetta di un dolce" to "n3",
    "gita scolastica" to "n1",
    "sciopero della scuola" to "c2",
    "dove lasciano il corriere" to "m7",
    "cena con gli amici" to "m8",
  )

  private val subjects = listOf("riunione", "compito", "verifica", "spesa", "viaggio", "lezione", "progetto", "visita", "festa",
    "allenamento", "colloquio", "bolletta", "ricetta", "consegna", "prenotazione", "esame", "gita", "concerto", "partita", "corso")
  private val details = listOf("con i genitori", "di matematica", "in biblioteca", "al mare", "a Firenze", "dal dentista",
    "con Giulia", "di inglese", "in palestra", "al ristorante", "della classe", "in ufficio", "dal medico", "con la nonna",
    "in montagna", "al museo", "di storia", "con gli amici", "in piscina", "alla stazione")
  private val actions = listOf("Ricordarsi di portare", "Preparare", "Controllare", "Chiedere informazioni su", "Prenotare",
    "Scrivere a Marco per", "Comprare", "Rimandare", "Confermare", "Pagare")
  private val objects = listOf("i documenti", "il quaderno", "la ricevuta", "le foto", "il biglietto", "la tessera", "il regalo",
    "gli appunti", "la cartella", "il libro di testo", "le chiavi", "il certificato")
  private val days = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica")

  /**
   * [count] note inventate per le misure (la prima indicizzazione, il tempo di una ricerca): titoli e testi di poche
   * righe, con parole che si ripetono come in un quaderno vero. Sempre le stesse per lo stesso [seed].
   */
  fun generated(count: Int, seed: Int = 22): List<FluidSearchDocument> {
    val random = Random(seed)
    return List(count) { i ->
      val subject = subjects[random.nextInt(subjects.size)]
      val detail = details[random.nextInt(details.size)]
      val day = days[random.nextInt(days.size)]
      val sentences = buildString {
        repeat(2 + random.nextInt(4)) {
          if (isNotEmpty()) append(' ')
          append(actions[random.nextInt(actions.size)]).append(' ').append(objects[random.nextInt(objects.size)])
            .append(" per la ").append(subjects[random.nextInt(subjects.size)]).append(' ')
            .append(details[random.nextInt(details.size)]).append(", ").append(days[random.nextInt(days.size)]).append('.')
        }
      }
      FluidSearchDocument(
        id = "gen-$i",
        kind = "nota",
        fields = mapOf("title" to "${subject.replaceFirstChar { it.uppercase() }} $detail di $day", "body" to sentences),
        timestamp = Now - (i % 365) * Day,
        link = "pampa-prova://notes/gen-$i",
      )
    }
  }
}
