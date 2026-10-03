# Elekter Raspberry Pi 3 Model B peale paigaldamine

Juhend viib valmis `elekter` rakenduse Raspberry Pi peale, nii et see käivitub ise ja juhib relee GPIO17 kaudu. Ühendamine ja ohutus on kirjas failis [raspberry-pi-rele.md](raspberry-pi-rele.md).

## Kokkuvõte (versioonid ja asukohad)

| Mis | Väärtus |
|---|---|
| Plaat | Raspberry Pi 3 Model B (1 GB RAM, WiFi 2,4 GHz pardal) |
| Operatsioonisüsteem | Raspberry Pi OS **64-bit** (Bookworm), Lite versioon sobib |
| Java Raspberry peal | **OpenJDK 17** või uuem (`openjdk-17-jre-headless`). Jar on kompileeritud `--release 17`-ga, nii et vanem kui 17 ei tööta |
| Java arvutis (ehitamiseks) | JDK 26 (testitud) Gradle'i jaoks, `JAVA_HOME=~/.jdks/openjdk-26.0.1` |
| Lisapakett | `gpiod` (libgpiod käsurea tööriistad, vajalik `gpioset` käsk) |
| Jar arvutis | `build/libs/elekter-0.0.1.jar` (`-plain.jar` faili ei ole vaja) |
| Jar Raspberry peal | `/opt/elekter/elekter.jar` |
| Teenuse fail Raspberry peal | `/etc/systemd/system/elekter.service` (allikas: `deploy/elekter.service`) |
| Töökaust Raspberry peal | `/opt/elekter` (siia salvestub `limit.txt` ja kausta `prices/` hinnafailid) |
| Veebileht | `http://<raspberry-ip>:8080/` |
| Relee | GPIO17 (BCM), füüsiline pinn 11, GND pinn 9 |
| Ajavöönd | `Europe/Tallinn` |

## 1. Raspberry ettevalmistus (üks kord)

1. Kirjuta SD-kaardile **Raspberry Pi OS Lite (64-bit)** programmiga *Raspberry Pi Imager*.
2. Imageri seadetes (hammasratta ikoon / "Edit settings") määra:
   - hostinimi (nt `elekter`), kasutajanimi ja parool (juhend eeldab kasutajat `pi`, vt märkust all);
   - WiFi nimi ja parool (Pi 3 toetab ainult 2,4 GHz);
   - ajavöönd `Europe/Tallinn`;
   - **SSH sisse** (Services → Enable SSH).
3. Pane SD-kaart Pi sisse, lülita toide peale ja oota ~1 minut.
4. Logi sisse arvutist: `ssh pi@elekter.local` (või `ssh pi@<ip>`, IP leiad ruuterist).

> Kui valisid kasutajanimeks midagi muud kui `pi`, muuda failis `deploy/elekter.service` rida `User=pi` ja asenda käskudes `pi@` oma kasutajanimega.

## 2. Vajaliku tarkvara paigaldus Raspberry peal

```
sudo apt update
sudo apt install -y openjdk-17-jre-headless gpiod
sudo timedatectl set-timezone Europe/Tallinn     # kui Imageris ei seadnud
```

Kontrolli:

```
java -version        # peab näitama 17 või uuemat
gpioset --version    # libgpiod versioon (Bookworm: 1.6, uuematel 2.x, mõlemad sobivad)
timedatectl          # "System clock synchronized: yes" ja "NTP service: active"
groups               # peab sisaldama "gpio"
```

Kellaaja sünkroniseerimise (NTP) teeb operatsioonisüsteem ise (`systemd-timesyncd`). Rakendus kontrollib kella lisaks ise (vt jaotist "Kella kontroll"). Raspberry'l puudub reaalaja kell, nii et pärast voolukatkestust on kell enne esimest sünkroonimist vale. Rakendus sõltub kellast hinna leidmisel, seepärast käivitub teenus alles pärast `time-sync.target`'it.

Täiendavat Gradle'i, Spring Booti ega Mavenit Raspberry peale **ei** paigaldata.

## 3. Kaust rakenduse jaoks (üks kord)

```
sudo mkdir -p /opt/elekter
sudo chown pi:pi /opt/elekter
```

## 4. Jar ja teenuse fail arvutist Raspberry'le

Arvutis projekti kaustas:

```
export JAVA_HOME=~/.jdks/openjdk-26.0.1
./gradlew bootJar
scp build/libs/elekter-0.0.1.jar pi@elekter.local:/opt/elekter/elekter.jar
scp deploy/elekter.service pi@elekter.local:/tmp/
```

Raspberry peal:

```
sudo mv /tmp/elekter.service /etc/systemd/system/elekter.service
sudo systemctl daemon-reload
sudo systemctl enable --now elekter
```

`enable` paneb teenuse käivituma iga kord, kui Pi käivitub, `--now` käivitab selle kohe.

## 5. Kontrolli, et töötab

```
systemctl status elekter                 # "active (running)"
journalctl -u elekter -f                 # logi (Ctrl+C väljumiseks); otsi rida "Started ElekterApplication"
curl localhost:8080/api/status           # JSON: currentPrice, limit, switchOn, intervalStart
```

Käivitus võtab Pi 3 peal tõenäoliselt umbes pool minutit. Ava seejärel arvutis või telefonis brauser: `http://elekter.local:8080/` (või `http://<ip>:8080/`). Liigutad liugurit ja lüliti märk peaks muutuma.

Logist näed relee seisu: `Relay GPIO17 -> ON` või `-> OFF`. Kontrolli ühendust koormuseta: kui hind on limiidist madalam (või sellega võrdne), peab GPIO17 olema kõrge (3,3 V) ja relee sisse lülituma.

## 6. Relee ühendamine

GPIO17 (pinn 11) → relee klemm 3 (**+**), GND (pinn 9) → relee klemm 4 (**−**). Koormusklemmid 1 ja 2 on 230 V ahelas. Täpsem info ja ohutus on failis [raspberry-pi-rele.md](raspberry-pi-rele.md).

- **Esimene test tee alati koormuseta** (ilma 230 V ühendamata), näiteks mõõteriistaga või releeklemmide LED-iga.
- Võrgupinge juhtmeid ei tohi ühendada ilma kogemuseta.
- Kui rakendus peatub või Pi käivitub, on GPIO17 LOW ja relee VÄLJAS. Aktiivne-madala releega plaadi korral oleks see vastupidi, selle jaoks on käivitusvõti `--elekter.gpio.active-low=true` (lisa see failis `elekter.service` `ExecStart` rea lõppu).

## 7. Uuendamine (uus versioon)

Arvutis:

```
./gradlew bootJar
scp build/libs/elekter-0.0.1.jar pi@elekter.local:/opt/elekter/elekter.jar
```

Raspberry peal:

```
sudo systemctl restart elekter
```

Limiit (`limit.txt`) jääb alles, sest see on kaustas `/opt/elekter` jari kõrval. Uuendamise ajal on relee lühikeseks ajaks VÄLJAS.

## 8. Kasulikud käsud

| Käsk | Mida teeb |
|---|---|
| `sudo systemctl stop elekter` | peatab rakenduse (relee läheb VÄLJAS) |
| `sudo systemctl start elekter` | käivitab |
| `sudo systemctl restart elekter` | taaskäivitab |
| `journalctl -u elekter -n 100` | viimased 100 logirida |
| `cat /opt/elekter/limit.txt` | praegune limiit |
| `curl localhost:8080/prices` | praegused ja tulevased hinnad (EET) |
| `ls /opt/elekter/prices` | salvestatud hinnafailid (nimes Nord Pooli kuupäev) |

## 9. Probleemid ja lahendused

| Probleem | Põhjus / lahendus |
|---|---|
| `UnsupportedClassVersionError` logis | Java on liiga vana. Kontrolli `java -version`, vaja on 17+. |
| Leht ei avane | Kontrolli `systemctl status elekter`, Pi IP-d ja et arvuti on samas võrgus. Port on 8080. |
| Hinda ei ole (`currentPrice: null`) | Pi'l pole internetti, kell on vale (`timedatectl`) või Nord Pool ei vasta. Lüliti on sel ajal VÄLJAS. Homse päeva hinnad ilmuvad alles pärast Nord Pooli avaldamist (tavaliselt pärastlõunal). |
| Logis "Could not drive GPIO17" | `gpioset` puudub (`sudo apt install gpiod`) või kasutaja pole grupis `gpio` (`sudo usermod -aG gpio pi`, siis taaskäivita Pi). |
| `gpioset` ütleb "Device or resource busy" | Rakendus hoiab pinni. Peata teenus enne käsitsi testimist: `sudo systemctl stop elekter`. |
| Relee ei lülitu | Kontrolli juhtmeid (pinn 11 ja 9), polaarsust (+/−) ja seda, et SSR-i juhtpinge on 3–32 V (3,3 V GPIO sobib). |
| Lehel "NTP: kell on vale" ja lüliti VÄLJAS | Süsteemi kell erineb üle 60 s. Kontrolli `timedatectl` ja internetti, NTP peab olema aktiivne. Rakendus kontrollib kella iga 2 minuti tagant ja läheb ise tööle, kui kell on parandatud. |
| Lehel "NTP: serveriga ei saanud ühendust" | UDP port 123 on blokeeritud või `pool.ntp.org` ei vasta. Lüliti töötab edasi. Teise serveri saad määrata ExecStart reale: `--elekter.ntp.server=<aadress>`. |

## 10. Turvalisus

Veebilehel **ei ole parooli**. Igaüks sinu võrgus saab limiiti muuta, seega ära ava porti 8080 internetti (ruuteri pordiedastus). Kaugjuurdepääsuks kasuta VPN-i (nt Tailscale või WireGuard).

## Hinnafailid

Rakendus salvestab hinnad kausta `/opt/elekter/prices/`, üks fail Nord Pooli tarnepäeva kohta (nt `2026-10-03.txt`, ridade kuju `algus;lõpp;hind`, kellaajad EET-s). Lüliti otsus tehakse nendest failidest, mitte iga kord internetist.

- **Iga päev kell 14:30 (EET)** laetakse alla homse päeva fail. Ebaõnnestumisel proovitakse veel 3 korda 2-minutilise vahega.
- **Käivitusel ja iga 15 minuti tagant** laetakse puuduv fail alla (nt kui Pi oli välja lülitatud või võrku polnud kell 14:30). Esimesel käivitusel tekivad failid seega paari sekundiga.
- **Kustutamine:** Nord Pooli tarnepäev algab Eesti ajas kell 01:00, seega fail on kasutusel kuni järgmise päeva 01:00. Iga tunni :05 peal kustutatakse failid, mille viimane intervall on lõppenud (praktikas kell 01:05).
- **Tõrke korral** (faili pole, hinda pole) on lüliti VÄLJAS.
- Failid on tavalised tekstifailid, neid võib käsitsi vaadata (`cat /opt/elekter/prices/*.txt`) või kustutada, siis laetakse need uuesti alla.

## Kella kontroll

Rakendus küsib NTP-serverilt (`pool.ntp.org`, UDP port 123) kellaaega käivitusel ja siis iga tund (kui kell on vale või server ei vasta, iga 2 minuti tagant) ning näitab tulemust lehe allservas.

- **Kell õige** (erinevus kuni 60 s): tavaline töö.
- **Kell vale** (erinevus üle 60 s): lüliti on VÄLJAS, hinda ei näidata ja hindu ei laadita alla. Tavaliselt juhtub see pärast voolukatkestust, sest Raspberry'l pole reaalaja kella. Kui operatsioonisüsteem on kella parandanud, läheb rakendus 2 minuti jooksul ise tavatööle.
- **Server ei vasta:** lülitit ei blokeerita, aga lehel on hoiatus.
- **Kella ei seata rakendusest**, sest see vajaks root-õigusi. Kella korrigeerib `systemd-timesyncd`.
- Seaded (valikuline, `ExecStart` rea lõppu): `--elekter.ntp.server=...`, `--elekter.ntp.port=...`, `--elekter.ntp.threshold-seconds=...`.

## Mida see versioon veel ei tee

Hinnad on ainult Nord Pooli päev-ette hinnad EE piirkonnale.
