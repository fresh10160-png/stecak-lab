"""Generiranje statističkog izvještaja (Markdown) iz kataloga nekropola."""

from __future__ import annotations

from pathlib import Path

from .data import (
    broj_nedokumentiranih,
    load_dataframe,
    stats_motiva,
    stats_po_drzavi,
    ukupan_broj_stecaka,
)

OUTPUT_PATH = Path(__file__).resolve().parent.parent / "output" / "izvjestaj.md"


def generate_report() -> str:
    df = load_dataframe()
    ukupno = ukupan_broj_stecaka(df)
    nedokumentirano = broj_nedokumentiranih(df)
    po_drzavi = stats_po_drzavi(df)
    motivi = stats_motiva(df)

    linije = [
        "# Statistički izvještaj — Stećak Lab",
        "",
        f"- Nekropola u katalogu: **{len(df)}**",
        f"- Ukupno stećaka (okvirno, gdje je dokumentirano): **{ukupno}**",
        f"- Nekropola bez dokumentiranog broja stećaka: **{nedokumentirano}**",
        "",
        "## Po državi",
        "",
        "| Država | Nekropola | Stećaka (okvirno) |",
        "|---|---:|---:|",
    ]
    for _, red in po_drzavi.iterrows():
        linije.append(
            f"| {red['drzava']} | {int(red['broj_nekropola'])} | {int(red['broj_stecaka'])} |"
        )

    linije += ["", "## Najčešći motivi", "", "| Motiv | Broj lokaliteta |", "|---|---:|"]
    for motiv, broj in motivi.items():
        linije.append(f"| {motiv} | {int(broj)} |")

    return "\n".join(linije) + "\n"


def save_report(path: Path = OUTPUT_PATH) -> Path:
    tekst = generate_report()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(tekst, encoding="utf-8")
    return path


if __name__ == "__main__":
    izlaz = save_report()
    print(f"Izvještaj spremljen u {izlaz}")
