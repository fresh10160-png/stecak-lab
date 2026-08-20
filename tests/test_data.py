from stecak_lab.data import (
    broj_nedokumentiranih,
    filter_necropoles,
    load_dataframe,
    stats_motiva,
    stats_po_drzavi,
    ukupan_broj_stecaka,
)


def test_load_dataframe_ima_redove():
    df = load_dataframe()
    assert len(df) > 0
    assert {"id", "naziv", "drzava", "lat", "lng", "motivi"}.issubset(df.columns)


def test_ukupan_broj_stecaka_nije_negativan():
    df = load_dataframe()
    assert ukupan_broj_stecaka(df) >= 0


def test_broj_nedokumentiranih_ne_prelazi_ukupan_broj_redova():
    df = load_dataframe()
    assert 0 <= broj_nedokumentiranih(df) <= len(df)


def test_stats_po_drzavi_pokriva_sve_drzave():
    df = load_dataframe()
    stats = stats_po_drzavi(df)
    assert set(stats["drzava"]) == set(df["drzava"].unique())


def test_stats_motiva_vraca_pozitivne_brojeve():
    df = load_dataframe()
    motivi = stats_motiva(df)
    assert (motivi > 0).all()


def test_filter_necropoles_po_drzavi():
    df = load_dataframe()
    bih = filter_necropoles(df, drzava="Bosna i Hercegovina")
    assert len(bih) > 0
    assert (bih["drzava"] == "Bosna i Hercegovina").all()


def test_filter_necropoles_samo_sa_natpisima():
    df = load_dataframe()
    sa_natpisima = filter_necropoles(df, samo_sa_natpisima=True)
    assert (sa_natpisima["natpisi"]).all()
