"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import type { Nekropola } from "@/lib/types";

export default function KatalogNekropola({
  nekropole,
  drzave,
  motivi,
}: {
  nekropole: Nekropola[];
  drzave: string[];
  motivi: string[];
}) {
  const [upit, setUpit] = useState("");
  const [drzava, setDrzava] = useState("");
  const [motiv, setMotiv] = useState("");
  const [samoSaNatpisima, setSamoSaNatpisima] = useState(false);

  const filtrirano = useMemo(() => {
    return nekropole.filter((n) => {
      if (drzava && n.drzava !== drzava) return false;
      if (motiv && !n.motivi.includes(motiv)) return false;
      if (samoSaNatpisima && !n.natpisi) return false;
      if (upit) {
        const q = upit.toLowerCase();
        const haystack = `${n.naziv} ${n.opcina} ${n.drzava} ${n.opis}`.toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      return true;
    });
  }, [nekropole, upit, drzava, motiv, samoSaNatpisima]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-3 rounded-xl border border-moss-200 bg-white p-4">
        <input
          type="text"
          placeholder="Pretraži po nazivu, općini, opisu…"
          value={upit}
          onChange={(e) => setUpit(e.target.value)}
          className="min-w-[220px] flex-1 rounded-md border border-moss-200 px-3 py-2 text-sm focus:border-moss-500 focus:outline-none"
        />
        <select
          value={drzava}
          onChange={(e) => setDrzava(e.target.value)}
          className="rounded-md border border-moss-200 px-3 py-2 text-sm"
        >
          <option value="">Sve države</option>
          {drzave.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
        <select
          value={motiv}
          onChange={(e) => setMotiv(e.target.value)}
          className="rounded-md border border-moss-200 px-3 py-2 text-sm"
        >
          <option value="">Svi motivi</option>
          {motivi.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
        <label className="flex items-center gap-2 text-sm text-stone-700">
          <input
            type="checkbox"
            checked={samoSaNatpisima}
            onChange={(e) => setSamoSaNatpisima(e.target.checked)}
            className="h-4 w-4 rounded border-moss-300"
          />
          samo s natpisima
        </label>
      </div>

      <p className="text-sm text-stone-500">
        Prikazano {filtrirano.length} od {nekropole.length} nekropola.
      </p>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {filtrirano.map((n) => (
          <Link
            key={n.id}
            href={`/nekropole/${n.id}`}
            className="rounded-xl border border-moss-200 bg-white p-5 transition hover:border-moss-400 hover:shadow-md"
          >
            <div className="flex items-start justify-between">
              <h3 className="font-serif text-lg font-semibold text-moss-900">
                {n.naziv}
              </h3>
              {n.unescoLokalitet && (
                <span className="rounded-full bg-moss-100 px-2 py-0.5 text-xs font-medium text-moss-800">
                  UNESCO
                </span>
              )}
            </div>
            <div className="mt-1 text-sm text-stone-600">
              {n.opcina}, {n.drzava}
            </div>
            <p className="mt-2 line-clamp-2 text-sm text-stone-600">{n.opis}</p>
            <div className="mt-3 flex flex-wrap gap-1">
              {n.motivi.slice(0, 3).map((m) => (
                <span
                  key={m}
                  className="rounded-full bg-stone-100 px-2 py-0.5 text-xs text-stone-600"
                >
                  {m}
                </span>
              ))}
            </div>
          </Link>
        ))}
        {filtrirano.length === 0 && (
          <p className="col-span-2 rounded-xl border border-dashed border-moss-200 p-8 text-center text-sm text-stone-500">
            Nema rezultata za odabrane filtere.
          </p>
        )}
      </div>
    </div>
  );
}
