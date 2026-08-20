import { getSveNekropole } from "@/lib/data";
import NecropoleMap from "@/components/NecropoleMapClient";

export const metadata = {
  title: "Karta nekropola — Stećak Lab",
};

export default function KartaStranica() {
  const nekropole = getSveNekropole();

  return (
    <div className="space-y-4">
      <div>
        <h1 className="font-serif text-2xl font-semibold text-moss-900">
          Karta nekropola
        </h1>
        <p className="mt-1 text-sm text-stone-600">
          Geografski raspored katalogiziranih lokaliteta. Kliknite na oznaku za
          osnovne podatke i poveznicu na detaljnu stranicu.
        </p>
      </div>
      <div className="overflow-hidden rounded-xl border border-moss-200 shadow-sm">
        <NecropoleMap nekropole={nekropole} />
      </div>
    </div>
  );
}
