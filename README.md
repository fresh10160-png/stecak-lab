# Stećak Lab (Python)

Istraživački laboratorij za katalogizaciju i analizu **stećaka** —
srednjovjekovnih nadgrobnih spomenika upisanih 2016. na UNESCO-vu listu
svjetske baštine, rasprostranjenih po Bosni i Hercegovini, Hrvatskoj, Crnoj
Gori i Srbiji.

Ovo je Python/pandas/Jupyter varijanta laboratorija (usporedo postoji i
web-dashboard varijanta na drugoj grani/repozitoriju) — namijenjena
istraživačkoj analizi podataka: učitavanje, filtriranje, statistika,
interaktivna karta i grafikoni, sve iz Jupyter notebook-a ili skripti.

## Instalacija

```bash
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -e ".[dev]"     # ili: pip install -r requirements.txt
```

## Korištenje

**Jupyter notebooks** (glavni ulaz za istraživanje):

```bash
jupyter notebook notebooks/
```

- `01_pregled.ipynb` — učitavanje kataloga, osnovne brojke, pregled po državi
- `02_karta.ipynb` — interaktivna Folium karta svih lokaliteta (i filtriranih podskupova)
- `03_motivi_i_natpisi.ipynb` — učestalost motiva, broj stećaka po državi, natpisi

**Iz Python koda / skripti**:

```python
from stecak_lab.data import load_dataframe, filter_necropoles, stats_po_drzavi

df = load_dataframe()
bih = filter_necropoles(df, drzava="Bosna i Hercegovina")
stats_po_drzavi(df)
```

**CLI skripte**:

```bash
python scripts/generate_report.py   # sprema output/karta.html i output/izvjestaj.md
python scripts/export_csv.py        # sprema data/necropoles.csv
```

**Testovi**:

```bash
pytest
```

## Struktura projekta

```
data/necropoles.json     izvorni katalog nekropola (23 lokaliteta, sa izvorima)
stecak_lab/               Python paket
  data.py                  učitavanje, filtriranje, agregatna statistika
  mapping.py                izrada Folium karte
  report.py                  generiranje Markdown izvještaja
notebooks/                Jupyter notebooks za istraživanje
scripts/                  CLI skripte (izvještaj, izvoz u CSV)
tests/                    pytest testovi za stecak_lab.data
```

## Podaci

`data/necropoles.json` sadrži **23 lokaliteta** (20 iz Bosne i Hercegovine —
uključujući sve zasad prikupljene UNESCO-om upisane BiH nekropole — te po
jedan iz Hrvatske, Crne Gore i Srbije), prikupljen web-pretragom javno
dostupnih izvora (Wikipedia, stecciwh.org, kons.gov.ba, UNESCO
dokumentacija, lokalni mediji). Koordinate su za većinu lokaliteta na
razini općine/naselja (nisu terenski GPS-mjerene), a polja koja izvori
nisu naveli (broj stećaka, oblici, motivi) ostavljena su prazna/`null`
umjesto nagađanja. Svaki zapis navodi korištene izvore u polju `izvori`.
Prije ozbiljne istraživačke upotrebe podatke treba dopuniti terenskim
mjerenjima i stručnom literaturom.

Novi lokalitet dodaje se kao novi objekt u `data/necropoles.json`; svi
alati (notebooks, skripte, testovi) ga automatski uključuju.

## Tehnologije

Python 3.9+ · pandas · Folium · Matplotlib · Jupyter · pytest
