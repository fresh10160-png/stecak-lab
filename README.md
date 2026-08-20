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

`data/necropoles.json` sadrži **polazni, ilustrativni skup** od nekoliko
poznatih nekropola (npr. Radimlja, Boljuni) namijenjen pokretanju
istraživanja i demonstraciji alata. Koordinate su okvirne (na razini
naselja), a brojevi stećaka i opisi trebaju biti provjereni i dopunjeni
terenskim podacima i stručnom literaturom prije ozbiljne istraživačke
upotrebe — vidi stranicu **O projektu** u aplikaciji.

Novi lokalitet dodaje se kao novi objekt u `data/necropoles.json`, prema
strukturi definiranoj u `lib/types.ts`; automatski se pojavljuje u katalogu,
na karti i u statistici.

## Tehnologije

Next.js 14 (App Router) · TypeScript · Tailwind CSS · react-leaflet ·
Recharts
