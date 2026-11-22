"""Confronti affiancati, dopo misura.py. Uso: python video.py CARTELLA_SESSIONE CARTELLA_RICERCA."""
import argparse
import csv
import functools
from pathlib import Path
import subprocess

from PIL import Image, ImageDraw, ImageFont

W, H = 400, 600
FRAME = (864, 720)
FONTS = Path("C:/Windows/Fonts")
TITLE = ImageFont.truetype(str(FONTS/"segoeuib.ttf"), 23)
LABEL = ImageFont.truetype(str(FONTS/"segoeui.ttf"), 16)
SMALL = ImageFont.truetype(str(FONTS/"segoeui.ttf"), 13)
TITLES = {"pagina-arriva": "Una pagina arriva, poi torna indietro",
          "pagina-col-dito": "Indietro con sosta e inversione",
          "pagina-col-dito-annullata": "La pagina torna sotto il dito",
          "zoom-scurisce": "Dalla cella alla pagina e ritorno"}


@functools.lru_cache(maxsize=6)
def reference_frames(path):
    rate = subprocess.check_output(["ffprobe","-v","error","-select_streams","v:0","-show_entries",
        "stream=r_frame_rate","-of","csv=p=0",str(path)], text=True).strip().split("/")
    fps = float(rate[0]) / (float(rate[1]) if len(rate) > 1 else 1)
    data = subprocess.check_output(["ffmpeg","-v","error","-threads","2","-i",str(path),"-vf",
        f"scale={W}:{H}:force_original_aspect_ratio=decrease,pad={W}:{H}:(ow-iw)/2:(oh-ih)/2:color=0x20242c",
        "-threads","1","-f","rawvideo","-pix_fmt","rgb24","-"])
    stride = W*H*3
    return fps, [Image.frombytes("RGB",(W,H),data[i:i+stride]) for i in range(0,len(data),stride)]


def chapter(scene, dark, close):
    if scene == "pagina-arriva":
        if close and dark:
            return "05-pop-settings-26-scuro", 1, "Impostazioni · iOS 26 · curva scura NV"
        if close:
            return "02-pop-note-26", 1, "Note · iOS 26 chiaro · ritorno al tasto"
        return "01-push-note-26", 1, "Note · iOS 26 chiaro · apertura interrotta"
    if scene == "pagina-col-dito":
        return "04-dito-contenuto-note-26", 1, "Note · iOS 26 chiaro · gesto dal contenuto"
    if scene == "zoom-scurisce":
        if close:
            return "10-zoom-ritorno-18", 2, "iPadOS 18 chiaro · ritorno con il dito"
        return "08-zoom-ipad-18", 2, "iPadOS 18 chiaro · apertura dalla cella"
    return None, 1, "Annullo · riferimento Apple NV"


def movie(path, scene, dark, dest, research):
    files = sorted((path/"testo").glob("f*.png"))
    outputs = []
    try:
        for fps, speed in [(120, "tempo-vero"), (30, "rallentato-4x")]:
            out = dest/(path.name+"-"+speed+".mp4")
            outputs.append(subprocess.Popen(["ffmpeg","-y","-v","error","-f","rawvideo","-pix_fmt","rgb24",
                "-s","864x720","-r",str(fps),"-i","-","-an","-c:v","libx264","-threads","2","-crf","18",
                "-preset","veryfast","-pix_fmt","yuv420p","-movflags","+faststart",str(out)], stdin=subprocess.PIPE))
        segments = [files[:103], files[103:]] if scene in ("pagina-arriva","zoom-scurisce") else [files]
        for index, segment in enumerate(segments):
            clip, factor, label = chapter(scene, dark, index > 0)
            refs, ref_rate, origin = [], 1., 0
            if clip:
                ref_rate, refs = reference_frames(research/"clip"/(clip+".mp4"))
                with (research/"dati"/(clip+".csv")).open(encoding="utf-8-sig") as stream:
                    origin = next(int(r["frame_clip"]) for r in csv.DictReader(stream) if float(r["ms_video"]) == 0)
            start = 5 if index == 0 else 0
            # I due orologi corrono ciascuno al proprio passo. Quello che arriva prima resta fermo.
            count = max(len(segment), start+round((len(refs)-origin)*120/ref_rate/factor)) if refs else len(segment)
            for k in range(count):
                canvas = Image.new("RGB", FRAME, "#161A22")
                d = ImageDraw.Draw(canvas)
                d.text((20,12), TITLES[scene], font=TITLE, fill="#F4F6FA")
                d.text((20,53), "FLUID   /   "+("Scuro · RTL" if dark else "Chiaro · LTR"), font=LABEL, fill="#B1E3D5")
                d.text((444,53), label, font=LABEL, fill="#D8DEEB")
                f = segment[min(k,len(segment)-1)]
                with Image.open(f) as im:
                    canvas.paste(im.convert("RGB"),(20,80))
                if refs:
                    j = max(0,min(len(refs)-1,origin+round((k-start)*ref_rate*factor/120)))
                    canvas.paste(refs[j],(444,80))
                else:
                    d.rectangle((444,80,843,679), fill="#20242C")
                    d.text((475,334),"Manca una clip Apple dell’annullo.",font=LABEL, fill="#DCE2EF")
                note = "WWDC24 10145 riportata a tempo reale (clip Apple a 0,5×)." if factor == 2 else (
                    "WWDC25 284 al ritmo pubblicato. Velocità della dimostrazione non dichiarata." if clip and clip[:2] != "05"
                    else "La misura è sul banco Fluid. Parità Apple in scuro e all’annullo NV.")
                d.text((20,691),note,font=SMALL,fill="#B7C2D1")
                data = canvas.tobytes()
                for proc in outputs:
                    proc.stdin.write(data)
    finally:
        for proc in outputs:
            proc.stdin.close()
        for proc in outputs:
            if proc.wait():
                raise RuntimeError("FFmpeg non ha chiuso il video")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("sessione",type=Path)
    ap.add_argument("ricerca",type=Path)
    args = ap.parse_args()
    dest = args.sessione/"per-alessio"
    dest.mkdir(exist_ok=True)
    for path in sorted((args.sessione/"tracce").iterdir()):
        scene, theme = path.name.rsplit("-",1)
        if scene in TITLES:
            movie(path,scene,theme=="scuro",dest,args.ricerca)
            print(path.name, flush=True)
