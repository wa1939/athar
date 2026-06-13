# Account Routing For Ingestion

Athar can route new SMS and notification transactions to a specific account instead
of putting every parsed row into the manual seed account.

## Configuration

Settings -> Accounts -> Add/Edit account has **SMS routing aliases**. The field accepts
comma-separated values:

- sender names, such as `AlRajhiBank`, `STC Bank`, or `barq app`
- card or account tails, such as `9803` or `0930`

Aliases stay in the encrypted local database with the account. They are not uploaded
or shared.

## Resolution Rules

For each parsed SMS or bank notification, Athar checks active accounts only:

1. Numeric aliases match card/account tails in the message body or parser
   counterparty. A single numeric match wins even when several accounts use the
   same bank sender.
2. Otherwise, text aliases match the incoming sender name.
3. If exactly one account matches, the transaction is stored on that account.
4. If zero or multiple accounts match, Athar falls back to the manual seed account.

The fallback is intentional. Guessing the wrong account corrupts net worth, so
ambiguous routing must stay safe until the user adds a more specific alias.

## Examples

- Checking account aliases: `AlRajhiBank, 0930`
- Credit card aliases: `AlRajhiBank, 9803`
- Message body: `PoS purchase ... Card: **9803`
- Result: transaction is linked to the credit-card account.

If the body has no account/card tail and both accounts share only `AlRajhiBank`,
Athar falls back to the manual seed account instead of guessing.
