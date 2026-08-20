# Stećak Lab

Istraživački laboratorij za katalogizaciju, kartiranje i analizu **stećaka** —
srednjovjekovnih nadgrobnih spomenika upisanih 2016. na UNESCO-vu listu
svjetske baštine, rasprostranjenih po Bosni i Hercegovini, Hrvatskoj, Crnoj
Gori i Srbiji.

Web aplikacija izrađena u Next.js-u (TypeScript, Tailwind CSS, Leaflet,
Recharts) koja nudi:

- **Katalog nekropola** — pretraga i filtriranje po državi, motivu, natpisima
- **Kartu** lokaliteta (react-leaflet / OpenStreetMap)
- **Stranicu detalja** za svaku nekropolu (oblici, motivi, izvori, napomene)
- **Statistiku** — broj stećaka po državi, učestalost motiva

## Pokretanje

```bash
npm install
npm run dev
```

Aplikacija je dostupna na `http://localhost:3000`.

Za produkcijski build:

```bash
npm run build
npm start
```

## Struktura projekta

```
app/                 stranice (Next.js App Router)
  page.tsx           početna / pregled
  karta/              interaktivna karta
  nekropole/           katalog + [id] stranica detalja
  statistika/          grafovi i agregati
  o-projektu/          metodologija i napomene o podacima
components/          UI komponente (karta, katalog, grafovi, navigacija)
lib/                 tipovi i pristup podacima (lib/types.ts, lib/data.ts)
data/necropoles.json  polazni skup podataka o nekropolama
```

## Podaci

`data/necropoles.json` sadrži **polazni skup od 23 lokaliteta** (20 iz Bosne
i Hercegovine — uključujući sve zasad prikupljene UNESCO-om upisane BiH
nekropole — te po jedan iz Hrvatske, Crne Gore i Srbije), prikupljen
web-pretragom javno dostupnih izvora. Koordinate su za većinu lokaliteta na
razini općine/naselja (nisu terenski GPS-mjerene), a polja koja izvori nisu
naveli (broj stećaka, oblici, motivi) ostavljena su prazna/`null` umjesto
nagađanja. Svaki zapis navodi korištene izvore. Prije ozbiljne istraživačke
upotrebe podatke treba dopuniti terenskim mjerenjima i stručnom literaturom
— vidi stranicu **O projektu** u aplikaciji.

Novi lokalitet dodaje se kao novi objekt u `data/necropoles.json`, prema
strukturi definiranoj u `lib/types.ts`; automatski se pojavljuje u katalogu,
na karti i u statistici.

## Tehnologije

Next.js 14 (App Router) · TypeScript · Tailwind CSS · react-leaflet ·
Recharts
