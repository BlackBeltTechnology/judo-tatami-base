## Why

The asm2keycloak transformation produces non-deterministic XMI IDs — both ETL and Zeta use EMF auto-generated UUIDs for Realm, Client, and AttributeBinding elements. XMI ID comparison always fails because each run generates different UUIDs for structurally identical elements. Adding deterministic IDs enables exact XMI ID matching (as done for psm2asm and asm2rdbms).

## What Changes

- **ETL (`realm.etl`)**: Add `setId()` calls to set structured XMI IDs on Realm elements using `"Realm/" + realmName` pattern.
- **ETL (`client.etl`)**: Add `setId()` calls to set structured XMI IDs on Client and AttributeBinding elements using `"(asm/" + sourceId + ")/RuleName"` pattern.
- **Zeta (`RealmRules.java`)**: Set XMI ID on Realm via XMLResource after factory creation using `"Realm/" + realmName` pattern.
- **Zeta (`ClientRules.java`)**: Switch from `keycloakFactory.createClient()` / `createAttributeBinding()` to `ctx.createTarget(Client.class, s, "CreateKeycloakClient")` and `ctx.createTarget(AttributeBinding.class, s, "CreateKeycloakClientClaim")`.

## Capabilities

### New Capabilities
- `keycloak-xmiid`: Deterministic XMI ID generation for asm2keycloak transformation elements

### Modified Capabilities
_(none — no existing spec requirements change)_

## Impact

- **asm2keycloak ETL scripts (2 files)**: `realm.etl` and `client.etl` get `setId()` calls
- **asm2keycloak Zeta rules (2 files)**: `RealmRules.java` and `ClientRules.java` updated for deterministic IDs
- **Test**: `Asm2KeycloakDiscoveryComparisonTest` should pass with `xmiIds=true`
