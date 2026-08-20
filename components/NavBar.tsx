import Link from "next/link";

const linkovi = [
  { href: "/", label: "Pregled" },
  { href: "/karta", label: "Karta" },
  { href: "/nekropole", label: "Nekropole" },
  { href: "/statistika", label: "Statistika" },
  { href: "/o-projektu", label: "O projektu" },
];

export default function NavBar() {
  return (
    <header className="border-b border-moss-200 bg-moss-900 text-moss-50">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-4">
        <Link href="/" className="font-serif text-xl font-semibold tracking-wide">
          Stećak Lab
          <span className="ml-2 text-xs font-sans font-normal text-moss-200">
            istraživački laboratorij
          </span>
        </Link>
        <nav className="flex flex-wrap gap-1 text-sm">
          {linkovi.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="rounded-md px-3 py-1.5 text-moss-100 transition hover:bg-moss-700 hover:text-white"
            >
              {l.label}
            </Link>
          ))}
        </nav>
      </div>
    </header>
  );
}
