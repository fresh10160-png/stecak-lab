from stecak_lab.data import load_dataframe
from stecak_lab.hipoteze import (
    generiraj_kandidat_zone,
    haversine_km,
    je_u_iskljucenoj_zoni,
    najbliza_nekropola,
    oceni_kandidat,
)

# Bihać, centar grada — primjer iz korisničkog upita o Japodima.
BIHAC_LAT, BIHAC_LNG = 44.8167, 15.87

# Radimlja (Stolac) — poznat, dokumentiran lokalitet iz kataloga.
RADIMLJA_LAT, RADIMLJA_LNG = 43.0736, 17.9536


def test_haversine_nula_za_istu_tacku():
    assert haversine_km(43.0, 18.0, 43.0, 18.0) == 0.0


def test_haversine_priblizno_tacan():
    # Stolac -> Mostar, poznata udaljenost ~30ak km zračne linije.
    km = haversine_km(43.0833, 17.9500, 43.3438, 17.8078)
    assert 25 < km < 40


def test_bihac_je_u_iskljucenoj_zoni():
    zona = je_u_iskljucenoj_zoni(BIHAC_LAT, BIHAC_LNG)
    assert zona is not None
    assert "unsko-sansk" in zona.naziv.lower()


def test_radimlja_okolina_nije_u_iskljucenoj_zoni():
    assert je_u_iskljucenoj_zoni(RADIMLJA_LAT, RADIMLJA_LNG) is None


def test_najbliza_nekropola_prepoznaje_sami_lokalitet():
    df = load_dataframe()
    rezultat = najbliza_nekropola(RADIMLJA_LAT, RADIMLJA_LNG, df)
    assert rezultat["naziv"] == "Radimlja"
    assert rezultat["udaljenost_km"] == 0.0


def test_oceni_kandidat_bihac_je_nula():
    rezultat = oceni_kandidat(BIHAC_LAT, BIHAC_LNG)
    assert rezultat["ocjena"] == 0
    assert rezultat["u_iskljucenoj_zoni"] is not None


def test_oceni_kandidat_na_poznatom_lokalitetu_je_niska():
    # Tačno na postojećoj nekropoli udaljenost je 0 -> izvan "slatke zone".
    rezultat = oceni_kandidat(RADIMLJA_LAT, RADIMLJA_LNG)
    assert rezultat["ocjena"] == 0.0


def test_oceni_kandidat_bonus_za_gomilu():
    lat, lng = RADIMLJA_LAT + 0.15, RADIMLJA_LNG + 0.15
    bez_bonusa = oceni_kandidat(lat, lng, na_prapovijesnoj_gomili=False)
    sa_bonusom = oceni_kandidat(lat, lng, na_prapovijesnoj_gomili=True)
    assert sa_bonusom["ocjena"] >= bez_bonusa["ocjena"]


def test_generiraj_kandidat_zone_izbjegava_iskljucenu_zonu():
    df = load_dataframe()
    kandidati = generiraj_kandidat_zone(df, korak_stupnjeva=0.25, top_n=10)
    if len(kandidati):
        for _, red in kandidati.iterrows():
            assert je_u_iskljucenoj_zoni(red["lat"], red["lng"]) is None


def test_generiraj_kandidat_zone_ima_ocjenu_i_obrazlozenje():
    df = load_dataframe()
    kandidati = generiraj_kandidat_zone(df, korak_stupnjeva=0.2, top_n=5)
    if len(kandidati):
        assert {"ocjena", "obrazlozenje", "lat", "lng"}.issubset(kandidati.columns)
        assert (kandidati["ocjena"] > 0).all()
