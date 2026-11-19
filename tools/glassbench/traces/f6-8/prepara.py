"""Raccoglie le tracce F6 e aggiunge l'allineamento ai riferimenti R4."""

import json
from pathlib import Path


QUI = Path(__file__).resolve().parent
VIDEO = "wwdc2025-219_hd"
COLLAGE = "artifacts/liquid-glass/riferimenti/collage"


def apple(nome, frame, ritaglio, collage, nota):
    return {
        "name": nome,
        "video": VIDEO,
        "t0Frame": frame,
        "crop": ritaglio,
        "collage": f"{COLLAGE}/{collage}.jpg",
        "note": nota,
    }


def salva(nome, sorgente, *riferimenti, riposo_prima_del_contatto=False):
    traccia = json.loads((QUI.parent / sorgente).read_text(encoding="utf-8"))
    traccia.update(name=nome, phase="f6-8")
    if riferimenti:
        traccia["apple"] = list(riferimenti)
    if riposo_prima_del_contatto:
        # F61GelTest misura il clip attivo prima del down, anche se l'export storico
        # mette questi due frame nel ramo senza clip. La lastra è ancora a riposo.
        for frame in traccia["frames"]:
            if frame["t"] >= 0:
                break
            vetro = next(elemento for elemento in frame["elements"] if elemento["name"] == "vetro")
            vetro.update(clip=True, marginDp=0)
    destinazione = QUI / nome / "trace.json"
    destinazione.parent.mkdir(exist_ok=True)
    destinazione.write_text(
        json.dumps(traccia, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n",
    )


def main():
    salva("tasto", "f6-2/tasto/trace.json", apple(
        "b4-tocco", 6950, [408, 64, 1068, 890], "B4_tocco_tasto_f6940-7018_ogni2",
        "Contatto f6950, distacco f6967. Render Apple con retiming sconosciuto.",
    ), riposo_prima_del_contatto=True)
    salva("capsula-trascinata", "f6-2/capsula-trascinata/trace.json", apple(
        "b4-flessione", 7310, [100, 228, 1700, 588], "B4_flessione_capsula_f7300-7384_ogni2",
        "Contatto f7310. Fondale, gesto e glifi del banco sono ricostruiti.",
    ), riposo_prima_del_contatto=True)
    salva("bagliore-vicini", "f6-2/bagliore-vicini/trace.json", apple(
        "b4-propagato", 21792, [545, 365, 805, 395], "B4_bagliore_propagato_f21776-21880_ogni3",
        "Allineato all'accensione visibile della barra. Ripresa Apple in prospettiva.",
    ), apple(
        "b4-play", 21969, [542, 325, 805, 430], "B4_bagliore_play_f21934-22036_ogni3",
        "Allineato alla riapertura di Play. Confronto della luce su superfici diverse.",
    ))
    salva("interruttore", "f6-3/interruttore-tocco/trace.json", apple(
        "b5-interruttore", 7756, [660, 360, 600, 395], "B5_interruttore_f7752-7880_ogni2",
        "Primo tocco che spegne la pista verde.",
    ))
    salva("cursore", "f6-3/cursore-arresto/trace.json", apple(
        "b5-cursore", 8104, [120, 375, 1650, 435], "B5_cursore_f8040-8146_ogni2",
        "Allineato al rilascio attorno a f8129-f8130. Il percorso del banco viene da WWDC25 284.",
    ))
    salva("luce-bordo", "f6-5/corsa-sul-bordo.json", apple(
        "b8-bordo", 19975, [530, 275, 860, 565], "B8_luce_sul_bordo_f19975-20235_ogni8",
        "Allineamento della corsa ottica sintetica. Nessun evento di comparsa o sensore in questa traccia.",
    ))
    salva("tasto-interrotto", "f6-2/tasto-interrotto/trace.json", riposo_prima_del_contatto=True)


if __name__ == "__main__":
    main()
