"""
Generiranje hipoteza o mogućim neistraženim lokalitetima stećaka.

VAŽNA NAPOMENA O NAMJENI I OGRANIČENJIMA
-----------------------------------------
Ovaj modul NE otkriva stvarne nove lokalitete stećaka i njegov izlaz se
NIKAD ne smije predstavljati kao potvrđen nalaz. On generira grubu,
heurističku procjenu "prioritetnih zona za terensko istraživanje" na
osnovu:

1. dokumentirane isključene zone (mjesta gdje je iz historijskih razloga
   izrazito malo vjerojatno da postoje stećci), i
2. jednostavne analize praznina (koje dijelove poznatog areala postojećih
   nekropola treniranje pokriva rjeđe nego ostale, na temelju najbliže
   udaljenosti do dokumentiranog lokaliteta u katalogu).

Prema dostupnom istraživanju (kolovoz 2026), ne postoji objavljen,
akademski validiran prediktivni GIS model specifično za stećke (za
razliku od npr. paleolitskih nalazišta na Balkanu, gdje takvi modeli
postoje). Najbliži relevantan rad je Saša Čaval, "Social landscapes as
multicultural spaces: stećci in Bosnia and Herzegovina", Antiquity 95(380)
(2021) — koji povezuje nekropole s obližnjim srednjovjekovnim naseljima
("deathscape" nasuprot "livescape") u dolini Dabra, te SOLMUS projekt
(ZRC SAZU / University of Reading, Marie Skłodowska-Curie grant 797881),
koji je proveo geofizička istraživanja na 7 nekropola u istoj dolini.
Ovaj modul je eksperimentalni, mnogo grublji pokušaj inspiriran tom idejom
("blizina poznatim klasterima ukazuje na sličan kontekst naseljavanja"),
ne njena reprodukcija.

Glavni, najbolje potkrijepljeni faktor je HISTORIJSKO-POLITIČKA/VJERSKA
ZONA: stećci su gotovo isključivo vezani uz teritorij srednjovjekovne
bosanske države i Crkve bosanske (i susjedne oblasti pod sličnim
kulturnim uticajem u Humu, dijelu Crne Gore i zapadne Srbije). Sjeverozapadna
Bosna (Bihać, Cazin, Bosanska Krupa, Bosanski Petrovac, Ključ, Sanski Most,
Velika Kladuša, Bužim — Unsko-sanski kanton) bila je kroz cijeli srednji
vijek dio ugarsko-hrvatske krune (Bihać je dobio status kraljevskog
slobodnog grada 1260–62), IZVAN bosanske države i Crkve bosanske — u
dostupnim izvorima (UNESCO lista, nekropola.ba, stecciwh.org) nije
pronađena nijedna dokumentirana nekropola stećaka u toj regiji. To je
razlog zašto "japodsko" porijeklo stanovništva Bihaća (željezno doba,
tisućljeće prije stećaka) NIJE relevantan prediktor za stećke — kulturna
veza ne postoji, dok politička/vjerska granica jest dobro potkrijepljen
razlog odsustva.

Svaki rezultat ovog modula treba tumačiti kao "vrijedi provjeriti na
terenu / u lokalnoj konzervatorskoj dokumentaciji", a ne kao "ovdje
postoji stećak".
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Optional

import pandas as pd

from .data import load_dataframe

# ---------------------------------------------------------------------------
# Dokumentirane isključene zone (niska vjerojatnoća stećaka)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class IskljucenaZona:
    naziv: str
    lat_min: float
    lat_max: float
    lng_min: float
    lng_max: float
    razlog: str
    izvori: tuple[str, ...] = field(default_factory=tuple)

    def sadrzi(self, lat: float, lng: float) -> bool:
        return self.lat_min <= lat <= self.lat_max and self.lng_min <= lng <= self.lng_max


ZONE_ISKLJUCENJA: list[IskljucenaZona] = [
    IskljucenaZona(
        naziv="Unsko-sanski kanton (Bihać, Cazin, Bosanska Krupa, B. Petrovac, "
        "Ključ, Sanski Most, V. Kladuša, Bužim)",
        lat_min=44.45,
        lat_max=45.25,
        lng_min=15.65,
        lng_max=16.85,
        razlog=(
            "Kroz cijeli srednji vijek dio Ugarsko-hrvatskog kraljevstva "
            "(Bihać je 1260–62. dobio status kraljevskog slobodnog grada), "
            "izvan teritorija bosanske države i Crkve bosanske s kojima je "
            "pojava stećaka gotovo isključivo povezana. U dostupnim izvorima "
            "nije pronađena nijedna dokumentirana nekropola stećaka u ovoj "
            "regiji (istraženo na primjer korisničkog upita o 'Japodima' u "
            "Bihaću — Japodi su ilirsko pleme željeznog doba, kulturno i "
            "vremenski nepovezano sa srednjovjekovnim stećcima)."
        ),
        izvori=(
            "UNESCO World Heritage List – Stećci Medieval Tombstone Graveyards (whc.unesco.org/en/list/1504)",
            "nekropola.ba",
            "Slavorum — Bosnian Medieval Tombstones Stećci (slavorum.org)",
            "Wikipedia — Bihać (status kraljevskog slobodnog grada 1260-62)",
        ),
    ),
]


def je_u_iskljucenoj_zoni(lat: float, lng: float) -> Optional[IskljucenaZona]:
    """Vrati IskljucenaZona ako je tačka unutar dokumentirane niskoprioritetne zone, inače None."""
    for zona in ZONE_ISKLJUCENJA:
        if zona.sadrzi(lat, lng):
            return zona
    return None


# ---------------------------------------------------------------------------
# Geometrija i najbliži poznati lokalitet
# ---------------------------------------------------------------------------


def haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Udaljenost po velikom krugu (km) između dvije geografske tačke."""
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlambda / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def najbliza_nekropola(lat: float, lng: float, df: Optional[pd.DataFrame] = None) -> dict:
    """Nađi najbližu poznatu nekropolu iz kataloga i udaljenost do nje (km)."""
    if df is None:
        df = load_dataframe()
    udaljenosti = df.apply(
        lambda red: haversine_km(lat, lng, red["lat"], red["lng"]), axis=1
    )
    idx = udaljenosti.idxmin()
    return {
        "naziv": df.loc[idx, "naziv"],
        "opcina": df.loc[idx, "opcina"],
        "udaljenost_km": round(float(udaljenosti.loc[idx]), 1),
    }


# ---------------------------------------------------------------------------
# Ocjena kandidata
# ---------------------------------------------------------------------------

# "Slatka zona" udaljenosti od najbližeg poznatog lokaliteta: dovoljno daleko
# da vrijedi provjeriti kao potencijalnu prazninu u pokrivenosti terena, ali
# dovoljno blizu da ostane unutar kulturno-geografskog kontinuuma poznatih
# klastera, a ne nasumično daleko izvan njega. Ovo je proizvoljan, grub
# parametar demonstracije — NIJE kalibriran na stvarnim podacima o
# neotkrivenim lokalitetima (takvi podaci po definiciji ne postoje).
UDALJENOST_MIN_KM = 8.0
UDALJENOST_OPT_KM = 20.0
UDALJENOST_MAX_KM = 45.0


def _trokutasti_rezultat(x: float, min_: float, opt: float, max_: float) -> float:
    """Trokutasta funkcija ocjene: 0 na rubovima, 1 na `opt`."""
    if x <= min_ or x >= max_:
        return 0.0
    if x <= opt:
        return (x - min_) / (opt - min_)
    return (max_ - x) / (max_ - opt)


def oceni_kandidat(
    lat: float,
    lng: float,
    df: Optional[pd.DataFrame] = None,
    na_prapovijesnoj_gomili: Optional[bool] = None,
) -> dict:
    """
    Heuristička, EKSPERIMENTALNA ocjena (0-100) koliko je vrijedno terenski
    provjeriti tačku (lat, lng) kao mogući neistraženi lokalitet. Ocjena NIJE
    procjena vjerojatnosti u statističkom smislu — riječ je o grubom rangiranju
    za prioritizaciju terenskog rada, izvedenom iz svega dva faktora s
    razumnim opravdanjem u dostupnoj literaturi (isključena zona + blizina
    poznatim klasterima) plus opcionalnog bonusa za prapovijesnu gomilu.
    """
    if df is None:
        df = load_dataframe()

    zona = je_u_iskljucenoj_zoni(lat, lng)
    if zona is not None:
        return {
            "lat": lat,
            "lng": lng,
            "ocjena": 0,
            "u_iskljucenoj_zoni": zona.naziv,
            "obrazlozenje": f"Unutar dokumentirane niskoprioritetne zone: {zona.razlog}",
            "napomena": "SPEKULATIVNO — vidi docstring modula za ograničenja metode.",
        }

    najblizi = najbliza_nekropola(lat, lng, df)
    ocjena_udaljenosti = _trokutasti_rezultat(
        najblizi["udaljenost_km"], UDALJENOST_MIN_KM, UDALJENOST_OPT_KM, UDALJENOST_MAX_KM
    )

    bonus_gomila = 15.0 if na_prapovijesnoj_gomili else 0.0

    ocjena = round(min(100.0, ocjena_udaljenosti * 85.0 + bonus_gomila), 1)

    obrazlozenje = (
        f"{najblizi['udaljenost_km']} km od najbližeg poznatog lokaliteta "
        f"({najblizi['naziv']}, {najblizi['opcina']}) — "
        + (
            "u 'slatkoj zoni' udaljenosti od poznatih klastera (moguća praznina u pokrivenosti)."
            if ocjena_udaljenosti > 0
            else "izvan slatke zone (ili predaleko od poznatog kulturnog areala, ili preblizu/preklapa se s postojećim lokalitetom)."
        )
    )
    if na_prapovijesnoj_gomili:
        obrazlozenje += (
            " Dodatan bonus jer je tačka na/uz prapovijesnu gomilu — dokumentiran obrazac "
            "sekundarnog srednjovjekovnog ukopa (npr. Hatelji/SOLMUS projekt, Pakoštane – "
            "Maksanova gomila)."
        )

    return {
        "lat": lat,
        "lng": lng,
        "ocjena": ocjena,
        "u_iskljucenoj_zoni": None,
        "najblizi_poznati_lokalitet": najblizi["naziv"],
        "udaljenost_km": najblizi["udaljenost_km"],
        "obrazlozenje": obrazlozenje,
        "napomena": "SPEKULATIVNO — vidi docstring modula za ograničenja metode.",
    }


# ---------------------------------------------------------------------------
# Generiranje mreže kandidatskih zona
# ---------------------------------------------------------------------------


def generiraj_kandidat_zone(
    df: Optional[pd.DataFrame] = None,
    korak_stupnjeva: float = 0.15,
    margina_stupnjeva: float = 0.3,
    prag_ocjene: float = 40.0,
    top_n: int = 15,
) -> pd.DataFrame:
    """
    Generiraj grubu mrežu tačaka preko omeđujućeg pravokutnika poznatih BiH
    lokaliteta (uvećanog za marginu), oceni svaku heuristikom `oceni_kandidat`,
    isključi tačke u dokumentiranim niskoprioritetnim zonama, i vrati top_n
    najviše ocijenjenih kao DataFrame.

    Rezultat je alat za PRIORITIZACIJU terenskog istraživanja, ne popis
    stvarnih lokaliteta. Svaki red uključuje obrazloženje i eksplicitnu
    napomenu o spekulativnosti.
    """
    if df is None:
        df = load_dataframe()
    bih = df[df["drzava"] == "Bosna i Hercegovina"]

    lat_min, lat_max = bih["lat"].min() - margina_stupnjeva, bih["lat"].max() + margina_stupnjeva
    lng_min, lng_max = bih["lng"].min() - margina_stupnjeva, bih["lng"].max() + margina_stupnjeva

    kandidati = []
    lat = lat_min
    while lat <= lat_max:
        lng = lng_min
        while lng <= lng_max:
            if je_u_iskljucenoj_zoni(lat, lng) is None:
                rezultat = oceni_kandidat(round(lat, 4), round(lng, 4), df=bih)
                if rezultat["ocjena"] >= prag_ocjene:
                    kandidati.append(rezultat)
            lng += korak_stupnjeva
        lat += korak_stupnjeva

    if not kandidati:
        return pd.DataFrame(
            columns=[
                "lat",
                "lng",
                "ocjena",
                "najblizi_poznati_lokalitet",
                "udaljenost_km",
                "obrazlozenje",
            ]
        )

    rezultat_df = pd.DataFrame(kandidati).sort_values("ocjena", ascending=False)
    # Prorijedi guste susjedne kandidate (unutar ~korak_stupnjeva) da se ne
    # ponavlja praktički ista tačka desetke puta.
    zadrzani: list[dict] = []
    for _, red in rezultat_df.iterrows():
        preblizu = any(
            haversine_km(red["lat"], red["lng"], z["lat"], z["lng"]) < 10.0 for z in zadrzani
        )
        if not preblizu:
            zadrzani.append(red.to_dict())
        if len(zadrzani) >= top_n:
            break

    return pd.DataFrame(zadrzani)
