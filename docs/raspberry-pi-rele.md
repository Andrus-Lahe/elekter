# Raspberry Pi 3 Model B ja SSR-relee (LCDK-480DA)

## Raspberry Pi 3 Model B (2015)

- Sisseehitatud WiFi: 2,4 GHz, 802.11n (5 GHz-t ei toeta). Bluetooth 4.1.
- Ethernet 10/100 Mbit/s. 40-pinnine GPIO päis.
- GPIO signaal on **3,3 V**. Sisendisse ei tohi anda 5 V.
- Üks GPIO pinn annab ohutult umbes 16 mA, kõik pinnid kokku umbes 50 mA.
- 5 V on olemas ainult toitepinnidena (2 ja 4), neid ei saa programmiga lülitada.

## GPIO pinnid (füüsiline number: nimi)

| Pinn | Nimi | Pinn | Nimi |
|------|------|------|------|
| 1 | 3V3 | 2 | 5V |
| 3 | GPIO2 (SDA) | 4 | 5V |
| 5 | GPIO3 (SCL) | 6 | GND |
| 7 | GPIO4 | 8 | GPIO14 (TXD) |
| 9 | GND | 10 | GPIO15 (RXD) |
| 11 | GPIO17 | 12 | GPIO18 (PWM) |
| 13 | GPIO27 | 14 | GND |
| 15 | GPIO22 | 16 | GPIO23 |
| 17 | 3V3 | 18 | GPIO24 |
| 19 | GPIO10 (MOSI) | 20 | GND |
| 21 | GPIO9 (MISO) | 22 | GPIO25 |
| 23 | GPIO11 (SCLK) | 24 | GPIO8 (CE0) |
| 25 | GND | 26 | GPIO7 (CE1) |
| 27 | ID_SD | 28 | ID_SC |
| 29 | GPIO5 | 30 | GND |
| 31 | GPIO6 | 32 | GPIO12 (PWM) |
| 33 | GPIO13 (PWM) | 34 | GND |
| 35 | GPIO19 | 36 | GPIO16 |
| 37 | GPIO26 | 38 | GPIO20 |
| 39 | GND | 40 | GPIO21 |

Pinn 1 on SD-kaardi poolses otsas. Tabelis on füüsilised numbrid (1–40); programmis kasutatakse tavaliselt GPIO numbrit (nt 17).

## SSR-relee LCDK-480**DA** (DC-AC)

- Juhtpinge: 3–32 V DC. Max sisendvool: 12 mA.
- Kindel sisselülitus alates 2,8 V DC, väljalülitus alla 1,5 V DC.
- Koormus: 24–480 V AC (ainult vahelduvvool). Voolutugevus on mudeli nimes (nt `48025DA` = 25 A).
- Max pingelang sisselülitatud olekus: kuni 1,5 V (25 A juures umbes 37 W soojust, vajab radiaatorit).
- Max lekkevool väljalülitatud olekus: 12 mA.
- Töötemperatuur -20 kuni 80 °C.

## Ühendus

- GPIO17 (pinn 11) → relee klemm 3 (**+**)
- GND (pinn 9) → relee klemm 4 (**−**)
- Järjestikust takistit ei ole vaja, sest relee piirab sisendvoolu ise.
- Koormusklemmid 1 ja 2 on eraldi ahelas (230 V).
- Pi käivitumisel on GPIO tavaliselt LOW, seega relee on algul VÄLJAS (sobib reegliga "tõrke korral lüliti välja").

## Ohutus (230 V)

- Pane ahelasse kaitse ja koormuse pool kinnisesse korpusesse.
- Väljalülitatud olekus võib koormusel olla kuni 12 mA lekkevool, väikese koormusega (nt LED) võib see tööle jääda.
- Võrgupinge juhtmeid ei tohi ühendada ilma kogemuseta.
