"""Izrada interaktivne Folium karte lokaliteta iz kataloga."""

from __future__ import annotations

from pathlib import Path
from typing import Optional

import folium
import pandas as pd

from .data import load_dataframe

OUTPUT_PATH = Path(__file__).resolve().parent.parent / "output" / "karta.html"


def build_map(df: Optional[pd.DataFrame] = None) -> folium.Map:
    """Izgradi Folium kartu sa svim nekropolama u katalogu (ili proslijeđenom podskupu)."""
    if df is None:
        df = load_dataframe()

    m = folium.Map(location=[43.4, 18.2], zoom_start=7, tiles="OpenStreetMap")

    for _, red in df.iterrows():
        broj = red.get("brojStecakaPribl")
        broj_tekst = (
            f"≈ {int(broj)} stećaka" if pd.notna(broj) else "broj stećaka nije dokumentiran"
        )
        unesco = " · UNESCO" if red.get("unescoLokalitet") else ""
        popup_html = (
            f"<b>{red['naziv']}</b><br>{red['opcina']}, {red['drzava']}{unesco}"
            f"<br>{broj_tekst}"
        )
        folium.Marker(
            location=[red["lat"], red["lng"]],
            popup=folium.Popup(popup_html, max_width=260),
            tooltip=red["naziv"],
            icon=folium.Icon(color="darkgreen", icon="info-sign"),
        ).add_to(m)

    return m


def save_map(path: Path = OUTPUT_PATH) -> Path:
    """Sačuvaj kartu kao samostalan HTML fajl."""
    m = build_map()
    path.parent.mkdir(parents=True, exist_ok=True)
    m.save(str(path))
    return path


if __name__ == "__main__":
    izlaz = save_map()
    print(f"Karta spremljena u {izlaz}")
