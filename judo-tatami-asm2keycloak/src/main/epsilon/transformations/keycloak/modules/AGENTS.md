# AGENTS.md — asm2keycloak/epsilon/transformations/keycloak/modules

ETL rule modules lowering ASM actor types onto the Keycloak metamodel.

| File | Purpose |
|---|---|
| `client.etl` | ETL rules `CreateKeycloakClient`/`CreateKeycloakClientClaim`: ASM actor types → `KEYCLOAK!Client` + `KEYCLOAK!AttributeBinding`, `realm`-annotation-driven registration, stable `eResource` trace IDs → see `client.etl.AGENTS.md` |
| `realm.etl` | ETL `pre` block instantiating one `KEYCLOAK!Realm` per distinct `realm` annotation value from `asmUtils.getAllActorTypes()`, dedup via `Set` → see `realm.etl.AGENTS.md` |
