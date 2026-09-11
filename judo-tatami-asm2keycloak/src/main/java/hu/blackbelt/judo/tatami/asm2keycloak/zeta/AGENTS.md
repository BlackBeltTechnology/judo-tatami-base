# AGENTS.md — asm2keycloak/zeta

Zeta-engine port of the ASM→Keycloak ETL rules — the default engine when `TransformationMode` is ZETA.

| File | Purpose |
|---|---|
| `Asm2KeycloakRuleNames.java` | Final, non-instantiable holder of the rule-name string constants shared by Zeta rules and trace consumers: `CREATE_REALM = "CreateRealm"`, `CREATE_KEYCLOAK_CLIENT = "CreateKeycloakClient"`, `CREATE_KEYCLOAK_CLIENT_CLAIM = "CreateKeycloakClientClaim"`. Values must stay byte-identical to the ETL rule names in `realm.etl`/`client.etl`, because trace element IDs embed them — renaming a constant silently orphans existing traces. |
| `Asm2KeycloakZetaTransformation.java` | Runs the ASM→Keycloak lowering on the Zeta `TransformationRegistry` + `TransformationExecutor` instead of Epsilon. Lombok `@Builder` takes `@NonNull asmModel`, `@NonNull keycloakModel`; `execute()` returns the native Zeta `TransformationTrace` from `TransformationResult.getTrace()`. Registers `RealmRules` then `ClientRules` in that order (realm phase must precede client phase). Builds a `TransformationContext` whose source/target `ResourceSet`s come from the two models, aliased as `source`/`asm` and `target`/`keycloak`, with context attributes `asmUtils` (an `AsmUtils` over the ASM resource set) and `keycloakResource`; rules look up those exact attribute keys. Executor is forced `parallel(false)` because Keycloak models are small. Nested private `Asm2KeycloakModelProvider` implements `ModelProvider.getAllContents/getName/getTypeName` over `AsmUtils.all(type)`, so only ASM-resident elements are enumerable as sources. |
| `CLAUDE.md` | `!! GENERATED` — `claude-mem` auto-writes the `<claude-mem-context>` block. Currently holds no activity. Not a source file; hand edits inside the tags are overwritten. |
