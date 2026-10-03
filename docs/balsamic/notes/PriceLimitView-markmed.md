# PriceLimitView märkmed

Vaade on staatiline leht `src/main/resources/static/index.html` (HTML, CSS ja JavaScript ühes failis, ilma Vue-ta), mida Spring Boot serveerib aadressil `/`.

## Vaate märkmed

```text
Roll: Kõik rollid
Failinimi: index.html
Frontend rada: /

Vaatega seotud lisainfo:
Lüliti on Sees (ON), kui praegune hind on madalam kui limiit või sellega võrdne (hinnamarker on liuguri vasakul, rohelisel alal); kui hind on limiidist kõrgem, on lüliti VÄLJAS (OFF). Lüliti järgib liugurit reaalajas, ka lohistamise ajal. Kui hinnainfot pole, on lüliti alati VÄLJAS. Liuguri vabastamisel salvestatakse limiit backendi (PUT /api/limit). Leht uuendab andmeid iga 30 sekundi tagant (GET /api/status). Kõik kellaajad on EET (Europe/Tallinn). Skaala on -10 kuni 1000 ja tihendatud: -10–75 võtab 60% laiusest, 75–100 8%, 100–200 8%, 200–300 9%, 300–500 10% ja 500–1000 viimased 5%. Skaala on 40 kasti, limiidist vasakul olevad kastid on rohelised, punane kolmnurk märgib praegust hinda.

Allservas on kolm staatuse rida: Lüliti (relee seis, viimane kontroll, viimane muutus; punane, kui relee pinni hoidev protsess ei tööta), Hinnafailid (tänane ja homne fail, viimane allalaadimine; punane, kui tänane fail puudub) ja NTP (kella erinevus; punane, kui kell on vale ja lüliti seetõttu VÄLJAS).
```

## API märkmed — GET /api/status

```text
API: GET /api/status

SwitchService.Status (Java record)
Response (200):
{
  "currentPrice": 85.0,
  "limit": 150.0,
  "switchOn": true,
  "intervalStart": "2026-10-03T10:45:00+03:00",
  "ntp": {
    "state": "ok",
    "offsetMillis": 40,
    "checkedAt": "2026-10-03T10:40:12.123456789+03:00"
  },
  "checkedAt": "2026-10-03T10:42:00.123456789+03:00",
  "relay": {
    "enabled": true,
    "on": true,
    "running": true,
    "changedAt": "2026-10-03T09:30:00.123456789+03:00"
  },
  "prices": {
    "today": true,
    "tomorrow": true,
    "lastDownload": {
      "date": "2026-10-04",
      "ok": true,
      "at": "2026-10-03T14:30:01.123456789+03:00"
    }
  }
}

API teenuse lisainfo:
Hind võetakse salvestatud hinnafailidest (kaust prices/, üks fail Nord Pooli tarnepäeva kohta, EET). Kui faili pole, hinda ei leita või kell on vale, on currentPrice ja intervalStart null ja switchOn false. ntp.state on ok, wrong, unreachable või unknown; kui kell on vale (erinevus üle 60 s), on lüliti VÄLJAS. relay.enabled on false, kui GPIO juhtimine pole sisse lülitatud; relay.running näitab, kas pinni hoidev gpioset protsess töötab. relay.on, relay.changedAt, checkedAt, ntp.offsetMillis, ntp.checkedAt ja prices.lastDownload on null, kuni vastavat kontrolli pole veel tehtud. checkedAt on lüliti viimane kontroll (iga 3 minuti tagant).

Veateated: —
```

## API märkmed — PUT /api/limit

```text
API: PUT /api/limit

StatusController.LimitUpdate (Java record)
Request body:
{
  "limit": 150
}

Response (200): NONE

API teenuse lisainfo:
Limiit salvestatakse kettale (limit.txt töökaustas) ja jääb alles ka pärast taaskäivitust; vaikimisi 100. Lubatud vahemik -10 kuni 1000. Pärast salvestamist arvutatakse lüliti seis kohe uuesti.

Veateated:
400 Bad Request, kui limiit on väljaspool lubatud vahemikku (vastuses on ainult Springi tavaline veakeha, kus pole teksti)
```
