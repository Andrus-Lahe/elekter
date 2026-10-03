# Vestluse kokkuvõte (Claude jaoks)

Kokkuvõte planeerimisvestlusest. Koodi ei ole veel muudetud; kõik allpool on otsused ja plaan.

## Eesmärk

Elektri börsihinna põhjal Raspberry Pi GPIO lüliti juhtimine. Veebileht, kus on üks keskel olev liugur hinnapiiri (limiidi) seadmiseks. Rakendus on `elekter` (Gradle + Spring Boot, vt `CLAUDE.md`).

## Praegune seis

- `GET /prices` (`PriceController.java`) tagastab Nord Pooli EE 15-minutilised hinnad (EUR/MWh) tekstina.
- Puudub: veebileht, limiidi salvestus, failide haldus, ajastused, GPIO, testid.

## Otsused

**Liugur ja skaala**
- Skaala -10 kuni 1000, horisontaalselt moonutatud: -10–100 võtab 60% laiusest, 100–300 järgmised 20%, 300–1000 viimased 20%.
- Skaala kohal punane kolmnurk märgib praegust hinda.
- Kastid: Limiit, Praegune hind, Lüliti. Allosas failide/NTP staatus.
- Liuguri vabastamisel salvestatakse limiit (`PUT /api/limit`).

**Lüliti loogika**
- ON, kui hind on madalam kui limiit või sellega võrdne (marker liuguri vasakul, rohelisel alal). Kui hind on limiidist kõrgem, on OFF.
- Lüliti järgib liugurit reaalajas (ka lohistamise ajal).
- Kui hinnainfo puudub või allalaadimine ebaõnnestub, lüliti OFF.

**Hinnafailid ja ajastus (kõik EET)**
- Kaks faili, nimetatud kuupäeva järgi: "today" ja "tomorrow".
- Homse faili allalaadimine iga päev kell 14:30. Ebaõnnestumisel 3 uut katset 2-minutilise vahega.
- Kell 00:05 kustutatakse eilne fail.
- Lüliti kontroll iga 3 minuti tagant "today" failist.
- NTP kellaaja kontroll iga tund.
- Limiit ja hinnafailid salvestatakse kettale (limiit säilib taaskäivitusel).

## Lahtised küsimused

- Ajavöönd: soovitus `Europe/Tallinn` (EET/EEST), mitte fikseeritud UTC+2. Nord Pooli päevad on UTC, praegune kood kasutab UTC kuupäevi.
- Keskööl peab "tomorrow" fail saama "today" failiks enne vana kustutamist.
- Veateated API märkmetes on `—`, kuni controller puudub (ei mõelda välja).
- Vue vs staatiline HTML: valik tegemata.

## Soovitatud väikseim esimene samm

1. `src/main/resources/static/index.html`: HTML, CSS ja JavaScript ühes failis, Spring Boot serveerib selle aadressil `/`.
2. `GET /api/status` ja `PUT /api/limit` (limiit alguses mälus).
3. Hiljem: failide allalaadimine ja ajastused, lüliti kontroll, GPIO, NTP.

## Seotud failid

- `docs/balsamic/notes/PriceLimitView-markmed.md`: Balsamiq-stiilis vaate- ja API märkmed (toolrental projekti formaadis).
- Eeskuju: GitHub `Andrus-Lahe/toolrental`, kaust `docs/balsamic/notes` ja `.claude/skills/skill-uus-balsamic-silt`.
- Märkmete reeglid: Vaate märkmed (kollane) ja API märkmed (valge), eesti keeles; ID-väljad subjekti nimega; massiivis üks element ja `...`; veateateid ei mõelda välja.
