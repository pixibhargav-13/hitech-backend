# db/seed — sample data, kept out of the schema

Everything in here is **sample data for demonstrating the app**, not schema. It is a Flyway
location of its own and does not run unless an environment explicitly asks for it.

## Switching it on

Set one variable on the environment that wants it (Dokploy → the backend app → Environment):

```
FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
```

Redeploy. On the next boot Flyway applies anything in here that has not run yet, alongside the
normal schema migrations. Leave the variable unset and this directory may as well not exist.

## Switching it off again

Remove the variable and redeploy. The rows already inserted stay — this is data, not a schema
object, so nothing un-applies itself. Flyway will notice that a version in its history has no file
behind it any more; `spring.flyway.ignore-migration-patterns: "*:missing"` in `application.yaml` is
what stops that from being treated as a corrupted schema and blocking startup.

To take the sample rows out:

```sql
DELETE FROM procurement_rfqs        WHERE notes = 'Sample data';
DELETE FROM procurement_work_orders WHERE notes = 'Sample data';
```

Lines, quotes, suppliers, bills and material movements all cascade from those two.

## Rules for anything added here

1. **Version from 900 up.** V1–V899 belongs to the schema. A seed sitting between two real
   migrations would change the order they apply in.
2. **Invent no masters.** Hang sample records off the parties, projects and items the target
   database already has. A seed that creates its own "ABC Traders" leaves the client deleting
   fake vendors out of their own books.
3. **Stay inside your module's tables.** Never write an invoice, a ledger entry or a stock
   movement — anything the client reads as real money must not have sample rows in it.
4. **Guard on existing data.** Check whether the module is already in use and skip if it is.
   Sample records interleaved with real ones are worse than an empty screen.
5. **Mark what you insert**, so it can be found and removed later. The procurement seed writes
   `notes = 'Sample data'` on every root row.
