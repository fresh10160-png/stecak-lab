"""Izvezi data/necropoles.json u data/necropoles.csv (za tablične alate / GIS)."""

from pathlib import Path

from stecak_lab.data import load_dataframe

OUT = Path(__file__).resolve().parent.parent / "data" / "necropoles.csv"


def main() -> None:
    df = load_dataframe().copy()
    df["oblici"] = df["oblici"].apply(lambda x: "; ".join(x))
    df["motivi"] = df["motivi"].apply(lambda x: "; ".join(x))
    df["izvori"] = df["izvori"].apply(lambda x: " | ".join(x))
    df.to_csv(OUT, index=False)
    print(f"Izvezeno {len(df)} redova u {OUT}")


if __name__ == "__main__":
    main()
