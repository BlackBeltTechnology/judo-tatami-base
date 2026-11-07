# AGENTS.md — asm2keycloak/zeta/rules

Declarative Zeta rule classes, one per ETL module of `asmToKeycloak.etl`.

| File | Purpose |
|---|---|
| `ClientRules.java` | Ports `client.etl` via `createKeycloakClient()`/`createKeycloakClientClaim()` exports → see `ClientRules.java.AGENTS.md` |
| `RealmRules.java` | `createRealmsPreHook` builds `Realm`s from `asmUtils.getAllActorTypes()` before `ClientRules` → see `RealmRules.java.AGENTS.md` |
