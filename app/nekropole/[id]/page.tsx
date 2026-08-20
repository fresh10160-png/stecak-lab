import { notFound } from "next/navigation";
import Link from "next/link";
import { getNekropolaBySlug, getSveNekropole } from "@/lib/data";
import NecropoleMap from "@/components/NecropoleMapClient";

export function generateStaticParams() {
  return getSveNekropole().map((n) => ({ id: n.id }));
}

export function generateMetadata({ params }: { params: { id: string } }) {
  const n = getNekropolaBySlug(params.id);
  return { title: n ? `${n.naziv} — Stećak Lab` : "Nekropola — Stećak Lab" };
}

export default function DetaljNekropolePage({
  params,
}: {
  params: { id: string };
}) {
  const nekropola = getNekropolaBySlug(params.id);
  if (!nekropola) notFound();

  return (
    <div className="space-y-8">
      <div>
        <Link href="/nekropole" className="text-sm text-moss-700 underline">
          ← natrag na katalog
        </Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <h1 className="font-serif text-3xl font-semibold text-moss-900">
            {nekropola.naziv}
          </h1>
          {nekropola.unescoLokalitet && (
            <span className="rounded-full bg-moss-100 px-3 py-1 text-xs font-medium text-moss-800">
              UNESCO svjetska baština
            </span>
          )}
        </div>
        <p className="mt-1 text-stone-600">
          {nekropola.opcina}, {nekropola.entitet}, {nekropola.drzava}
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 space-y-6">
          <section>
            <h2 className="font-serif text-lg font-semibold text-moss-900">Opis</h2>
            <p className="mt-2 text-sm leading-relaxed text-stone-700">
              {nekropola.opis}
            </p>
          </section>

          <section className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Statistika naslov="Period" vrijednost={nekropola.period} />
            <Statistika
              naslov="Broj stećaka"
              vrijednost={`≈ ${nekropola.brojStecakaPribl}`}
            />
            <Statistika
              naslov="Natpisi"
              vrijednost={nekropola.natpisi ? "da" : "ne"}
            />
            <Statistika naslov="Oblici" vrijednost={nekropola.oblici.join(", ")} />
          </section>

          <section>
            <h2 className="font-serif text-lg font-semibold text-moss-900">
              Motivi i ornamentika
            </h2>
            <div className="mt-2 flex flex-wrap gap-2">
              {nekropola.motivi.map((m) => (
                <span
                  key={m}
                  className="rounded-full bg-moss-50 px-3 py-1 text-sm text-moss-800"
                >
                  {m}
                </span>
              ))}
            </div>
          </section>

          <section>
            <h2 className="font-serif text-lg font-semibold text-moss-900">Izvori</h2>
            <ul className="mt-2 list-inside list-disc text-sm text-stone-600">
              {nekropola.izvori.map((izvor) => (
                <li key={izvor}>{izvor}</li>
              ))}
            </ul>
            {nekropola.napomenaPodataka && (
              <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
                {nekropola.napomenaPodataka}
              </p>
            )}
          </section>
        </div>

        <div className="overflow-hidden rounded-xl border border-moss-200 shadow-sm">
          <NecropoleMap nekropole={[nekropola]} />
        </div>
      </div>
    </div>
  );
}

function Statistika({ naslov, vrijednost }: { naslov: string; vrijednost: string }) {
  return (
    <div className="rounded-lg border border-moss-100 bg-white p-3">
      <div className="text-xs uppercase tracking-wide text-stone-500">{naslov}</div>
      <div className="mt-1 text-sm font-medium text-moss-900">{vrijednost}</div>
    </div>
  );
}
