## 1. ETL — Add deterministic XMI IDs

- [x] 1.1 Update `realm.etl` — add `r.eResource.setID(r, "Realm/" + realmName)` after creating each Realm
- [x] 1.2 Update `client.etl` — add `t.eResource.setID(t, "(asm/" + s.eResource.getURIFragment(s) + ")/CreateKeycloakClient")` in `CreateKeycloakClient` rule
- [x] 1.3 Update `client.etl` — add `t.eResource.setID(t, "(asm/" + s.eResource.getURIFragment(s) + ")/CreateKeycloakClientClaim")` in `CreateKeycloakClientClaim` rule

## 2. Zeta — Add deterministic XMI IDs

- [x] 2.1 Update `RealmRules.java` — after adding Realm to resource, set XMI ID via `((XMLResource) keycloakResource).setID(realm, "Realm/" + realmName)`
- [x] 2.2 Update `ClientRules.java` `createKeycloakClient()` — replace `keycloakFactory.createClient()` with `ctx.createTarget(Client.class, s, "CreateKeycloakClient")`
- [x] 2.3 Update `ClientRules.java` `createKeycloakClientClaim()` — replace `keycloakFactory.createAttributeBinding()` with `ctx.createTarget(AttributeBinding.class, s, "CreateKeycloakClientClaim")`

## 3. Verify

- [x] 3.1 Run `Asm2KeycloakDiscoveryComparisonTest` with `-Djudo.test.comparison.xmiIds=true` and verify it passes — PASS via `run-comparison.sh --xmiids --module asm2keycloak`
- [x] 3.2 Run full asm2keycloak test suite — 15 tests, 0 failures
