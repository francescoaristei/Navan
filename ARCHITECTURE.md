# Architecture

## external_id

`external_id` is a client-supplied id for transactions, it has been defined with a unique constraint/index
at the database level.
This has been done to make sure that two overlapping POSTs will not result in
duplicate rows in the `transactions` table for the same external_id.
Such attempt is blocked with a 409 error.

## Tables and Models

Transactions are stored in the transactions table.
The type column allows: flight, hotel, rail or navan fee. The type-specific fields are
stored as serialized JSON in the metadata TEXT column.
The `TransactionService` then validates the required metadata fields depending on the type
before storing a transaction.
GET/transactions/{id} parses the stored JSON and returns the same structure.
`tax_lines` and `fees_lines` are kept as separate child tables.

An invoice is defined as a row in the `invoices` table.
Each line of the invoice is saved in `invoice_lines`.
An invoice line copy the transaction type, total, metadata, taxes and fees of the transaction
it links to at time of creation (or replacement). Tax and fee rollups are saved in separate tables.  
When reading an invoice via GET, the saved snapshot is used.
`transaction_id` in `invoice_lines` is indexed to quickly check if a transaction is linked to any
invoice.

## Transaction and Invoice updates / deletes

Correct execution of requests on transactions and invoices has been obtained via
pessimistic row locking.
Affected rows are read with a database write lock, hold through the service transaction until commit or rollback.

## High volume

To deal with high volume, pagination with limit has been implemented, having default to 50 and upper bound of 100 rows
(requests above are rejected with 400 error).
Optional cursor parameter (`after_id`) is also provided to get rows after certain id.
If another page exists, `next_cursor` is returned as well.
The limit is applied at database level, before rows are loaded in memory.
Concurrent PATCH/DELETE are handled via pessimistic locking, keeping each mutation atomic.
This can be a source of latency on increased traffic.
