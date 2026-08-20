export const metadata = {
  title: "O projektu — Stećak Lab",
};

export default function OProjektuPage() {
  return (
    <div className="max-w-3xl space-y-8">
      <div>
        <h1 className="font-serif text-2xl font-semibold text-moss-900">O projektu</h1>
        <p className="mt-2 text-sm text-stone-700">
          Stećak Lab je otvoreni digitalni laboratorij namijenjen istraživačima,
          studentima i entuzijastima koji proučavaju stećke — srednjovjekovne
          nadgrobne spomenike rasprostranjene po Bosni i Hercegovini, Hrvatskoj,
          Crnoj Gori i Srbiji, upisane 2016. na UNESCO-vu listu svjetske baštine
          pod nazivom &ldquo;Stećci — srednjovjekovni nadgrobni spomenici&rdquo;.
        </p>
      </div>

      <section>
        <h2 className="font-serif text-lg font-semibold text-moss-900">
          Što alat trenutno nudi
        </h2>
        <ul className="mt-3 list-inside list-disc space-y-1 text-sm text-stone-700">
          <li>Katalog nekropola s pretragom i filterima (država, motiv, natpisi)</li>
          <li>Interaktivnu kartu lokaliteta</li>
          <li>Detaljnu stranicu za svaku nekropolu (oblici, motivi, izvori)</li>
          <li>Statistički pregled po državama i učestalosti motiva</li>
        </ul>
      </section>

      <section>
        <h2 className="font-serif text-lg font-semibold text-moss-900">
          Napomena o podacima
        </h2>
        <p className="mt-2 text-sm text-stone-700">
          Podaci u <code className="rounded bg-stone-100 px-1">data/necropoles.json</code>{" "}
          su polazni, ilustrativni skup (šest poznatih lokaliteta) namijenjen
          pokretanju istraživanja i demonstraciji alata. Koordinate su okvirne
          (na razini naselja), a brojevi stećaka i opisi trebaju biti provjereni
          i dopunjeni terenskim podacima, konzervatorskom dokumentacijom i
          stručnom literaturom (npr. izdanja Komisije za očuvanje nacionalnih
          spomenika BiH, monografije Š. Bešlagića, dokumentacija UNESCO-ove
          nominacije) prije korištenja u ozbiljnom istraživačkom radu.
        </p>
      </section>

      <section>
        <h2 className="font-serif text-lg font-semibold text-moss-900">
          Kako proširiti katalog
        </h2>
        <p className="mt-2 text-sm text-stone-700">
          Dodajte novi objekt u <code className="rounded bg-stone-100 px-1">data/necropoles.json</code>{" "}
          prateći strukturu definiranu u <code className="rounded bg-stone-100 px-1">lib/types.ts</code>.
          Nova nekropola se automatski pojavljuje u katalogu, na karti i u
          statistici bez dodatnih izmjena koda.
        </p>
      </section>
    </div>
  );
}
