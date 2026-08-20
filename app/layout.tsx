import type { Metadata } from "next";
import "./globals.css";
import "leaflet/dist/leaflet.css";
import NavBar from "@/components/NavBar";

export const metadata: Metadata = {
  title: "Stećak Lab — istraživački laboratorij za stećke",
  description:
    "Digitalni laboratorij za katalogizaciju, kartiranje i analizu stećaka — srednjovjekovnih nadgrobnih spomenika upisanih na UNESCO-vu listu svjetske baštine.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="hr">
      <body className="min-h-screen font-sans">
        <NavBar />
        <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
        <footer className="mx-auto mt-16 max-w-6xl px-4 pb-8 text-xs text-stone-500">
          Stećak Lab — istraživački alat otvorenog koda. Podaci su polazni skup za
          demonstraciju i istraživanje; prije objave provjerite ih uz primarnu i
          stručnu literaturu.
        </footer>
      </body>
    </html>
  );
}
