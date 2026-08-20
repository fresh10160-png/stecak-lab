export interface Koordinate {
  lat: number;
  lng: number;
}

export interface Nekropola {
  id: string;
  naziv: string;
  drzava: string;
  entitet: string;
  opcina: string;
  koordinate: Koordinate;
  unescoLokalitet: boolean;
  /** Približan broj stećaka; null ako broj nije dokumentiran u dostupnim izvorima. */
  brojStecakaPribl: number | null;
  oblici: string[];
  motivi: string[];
  natpisi: boolean;
  period: string;
  opis: string;
  izvori: string[];
  napomenaPodataka?: string;
}
