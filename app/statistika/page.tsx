import { getStatistikaPoDrzavi, getStatistikaMotiva, getUkupanBrojStecaka, getSveNekropole } from "@/lib/data";
import { GrafPoDrzavi, GrafMotiva } from "@/components/StatistikaGrafovi";

export const metadata = {
  title: "Statistika — Stećak Lab",
};

export default function StatistikaPage() {
  const poDrzavi = getStatistikaPoDrzavi();
  const motivi = getStatistikaMotiva();
  const ukupno = getUkupanBrojStecaka();
  const nekropole = getSveNekropole();
  const saNatpisima = nekropole.filter((n) => n.natpisi).length;

  return (
    <div className="space-y-10">
      <div>
        <h1 className="font-serif text-2xl font-semibold text-moss-900">
          Statistički pregled
        </h1>
        <p className="mt-1 text-sm text-stone-600">
          Agregirani podaci izvedeni iz kataloga — koristno kao polazna točka za
          usporednu analizu regija, motiva i epigrafike.
        </p>
      </div>

      <section className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <MiniStat naslov="Ukupno stećaka (okvirno)" vrijednost={ukupno.toLocaleString("hr-HR")} />
        <MiniStat naslov="Nekropola u katalogu" vrijednost={String(nekropole.length)} />
        <MiniStat
          naslov="Nekropole s natpisima"
          vrijednost={`${saNatpisima} / ${nekropole.length}`}
        />
      </section>

      <section>
        <h2 className="font-serif text-lg font-semibold text-moss-900">
          Broj stećaka po državi
        </h2>
        <div className="mt-4 rounded-xl border border-moss-200 bg-white p-4">
          <GrafPoDrzavi podaci={poDrzavi} />
        </div>
      </section>

      <section>
        <h2 className="font-serif text-lg font-semibold text-moss-900">
          Učestalost motiva u katalogu
        </h2>
        <div className="mt-4 rounded-xl border border-moss-200 bg-white p-4">
          <GrafMotiva podaci={motivi} />
        </div>
      </section>
    </div>
  );
}

function MiniStat({ naslov, vrijednost }: { naslov: string; vrijednost: string }) {
  return (
    <div className="rounded-xl border border-moss-200 bg-white p-5 text-center">
      <div className="font-serif text-2xl font-semibold text-moss-800">{vrijednost}</div>
      <div className="mt-1 text-xs uppercase tracking-wide text-stone-500">{naslov}</div>
    </div>
  );
}
