# PriceLimitView.vue märkmed

## Vaate märkmed

```text
Roll: Kõik rollid
Failinimi: PriceLimitView.vue
Frontend rada: /

Vaatega seotud lisainfo:
Lüliti on Sees (ON), kui praegune hind on madalam kui limiit või sellega võrdne (hinnamarker on liuguri vasakul, rohelisel alal); kui hind on limiidist kõrgem, on lüliti VÄLJAS (OFF). Lüliti järgib liugurit reaalajas, ka lohistamise ajal. Kui hinnainfot pole, on lüliti alati VÄLJAS. Liuguri vabastamisel salvestatakse limiit backendi (PUT /api/limit). Kõik kellaajad on EET (Europe/Tallinn). Skaala on tihendatud: -10–75 võtab 60% laiusest, 75–100 8%, 100–200 8%, 200–300 9%, 300–500 10% ja 500–1000 viimased 5%.
```

## API märkmed — GET /api/status

```text
API: GET /api/status

PriceStatusDto.java
Response (200):
{
  "currentPrice": 85.0,
  "limit": 150,
  "switchOn": true,
  "intervalStart": "2026-10-03T10:45:00+03:00"
}

API teenuse lisainfo:
Hind võetakse "today" failist (EET). Kui faili pole või hinda ei leita, on currentPrice null ja switchOn false.

Veateated: —
```

## API märkmed — PUT /api/limit

```text
API: PUT /api/limit

LimitUpdateRequestDto.java
Request body:
{
  "limit": 150
}

Response (200): NONE

API teenuse lisainfo:
Limiit salvestatakse kettale ja jääb alles ka pärast taaskäivitust. Lubatud vahemik -10 kuni 1000.

Veateated: —
```
