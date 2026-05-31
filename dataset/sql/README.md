# SQL Seed Files

This folder contains generated SQL seed files that match the current PostgreSQL schema used by the repository.

## Files

- `wallet_seed.sql`: seeds demo `wallets` and `ledger_entries`

## User Fixtures

There is no `users` table in the current service schema.

Because of that, demo users are stored separately in:

- `../users/demo_users.json`
- `../users/demo_users.csv`

These user IDs are reused consistently across:

- wallet seeds
- future JWT claim mapping

## Import Examples

### Wallet DB

```powershell
psql -h localhost -p 5434 -U wallet_user -d wallet_db -f dataset/sql/wallet_seed.sql
```

## Notes

- These files are synthetic local-dev fixtures.
- `wallet_seed.sql` intentionally funds the system merchant wallet so local top-up and payment flows can operate under the current non-negative balance constraint.
- If you recreate Docker volumes, the schema init files still run first; these SQL files are additional seeds you can apply afterward.
