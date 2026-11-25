# Il permesso aggiuntivo per le librerie di Google (GPL-3.0, sezione 7)

Dalla 4.0 (F10.2) l'engine ha un modulo, `engine-auth-google`, che si collega a librerie di Google **non libere**:
Google Play services (`play-services-auth`), la libreria `googleid` di Google Identity e il ponte di Credential
Manager verso Play services (`credentials-play-services-auth`). Più avanti Pampa Store userà anche Firebase Cloud
Messaging (`engine-push-fcm`, F10.6). Non sono librerie di sistema: viaggiano dentro l'APK. Una GPL-3.0 da sola non
permette di distribuire l'opera che le unisce, quindi l'autore dell'engine aggiunge un permesso esplicito, come prevede
la sezione 7 della licenza. È la scelta Q6 di Alessio del 24 novembre 2026.

## Il testo

Il modello è quello della Free Software Foundation, copiato alla lettera e riempito dove la FSF chiede di sostituire
il testo fra parentesi quadre. La frase fra parentesi graffe della FSF («Corresponding Source … shall include the source
code for the parts of [name of library] used…») è tolta, come dice la FSF quando non tutti possono distribuire i
sorgenti di quelle librerie: è il caso di Google Play services.

> Copyright (C) 2026 Alessio Pampaloni
>
> This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
> License as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later
> version.
>
> This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
> warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
> details.
>
> You should have received a copy of the GNU General Public License along with this program; if not, see
> <https://www.gnu.org/licenses>.
>
> Additional permission under GNU GPL version 3 section 7
>
> If you modify this Program, or any covered work, by linking or combining it with Google Play services (the
> com.google.android.gms libraries, such as play-services-auth), the Google Identity library
> (com.google.android.libraries.identity.googleid), the Credential Manager support for Google Play services
> (androidx.credentials:credentials-play-services-auth, together with the Google Play services libraries it depends
> on) or Firebase Cloud Messaging (com.google.firebase:firebase-messaging) (or a modified version of those
> libraries), containing parts covered by the terms of the Android Software Development Kit License or of the other
> license terms under which Google distributes those libraries, the licensors of this Program grant you additional
> permission to convey the resulting work.

**La fonte.** Free Software Foundation, *Frequently Asked Questions about the GNU Licenses*, voce «What legal issues
come up if I use GPL-incompatible libraries with GPL software?», <https://www.gnu.org/licenses/gpl-faq.html#GPLIncompatibleLibs>
(pagina aggiornata il 13 gennaio 2026, letta il 25 novembre 2026). La traduzione italiana della FSF
(<https://www.gnu.org/licenses/gpl-faq.it.html#GPLIncompatibleLibs>, del 2019) lascia il modello in inglese, e così
qui: vale il testo inglese. Il nome della licenza delle librerie di Play services è quello dei loro POM su Maven
(«Android Software Development Kit License», <https://developer.android.com/studio/terms>, letto il 25/11/2026 nel POM
di `play-services-auth` 22.0.0).

## Cosa vuol dire, in parole semplici

- **Si può distribuire un'app che unisce l'engine e quelle librerie di Google**, anche se non sono libere. Il resto
  della GPL-3.0 vale com'è: i sorgenti dell'engine e dell'app a chi riceve l'APK, la stessa licenza.
- **Il permesso non obbliga gli altri.** Chi modifica l'engine può toglierlo dal suo codice (lo dice la FSF nella
  stessa voce).
- **Vale per il codice di Alessio, non per quello degli altri.** La FSF è chiara: solo chi ha il copyright può dare il
  permesso, e per le parti di altri programmi GPL «you cannot authorize the exception for them». Nell'engine c'è codice
  GPL-3.0 di LibrePods (i pulsanti e le pagine di `engine-ui`, `LICENSES/LibrePods.md`): per quelle parti il permesso
  **non vale**, finché gli autori di LibrePods non lo danno anche loro.
  - `engine-auth` ed `engine-auth-google` non contengono codice di LibrePods e non dipendono da `engine-ui`.
  - Un'app che contiene **sia** `engine-ui` **sia** Play services (Pampa Store da F10.7, e già oggi Pampa Notes, che
    usa l'engine 3.0.5 e `googleid`) resta con questo dubbio per le parti di LibrePods. È una domanda per Alessio, nel
    rapporto di F10.2.
- **Non è un parere legale.** È la lettura della licenza e della FAQ della FSF (fiducia media), come già scritto in
  `artifacts\ecosistema\account\b\architettura.md` § 9.7.

## Dove vale

Il permesso vale per tutti i file dell'engine scritti da Alessio. La FSF consiglia di scriverlo «in each file to which
you are granting this permission»: i file di `engine-auth-google` lo richiamano in testa al modulo (`build.gradle`), e
questo file è il testo di riferimento. Lo stesso permesso andrà nella licenza di Pampa Store in F10.7.
