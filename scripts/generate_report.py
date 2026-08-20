"""CLI: generiraj kartu i statistički izvještaj iz kataloga nekropola."""

from stecak_lab.mapping import save_map
from stecak_lab.report import save_report


def main() -> None:
    karta = save_map()
    izvjestaj = save_report()
    print(f"Karta:     {karta}")
    print(f"Izvještaj: {izvjestaj}")


if __name__ == "__main__":
    main()
