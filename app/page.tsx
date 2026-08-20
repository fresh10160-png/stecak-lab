import Link from "next/link";
import {
  getSveNekropole,
  getUkupanBrojStecaka,
  getDrzave,
  getStatistikaMotiva,
} from "@/lib/data";

export default function PocetnaStranica() {
  const nekropole = getSveNekropole();
  const drzave = getDrzave();
  const ukupnoStecaka = getUkupanBrojStecaka();
  const topMotivi = getStatistikaMotiva().slice(0, 5);

  const kartice = [
    { broj: nekropole.length, opis: "katalogiziranih nekropola" },
    { broj: ukupnoStecaka.toLocaleString("hr-HR"), opis: "stećaka (okvirno)" },
    { broj: drzave.length, opis: "države u regiji" },
  ];

  return (
    <div className="space-y-12">
      <section className="rounded-2xl bg-moss-800 px-8 py-12 text-moss-50">
        <h1 className="font-serif text-3xl font-semibold sm:text-4xl">
          Laboratorij za istraživanje stećaka
        </h1>
        <p className="mt-4 max-w-2xl text-moss-100">
          Digitalni alat za katalogizaciju nekropola, kartiranje lokaliteta,
          praćenje motiva i natpisa te statističku analizu srednjovjekovnih
          nadgrobnih spomenika (stećaka) upisanih na UNESCO-vu listu svjetske
          baštine u Bosni i Hercegovini, Hrvatskoj, Crnoj Gori i Srbiji.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link
            href="/karta"
            className="rounded-lg bg-moss-50 px-4 py-2 text-sm font-medium text-moss-900 hover:bg-white"
          >
            Otvori kartu
          </Link>
          <Link
            href="/nekropole"
            className="rounded-lg border border-moss-300 px-4 py-2 text-sm font-medium hover:bg-moss-700"
          >
            Pregledaj katalog
          </Link>
        </div>
      </section>

      <section className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {kartice.map((k) => (
          <div
            key={k.opis}
            className="rounded-xl border border-moss-200 bg-white p-6 text-center shadow-sm"
          >
            <div className="font-serif text-3xl font-semibold text-moss-800">
              {k.broj}
            </div>
            <div className="mt-1 text-sm text-stone-600">{k.opis}</div>
          </div>
        ))}
      </section>

      <section>
        <h2 className="font-serif text-xl font-semibold text-moss-900">
          Najčešći motivi u katalogu
        </h2>
        <ul className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
          {topMotivi.map((m) => (
            <li
              key={m.motiv}
              className="flex items-center justify-between rounded-lg border border-moss-100 bg-white px-4 py-2 text-sm"
            >
              <span className="capitalize">{m.motiv}</span>
              <span className="rounded-full bg-moss-100 px-2 py-0.5 text-xs font-medium text-moss-800">
                {m.broj} lokaliteta
              </span>
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h2 className="font-serif text-xl font-semibold text-moss-900">
          Metodologija istraživanja
        </h2>
        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
          {[
            {
              naslov: "1. Terensko dokumentiranje",
              tekst:
                "Bilježenje lokacije (GPS), broja i oblika stećaka (ploča, sanduk, sljemenjak, stub), stanja očuvanosti i konteksta nekropole.",
            },
            {
              naslov: "2. Analiza ornamentike i natpisa",
              tekst:
                "Klasifikacija motiva (spirale, rozete, prizori lova, heraldički znakovi) i transkripcija epigrafskih natpisa (bosančica).",
            },
            {
              naslov: "3. Komparativna i statistička obrada",
              tekst:
                "Usporedba nekropola po regijama, motivima i periodima kroz katalog, kartu i statistički pregled u ovom laboratoriju.",
            },
          ].map((korak) => (
            <div
              key={korak.naslov}
              className="rounded-xl border border-moss-100 bg-white p-5"
            >
              <h3 className="font-medium text-moss-800">{korak.naslov}</h3>
              <p className="mt-2 text-sm text-stone-600">{korak.tekst}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
