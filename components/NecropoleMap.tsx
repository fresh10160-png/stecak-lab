"use client";

import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet";
import L from "leaflet";
import Link from "next/link";
import type { Nekropola } from "@/lib/types";

const ikona = new L.Icon({
  iconUrl:
    "data:image/svg+xml;base64," +
    btoa(
      `<svg xmlns="http://www.w3.org/2000/svg" width="28" height="40" viewBox="0 0 28 40">
        <path d="M14 0C6.3 0 0 6.3 0 14c0 10.5 14 26 14 26s14-15.5 14-26C28 6.3 21.7 0 14 0z" fill="#4f653d"/>
        <circle cx="14" cy="14" r="6" fill="#f4f6f2"/>
      </svg>`
    ),
  iconSize: [28, 40],
  iconAnchor: [14, 40],
  popupAnchor: [0, -36],
});

export default function NecropoleMap({
  nekropole,
  visina = "h-[560px]",
}: {
  nekropole: Nekropola[];
  visina?: string;
}) {
  const centar: [number, number] =
    nekropole.length === 1
      ? [nekropole[0].koordinate.lat, nekropole[0].koordinate.lng]
      : [43.4, 18.2];
  const zoom = nekropole.length === 1 ? 11 : 7;

  return (
    <MapContainer
      center={centar}
      zoom={zoom}
      className={`${visina} w-full`}
      scrollWheelZoom
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> suradnici'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {nekropole.map((n) => (
        <Marker key={n.id} position={[n.koordinate.lat, n.koordinate.lng]} icon={ikona}>
          <Popup>
            <div className="text-sm">
              <div className="font-semibold">{n.naziv}</div>
              <div className="text-stone-600">
                {n.opcina}, {n.drzava}
              </div>
              <div className="mt-1">≈ {n.brojStecakaPribl} stećaka</div>
              <Link
                href={`/nekropole/${n.id}`}
                className="mt-2 inline-block text-moss-700 underline"
              >
                Detalji lokaliteta →
              </Link>
            </div>
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  );
}
