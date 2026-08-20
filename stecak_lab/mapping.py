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


HIPOTEZE_OUTPUT_PATH = Path(__file__).resolve().parent.parent / "output" / "hipoteze_karta.html"


def build_hipoteze_map(
    df: Optional[pd.DataFrame] = None,
    kandidati: Optional[pd.DataFrame] = None,
) -> folium.Map:
    """
    Karta koja uz poznate (zelene) nekropole prikazuje i:
    - crveno osjenčane dokumentirane niskoprioritetne zone (`ZONE_ISKLJUCENJA`), i
    - narančaste oznake kandidatskih zona za terensku provjeru
      (`generiraj_kandidat_zone`), s jasnom oznakom da su SPEKULATIVNE.

    Vidi docstring `stecak_lab.hipoteze` za metodologiju i ograničenja.
    """
    from .hipoteze import ZONE_ISKLJUCENJA, generiraj_kandidat_zone

    if df is None:
        df = load_dataframe()
    if kandidati is None:
        kandidati = generiraj_kandidat_zone(df)

    m = build_map(df)

    for zona in ZONE_ISKLJUCENJA:
        folium.Rectangle(
            bounds=[(zona.lat_min, zona.lng_min), (zona.lat_max, zona.lng_max)],
            color="#b91c1c",
            weight=1,
            fill=True,
            fill_color="#b91c1c",
            fill_opacity=0.12,
            tooltip=f"Niskoprioritetna zona: {zona.naziv}",
            popup=folium.Popup(f"<b>{zona.naziv}</b><br>{zona.razlog}", max_width=320),
        ).add_to(m)

    for _, red in kandidati.iterrows():
        popup_html = (
            f"<b>SPEKULATIVNA kandidatska zona</b> (ocjena {red['ocjena']})<br>"
            f"{red['obrazlozenje']}<br>"
            f"<i>Nije potvrđen lokalitet — polazište za terensku provjeru.</i>"
        )
        folium.CircleMarker(
            location=[red["lat"], red["lng"]],
            radius=6 + min(6, red["ocjena"] / 15),
            color="#c2410c",
            weight=1,
            fill=True,
            fill_color="#f97316",
            fill_opacity=0.7,
            tooltip=f"Kandidat (ocjena {red['ocjena']})",
            popup=folium.Popup(popup_html, max_width=300),
        ).add_to(m)

    return m


def save_hipoteze_map(path: Path = HIPOTEZE_OUTPUT_PATH) -> Path:
    m = build_hipoteze_map()
    path.parent.mkdir(parents=True, exist_ok=True)
    m.save(str(path))
    return path


if __name__ == "__main__":
    izlaz = save_map()
    print(f"Karta spremljena u {izlaz}")
