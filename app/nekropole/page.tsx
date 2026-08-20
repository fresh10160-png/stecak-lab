import { getSveNekropole, getDrzave, getSveMotive } from "@/lib/data";
import KatalogNekropola from "@/components/KatalogNekropola";

export const metadata = {
  title: "Katalog nekropola — Stećak Lab",
};

export default function NekropolePage() {
  const nekropole = getSveNekropole();
  const drzave = getDrzave();
  const motivi = getSveMotive();

  return (
    <div className="space-y-4">
      <div>
        <h1 className="font-serif text-2xl font-semibold text-moss-900">
          Katalog nekropola
        </h1>
        <p className="mt-1 text-sm text-stone-600">
          Pretražite i filtrirajte lokalitete po državi, motivu ili prisustvu
          natpisa.
        </p>
      </div>
      <KatalogNekropola nekropole={nekropole} drzave={drzave} motivi={motivi} />
    </div>
  );
}
