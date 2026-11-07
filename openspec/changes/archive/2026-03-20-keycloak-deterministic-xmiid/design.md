## Context

The asm2keycloak transformation creates 3 element types: Realm (in a `pre` block), Client, and AttributeBinding (in rules). Neither ETL nor Zeta sets XMI IDs, so EMF auto-assigns random UUIDs. This makes XMI ID comparison impossible.

The `exact-xmiid-matching` change added `createTarget(Class, EObject, String)` and `buildSourceBasedId(EObject, String)` to `TransformationContext`. Client and AttributeBinding rules have source EObjects and can use this API directly. Realm is special — it's created from a string (realm name), not from a source EObject.

## Goals / Non-Goals

**Goals:**
- ETL and Zeta produce identical XMI IDs for the same keycloak elements
- IDs are deterministic across runs
- `Asm2KeycloakDiscoveryComparisonTest` passes with `xmiIds=true`

**Non-Goals:**
- Changing keycloak metamodel or business ID fields
- Refactoring RealmRules to use `@TransformRule` instead of `@PreExecution`

## Decisions

### Decision 1: Realm ID uses static prefix with realm name

Realm elements are not derived from a single source EObject — they're collected from all actor types sharing a realm name. Use a simple deterministic pattern:

```
Realm XMI ID: "Realm/{realmName}"
Example:      "Realm/DEFAULT"
```

**ETL:** `r.~id = "Realm/" + realmName;` (the `~id` syntax sets XMI resource ID in Epsilon)
**Zeta:** `((XMLResource) keycloakResource).setID(realm, "Realm/" + realmName);`

### Decision 2: Client and AttributeBinding use standard source-based IDs

These rules have source EObjects (EClass for Client, EAttribute for AttributeBinding):

```
Client XMI ID:           "(asm/{sourceId})/CreateKeycloakClient"
AttributeBinding XMI ID: "(asm/{sourceId})/CreateKeycloakClientClaim"
```

**ETL:** `t.~id = "(asm/" + s.eResource.getURIFragment(s) + ")/CreateKeycloakClient";`
**Zeta:** Switch from factory creation to `ctx.createTarget(Client.class, s, "CreateKeycloakClient")`

### Decision 3: Zeta ClientRules switches to ctx.createTarget

Currently `ClientRules` creates objects via `keycloakFactory.createClient()`. Switch to `ctx.createTarget(Client.class, s, "CreateKeycloakClient")` which:
- Sets the deterministic XMI ID automatically
- Registers the element in the resolution cache
- Is consistent with all other Zeta rules

## Risks / Trade-offs

- **[Risk] ETL `~id` syntax** → Need to verify that Epsilon's `~id` extended property correctly sets the XMI resource ID. Alternative: use `eResource.setID(t, id)` in EOL. → Mitigation: test with a simple model first.
- **[Trade-off] Realm pre-block stays as `@PreExecution`** → Not refactored to `@TransformRule` because Realm has no single source element. The XMI ID is set manually via XMLResource API.
