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
  brojStecakaPribl: number;
  oblici: string[];
  motivi: string[];
  natpisi: boolean;
  period: string;
  opis: string;
  izvori: string[];
  napomenaPodataka?: string;
}
