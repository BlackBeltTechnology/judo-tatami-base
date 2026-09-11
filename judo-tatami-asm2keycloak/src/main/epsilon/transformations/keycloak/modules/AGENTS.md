# AGENTS.md — asm2keycloak/epsilon/transformations/keycloak/modules

ETL rule modules lowering ASM actor types onto the Keycloak metamodel.

| File | Purpose |
|---|---|
| `client.etl` | Maps each ASM actor type to a `KEYCLOAK!Client`, each of its `EAttribute`s to a `KEYCLOAK!AttributeBinding`. Rule `CreateKeycloakClient` guards on `asmUtils.isActorType(s)` plus a non-blank `realm` extension annotation, sets `name`/`clientId` to the classifier FQName with `.` replaced by `-`, forces `enabled`, `directAccessGrantsEnabled`, `publicClient` true, `bearerOnly` false, redirect URI `*`, and appends itself to the `Realm` whose `realm` equals the annotation value. Rule `CreateKeycloakClientClaim` resolves `attributeName` from the `claim` annotation (`EMAIL`→`email`, `USERNAME`→`username`), else prefixes a colliding raw `email`/`username` with `_`, then attaches to `eContainer.equivalent("CreateKeycloakClient")`. Both set a stable `eResource` ID `(asm/<fragment>)/<RuleName>` — IDs are traceability keys, changing the pattern breaks incremental trace resolution. The `selectOne` realm lookup assumes `realm.etl` already ran. |
| `realm.etl` | `pre` block, not a rule: scans `asmUtils.getAllActorTypes()`, collects distinct `realm` extension annotation values, and instantiates one `KEYCLOAK!Realm` per value with `id` = `realm` = the annotation value, `enabled` and `loginWithEmailAllowed` true, `eResource` ID `Realm/<name>`. Runs before every ETL rule, which is what lets `client.etl` assume the realm exists. Deduplicates via a `Set`, so two actors sharing a realm name yield one `Realm`. |
