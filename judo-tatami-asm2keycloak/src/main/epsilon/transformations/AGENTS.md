# AGENTS.md — asm2keycloak/epsilon/transformations

Epsilon ETL entry point of the ASM→Keycloak lowering.

| File | Purpose |
|---|---|
| `asmToKeycloak.etl` | Root ETL module executed by `Asm2Keycloak`. Body is only two imports — `keycloak/modules/realm.etl` then `keycloak/modules/client.etl` — so all rules live in the modules. Import order is load order, not execution order; `realm.etl`'s `pre` block must materialise `KEYCLOAK!Realm` instances before `CreateKeycloakClient` looks them up, so `realm.etl` must stay first. |
