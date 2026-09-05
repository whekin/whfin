# Own transfers are joined by hand

Money leaves one account and arrives in another through somebody in the middle — an exchange, a
neighbour, a kiosk — where the amounts, the currencies and the dates all differ and nothing in either
row names the other. Sometimes the far side does not exist as a row at all, because nothing reads that
account. No rule can derive either case, so an **Own transfer** is only ever created by the owner's
explicit choice; closeness in time decides what is offered, never what is true.

The group type is `OWN_LINK` (`CRYPTO_BRIDGE` before DB v6, when a wallet was the only case it served).
Automatic pairing must leave it alone precisely because it is the one kind of group the app did not
derive. More than two sides is supported: one withdrawal often returns as several credits, and pairing
them one at a time leaves the remainder looking like income.

Both sides keep their own amount, currency, date and provenance. The difference between what left and
what arrived stays a difference — a spread is not a fee, and a fee is only a fee once the owner says
so. Where the far side must be written down, it is a `MANUAL` row created and linked in one
transaction, since a half-written movement reads as new money on one side. The intermediary is
optional information and never an income source.
