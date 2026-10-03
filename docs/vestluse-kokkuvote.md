# Vestluse kokkuvõte (Claude jaoks)

Kokkuvõte planeerimisvestlusest ja sellest, mis on tehtud. Tehtud asjade kirjeldus on ka failis `CLAUDE.md`, paigaldus on failis `raspberry-pi-paigaldus.md`.

## Eesmärk

Elektri börsihinna põhjal Raspberry Pi GPIO lüliti juhtimine. Veebileht, kus on üks keskel olev liugur hinnapiiri (limiidi) seadmiseks. Rakendus on `elekter` (Gradle + Spring Boot, vt `CLAUDE.md`).

## Praegune seis

Kõik allpool "Otsused" all kirjeldatu on tehtud ja testitud (`./gradlew test`): veebileht, limiidi salvestus, hinnafailid ja 14:30 allalaadimine, 3-minutiline lüliti kontroll, relee GPIO17 peal, NTP kellakontroll, staatuse read lehel, paigaldusskript. Päris Raspberry Pi peal on testimata: GPIO/relee, `systemd` teenus ja `deploy.sh`.

Veel tegemata: veebilehe parool (praegu pole autentimist, leht on mõeldud ainult koduvõrgule), lüliti seisu ajalugu.

## Otsused

**Liugur ja skaala**
- Skaala -10 kuni 1000, horisontaalselt moonutatud: -10–75 võtab 60% laiusest, 75–100 8%, 100–200 8%, 200–300 9%, 300–500 10% ja 500–1000 viimased 5%.
- Skaala kohal punane kolmnurk märgib praegust hinda.
- Leht näitab: suurt hinda, SEES/VÄLJAS märki, 40 kastist skaalat (rohelised kastid on limiidist vasakul), liugurit, limiiti ja allosas kolme staatuse rida (lüliti/relee, hinnafailid, NTP).
- Liuguri vabastamisel salvestatakse limiit (`PUT /api/limit`).

**Lüliti loogika**
- ON, kui hind on madalam kui limiit või sellega võrdne (marker liuguri vasakul, rohelisel alal). Kui hind on limiidist kõrgem, on OFF.
- Lüliti järgib liugurit reaalajas (ka lohistamise ajal).
- Kui hinnainfo puudub või allalaadimine ebaõnnestub, lüliti OFF.

**Hinnafailid ja ajastus (kõik EET)**
- Kaks faili, nimetatud kuupäeva järgi: "today" ja "tomorrow".
- Homse faili allalaadimine iga päev kell 14:30. Ebaõnnestumisel 3 uut katset 2-minutilise vahega.
- Failid on nimetatud Nord Pooli tarnepäeva (CET) kuupäeva järgi. Tarnepäev algab Eesti ajas kell 01:00, seega eilne fail kustutatakse kell 01:05 (mitte 00:05), kui selle viimane intervall on lõppenud. Puhastus käib iga tunni :05 peal.
- Kui fail puudub (Pi oli välja lülitatud või võrku polnud), laadib rakendus käivitusel ja iga 15 minuti tagant puuduva faili alla.
- Lüliti kontroll iga 3 minuti tagant "today" failist.
- NTP kellaaja kontroll iga tund (SNTP päring `pool.ntp.org`-ile). Rakendus ainult mõõdab kella nihet, kella korrigeerib operatsioonisüsteem (`systemd-timesyncd`), sest kella seadmine vajab root-õigusi. Kui nihe on üle 60 sekundi, on lüliti VÄLJAS ja allalaadimised ootavad. Kui NTP serverini ei jõua, lülitit ei blokeerita, aga lehel on hoiatus. Vale kella või ühenduseta korral korratakse kontrolli iga 2 minuti tagant.
- Limiit ja hinnafailid salvestatakse kettale (limiit säilib taaskäivitusel).

## Lahendatud küsimused

- **Ajavöönd:** kasutusel on `Europe/Tallinn` (EET/EEST). Nord Pooli tarnepäev järgib CET-i ja algab Eesti ajas kell 01:00, seepärast on failid nimetatud Nord Pooli kuupäeva järgi (vt "Hinnafailid ja ajastus").
- **"Tomorrow" muutumine "today"-ks:** eraldi ümbernimetamist pole, sest faile otsitakse praeguse kellaaja järgi kõigist failidest.
- **Veateated API märkmetes:** controller on olemas, kirjas on tegelik 400 vastus `PUT /api/limit` jaoks.
- **Vue või staatiline HTML:** valitud on staatiline HTML (`index.html`), Vue-d ja ehitusetappi pole.

## Seotud failid

- `docs/balsamic/notes/PriceLimitView-markmed.md`: Balsamiq-stiilis vaate- ja API märkmed (toolrental projekti formaadis).
- Eeskuju: GitHub `Andrus-Lahe/toolrental`, kaust `docs/balsamic/notes` ja `.claude/skills/skill-uus-balsamic-silt`.
- Märkmete reeglid: Vaate märkmed (kollane) ja API märkmed (valge), eesti keeles; ID-väljad subjekti nimega; massiivis üks element ja `...`; veateateid ei mõelda välja.
