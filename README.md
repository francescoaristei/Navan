# Navan

## How to run

`./mvnw spring-boot:run`

## How to test

`./mvnw test`

## Create a transaction

Flight:

```sh
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{"type":"flight","external_id":"flight-TXL-LHR-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T06:40:00Z","total":220.00,"tax_lines":[{"name":"Airport tax","amount":15.00}],"fee_lines":[{"name":"YQ","amount":25.00}],"metadata":{"origin":"TXL","destination":"LHR","fare":180.00,"coupons":[{"carrier":"BA","flight_number":"BA982","from":"TXL","to":"LHR","departure":"2026-03-11T07:00:00Z","arrival":"2026-03-11T08:20:00Z"}]}}'
```

Hotel:

```sh
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{"type":"hotel","external_id":"hotel-LON-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T15:00:00Z","total":331.70,"tax_lines":[{"name":"VAT","rate":0.07,"amount":21.70}],"fee_lines":[],"metadata":{"name":"Hotel Mitte London","address":{"street":"12 Kingsway","city":"London","country":"GB"},"check_in":"2026-03-11","check_out":"2026-03-13","nights":2,"room_rate_per_night":140.00,"extras":[{"name":"Breakfast","amount":30.00}]}}'
```

Rail:

```sh
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{"type":"rail","external_id":"rail-STP-PAR-2026-03-13","currency":"EUR","occurred_at":"2026-03-13T09:00:00Z","total":94.00,"tax_lines":[],"fee_lines":[{"name":"Booking fee","amount":4.00}],"metadata":{"legs":[{"origin":"London St Pancras","destination":"Lille Europe","departure":"2026-03-13T09:00:00Z","arrival":"2026-03-13T10:30:00Z","train_number":"EST9108","class":"standard"},{"origin":"Lille Europe","destination":"Paris Nord","departure":"2026-03-13T11:00:00Z","arrival":"2026-03-13T12:05:00Z","train_number":"EST9122","class":"standard"}]}}'
```

Navan fee kinds:

```sh
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{"type":"navan_fee","external_id":"navan-trip-fee-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T06:40:00Z","total":25.00,"tax_lines":[],"fee_lines":[],"metadata":{"fee_kind":"trip_fee","related_external_id":"flight-TXL-LHR-2026-03-11"}}'
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{"type":"navan_fee","external_id":"navan-agent-call-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T18:00:00Z","total":15.00,"tax_lines":[],"fee_lines":[],"metadata":{"fee_kind":"agent_call_fee","related_external_id":"hotel-LON-2026-03-11"}}'
```

## Duplicate create

The first request returns `201 Created`; the second returns `409 Conflict` with the first row's `id`.

```sh
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{"type":"navan_fee","external_id":"duplicate-fee-001","currency":"EUR","occurred_at":"2026-03-11T10:00:00Z","total":20.00,"tax_lines":[],"fee_lines":[],"metadata":{"fee_kind":"trip_fee"}}'
curl -i -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d '{"type":"navan_fee","external_id":"duplicate-fee-001","currency":"EUR","occurred_at":"2026-03-11T10:00:00Z","total":20.00,"tax_lines":[],"fee_lines":[],"metadata":{"fee_kind":"trip_fee"}}'
```

## Create an invoice

```sh
curl -i -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' -d '{"transaction_ids": [1]}'
curl -i -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' -d '{"transaction_ids": [1,2,3,4,5]}'
```

## Get a transaction or invoice

```sh
curl -i http://localhost:8080/transactions/1

curl -i http://localhost:8080/invoices/1
```

## List transactions

```sh
curl -i 'http://localhost:8080/transactions?limit=50'
curl -i 'http://localhost:8080/transactions?limit=50&after_id=123'
```

## List invoices

```sh
curl -i 'http://localhost:8080/invoices?limit=50'
curl -i 'http://localhost:8080/invoices?limit=50&after_id=123'
```

## Update and delete

```sh
curl -i -X PATCH http://localhost:8080/transactions/1 -H 'Content-Type: application/json' -d '{"total":45.00,"tax_lines":[],"fee_lines":[]}'

curl -i -X DELETE http://localhost:8080/transactions/1

curl -i -X PATCH http://localhost:8080/invoices/1 -H 'Content-Type: application/json' -d '{"transaction_ids":[1,2]}'

curl -i -X DELETE http://localhost:8080/invoices/1
```
