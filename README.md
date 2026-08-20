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
- `04_hipoteze_novih_lokaliteta.ipynb` — **eksperimentalno** rangiranje zona po
  prioritetu za terensku provjeru (vidi odjeljak niže)

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
  mapping.py                izrada Folium karte (uklj. kartu hipoteza)
  report.py                  generiranje Markdown izvještaja
  hipoteze.py               generiranje hipoteza o mogućim neistraženim zonama
notebooks/                Jupyter notebooks za istraživanje
scripts/                  CLI skripte (izvještaj, izvoz u CSV)
tests/                    pytest testovi (data, hipoteze)
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

## Hipoteze o mogućim neistraženim lokalitetima (`stecak_lab.hipoteze`)

**⚠️ Eksperimentalno, spekulativno — ne otkriva stvarne lokalitete.**
Modul generira grubo, heurističko rangiranje zona po prioritetu za
*terensku* provjeru, na osnovu dva faktora:

1. **Dokumentirane niskoprioritetne zone** — regije gdje je iz
   historijskih razloga izrazito malo vjerojatno da postoje stećci.
   Trenutno sadrži Unsko-sanski kanton (Bihać i okolina): kroz cijeli
   srednji vijek dio Ugarsko-hrvatskog kraljevstva, izvan teritorija
   bosanske države i Crkve bosanske s kojima je pojava stećaka gotovo
   isključivo povezana — ne "Japodi" (željezno doba, kulturno nepovezano
   sa stećcima), kako bi se moglo pretpostaviti po imenu regije.
2. **Analiza praznina** — udaljenost do najbližeg poznatog lokaliteta u
   katalogu, s pragovima koji **nisu kalibrirani na stvarnim podacima**
   (takvi podaci o neotkrivenim lokalitetima po definiciji ne postoje).

Prema istraživanju provedenom za ovaj projekt, ne postoji objavljen,
akademski validiran prediktivni GIS model specifično za stećke; najbliži
relevantan rad je Saša Čaval (2021), *"Social landscapes as multicultural
spaces: stećci in Bosnia and Herzegovina"*, Antiquity 95(380), i SOLMUS
projekt (ZRC SAZU / University of Reading). Ovaj modul je grub prvi
pokušaj inspiriran tom idejom, ne njena reprodukcija.

```python
from stecak_lab.hipoteze import oceni_kandidat, generiraj_kandidat_zone
from stecak_lab.mapping import build_hipoteze_map

oceni_kandidat(44.8167, 15.87)   # Bihać -> ocjena 0 (dokumentirana isključena zona)
kandidati = generiraj_kandidat_zone()  # top-N kandidatskih zona za teren
build_hipoteze_map()             # karta: poznati lokaliteti + isključena zona + kandidati
```

Puna metodologija, izvori i upute za tumačenje rezultata su u docstringu
`stecak_lab/hipoteze.py` i u `notebooks/04_hipoteze_novih_lokaliteta.ipynb`
— pročitajte prije korištenja izlaza. Svaki dodatni faktor (npr. nova
isključena/uključena zona) treba vlastito historijsko/arheološko
obrazloženje i izvore, po istom standardu kao postojeća zona.

## Tehnologije

Python 3.9+ · pandas · Folium · Matplotlib · Jupyter · pytest
