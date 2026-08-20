import necropoleData from "@/data/necropoles.json";
import type { Nekropola } from "./types";

const nekropole = necropoleData as Nekropola[];

export function getSveNekropole(): Nekropola[] {
  return nekropole;
}

export function getNekropolaBySlug(id: string): Nekropola | undefined {
  return nekropole.find((n) => n.id === id);
}

export function getDrzave(): string[] {
  return Array.from(new Set(nekropole.map((n) => n.drzava))).sort();
}

export function getSveMotive(): string[] {
  return Array.from(new Set(nekropole.flatMap((n) => n.motivi))).sort();
}

export function getSveOblike(): string[] {
  return Array.from(new Set(nekropole.flatMap((n) => n.oblici))).sort();
}

export function filtrirajNekropole(params: {
  upit?: string;
  drzava?: string;
  motiv?: string;
  samoSaNatpisima?: boolean;
}): Nekropola[] {
  const { upit, drzava, motiv, samoSaNatpisima } = params;
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
}

export function getStatistikaPoDrzavi() {
  const map = new Map<string, { drzava: string; brojNekropola: number; brojStecaka: number }>();
  for (const n of nekropole) {
    const entry = map.get(n.drzava) ?? { drzava: n.drzava, brojNekropola: 0, brojStecaka: 0 };
    entry.brojNekropola += 1;
    entry.brojStecaka += n.brojStecakaPribl ?? 0;
    map.set(n.drzava, entry);
  }
  return Array.from(map.values());
}

export function getStatistikaMotiva() {
  const map = new Map<string, number>();
  for (const n of nekropole) {
    for (const m of n.motivi) {
      map.set(m, (map.get(m) ?? 0) + 1);
    }
  }
  return Array.from(map.entries())
    .map(([motiv, broj]) => ({ motiv, broj }))
    .sort((a, b) => b.broj - a.broj);
}

export function getUkupanBrojStecaka(): number {
  return nekropole.reduce((sum, n) => sum + (n.brojStecakaPribl ?? 0), 0);
}

/** Broj nekropola kojima broj stećaka nije dokumentiran u dostupnim izvorima. */
export function getBrojNedokumentiranih(): number {
  return nekropole.filter((n) => n.brojStecakaPribl == null).length;
}
