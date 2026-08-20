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
          su polazni skup od 23 lokaliteta (20 iz Bosne i Hercegovine te po
          jedan iz Hrvatske, Crne Gore i Srbije), prikupljen web-pretragom
          javno dostupnih izvora (Wikipedia, stecciwh.org, kons.gov.ba,
          UNESCO-ova dokumentacija, lokalni mediji). Gdje izvori nisu naveli
          točan broj stećaka, oblike ili motive, polje je ostavljeno prazno
          (<code className="rounded bg-stone-100 px-1">null</code> / [ ]) umjesto
          nagađanja — u UI-ju se to prikazuje kao &ldquo;nije dokumentirano&rdquo;.
          Koordinate su za većinu lokaliteta na razini općine/naselja (nije
          korišten GPS s terena), pa prije ozbiljnog istraživačkog rada podatke
          treba dopuniti terenskim mjerenjima, konzervatorskom dokumentacijom
          i stručnom literaturom (npr. monografije Š. Bešlagića). Svaki zapis
          navodi izvor(e) korištene za taj lokalitet.
        </p>
      </section>

      <section>
        <h2 className="font-serif text-lg font-semibold text-moss-900">
          O upitu za lokalitet &ldquo;Japod&rdquo;
        </h2>
        <p className="mt-2 text-sm text-stone-700">
          Istraživanje nije pronašlo nikakvu nekropolu stećaka pod nazivom
          &ldquo;Japod&rdquo;, &ldquo;Japode&rdquo; ili &ldquo;Japoda&rdquo; — ni na UNESCO-voj listi
          ni u evidenciji Komisije za očuvanje nacionalnih spomenika BiH.
          Japodi (Iapydes) su bili ilirsko pleme željeznog doba (prije nove
          ere) nastanjeno u dolini Une oko Bihaća — potpuno druga kultura i
          period od srednjovjekovnih stećaka (12–16. stoljeće). Naziv se danas
          koristi npr. za turističko odredište &ldquo;Japodski otoci&rdquo; kod Bihaća,
          koje nema veze s nadgrobnim spomenicima. Lokalitet stoga namjerno
          nije dodan u katalog kako se ne bi unijeli izmišljeni podaci.
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
