# `Asm2KeycloakZetaTransformation.java`

Runs the ASM→Keycloak lowering on the Zeta `TransformationRegistry` + `TransformationExecutor` instead of Epsilon.
Lombok `@Builder` takes `@NonNull asmModel`, `@NonNull keycloakModel`; `execute()` returns the native Zeta `TransformationTrace` from `TransformationResult.getTrace()`.
Registers `RealmRules` then `ClientRules` in that order (realm phase must precede client phase).
Builds a `TransformationContext` whose source/target `ResourceSet`s come from the two models, aliased as `source`/`asm` and `target`/`keycloak`, with context attributes `asmUtils` (an `AsmUtils` over the ASM resource set) and `keycloakResource`; rules look up those exact attribute keys.
Executor is forced `parallel(false)` because Keycloak models are small.
Nested private `Asm2KeycloakModelProvider` implements `ModelProvider.getAllContents/getName/getTypeName` over `AsmUtils.all(type)`, so only ASM-resident elements are enumerable as sources.