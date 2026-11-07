# `RealmRules.java`

Ports the `pre` block of `realm.etl` as `@PreExecution public void createRealmsPreHook(TransformationContext ctx)`, `@TransformationContext(source = EClass.class, target = Realm.class)`.
Collects actor types via `asmUtils.getAllActorTypes()`, groups by trimmed non-blank `realm` extension annotation into a `LinkedHashMap` keeping the FIRST actor per realm as the trace source, then creates one `Realm` per unique name with `id` = `realm` = the name, `enabled` and `loginWithEmailAllowed` true, adds it to the target resource contents and sets XMI ID `Realm/<name>` when the resource is an `XMLResource`.
Must run before `ClientRules` — `createKeycloakClient` looks realms up by name and bails when absent.
Reads context attributes `asmUtils` and `keycloakResource`; needs a public no-arg constructor for `TransformationRegistry`.