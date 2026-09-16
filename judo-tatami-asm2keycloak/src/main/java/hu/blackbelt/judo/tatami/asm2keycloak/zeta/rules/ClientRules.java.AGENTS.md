# `ClientRules.java`

Ports `client.etl`. `@TransformationContext(source = EClass.class, target = Client.class)`.
Exports `createKeycloakClient() : TransformFunction<EClass, Client>` (rule `CREATE_KEYCLOAK_CLIENT`) and `createKeycloakClientClaim() : TransformFunction<EAttribute, AttributeBinding>` (rule `CREATE_KEYCLOAK_CLIENT_CLAIM`).
Client rule skips non-actor types and actors without a non-blank `realm` extension annotation, returns null and warns when no `Realm` with that name exists in the target resource, else builds a `Client` via `KeycloakFactory.eINSTANCE` with `name` = `clientId` = classifier FQName dotted-to-dashed, `enabled`/`directAccessGrantsEnabled`/`publicClient` true, `bearerOnly` false, redirect URI `*`, and containment under `realm.getClients()`.
Claim rule maps `claim` annotation `EMAIL`→`email`, `USERNAME`→`username`, escapes bare `email`/`username` to `_email`/`_username`, and attaches to `ctx.equivalent(actorType, Client.class)` — the claim is dropped from the model when the client equivalent is missing.
Both read context attributes `asmUtils` and `keycloakResource` and set XMI ID `(asm/<elementId>)/<RuleName>` only when the target resource is an `XMLResource`; IDs are assigned after containment, so containment must be set first.
Needs a public no-arg constructor for `TransformationRegistry`.