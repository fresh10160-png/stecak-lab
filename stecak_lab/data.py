"""Pristup podacima o nekropolama stećaka (data/necropoles.json)."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Optional

import pandas as pd

DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "necropoles.json"


def load_raw() -> list[dict]:
    """Učitaj sirove podatke iz data/necropoles.json."""
    with DATA_PATH.open(encoding="utf-8") as f:
        return json.load(f)


def load_dataframe() -> pd.DataFrame:
    """Učitaj katalog nekropola kao pandas DataFrame (koordinate raspakirane u lat/lng)."""
    df = pd.json_normalize(load_raw())
    df = df.rename(columns={"koordinate.lat": "lat", "koordinate.lng": "lng"})
    return df


def filter_necropoles(
    df: pd.DataFrame,
    drzava: Optional[str] = None,
    motiv: Optional[str] = None,
    samo_sa_natpisima: bool = False,
) -> pd.DataFrame:
    """Filtriraj katalog po državi, motivu i/ili prisustvu natpisa."""
    rezultat = df
    if drzava:
        rezultat = rezultat[rezultat["drzava"] == drzava]
    if motiv:
        rezultat = rezultat[rezultat["motivi"].apply(lambda m: motiv in m)]
    if samo_sa_natpisima:
        rezultat = rezultat[rezultat["natpisi"]]
    return rezultat


def stats_po_drzavi(df: pd.DataFrame) -> pd.DataFrame:
    """Broj nekropola i okvirni broj stećaka po državi, sortirano opadajuće."""
    grupirano = df.groupby("drzava").agg(
        broj_nekropola=("id", "count"),
        broj_stecaka=("brojStecakaPribl", lambda s: s.fillna(0).sum()),
    )
    return grupirano.reset_index().sort_values("broj_stecaka", ascending=False)


def stats_motiva(df: pd.DataFrame) -> pd.Series:
    """Učestalost pojedinačnih motiva kroz katalog (broj lokaliteta po motivu)."""
    return df["motivi"].explode().dropna().value_counts()


def ukupan_broj_stecaka(df: pd.DataFrame) -> int:
    """Zbroj brojStecakaPribl preko svih nekropola (nedokumentirani se broje kao 0)."""
    return int(df["brojStecakaPribl"].fillna(0).sum())


def broj_nedokumentiranih(df: pd.DataFrame) -> int:
    """Broj nekropola kojima broj stećaka nije dokumentiran u dostupnim izvorima."""
    return int(df["brojStecakaPribl"].isna().sum())
