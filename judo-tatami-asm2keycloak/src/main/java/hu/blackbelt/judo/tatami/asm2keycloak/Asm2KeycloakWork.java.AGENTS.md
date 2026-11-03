# `Asm2KeycloakWork.java`

`AbstractTransformationWork` unit that plugs the transformation into a Tatami workflow. `execute()` pulls `AsmModel` from the `TransformationContext` (throws `IllegalArgumentException` when absent), reuses or builds a `KeycloakModel` named/versioned after the ASM model, then dispatches on `Asm2KeycloakWorkParameter.transformationMode` — Zeta path runs `Asm2KeycloakZetaTransformation`, ETL path calls `EtlEngineRegistry.require(Asm2KeycloakEtlEngine.class, ARTIFACT_COORDINATES).execute(...)`; without `judo-tatami-asm2keycloak-etl` it throws `EtlEngineMissingException` naming the artifact. Single constructor `(TransformationContext)`; the script-root `URI` constructor was removed. Puts both the `KeycloakModel` and the resulting `Asm2KeycloakTransformationTrace` back into the context.
`Asm2KeycloakWorkParameter` defaults `createTrace`/`parallel`/`useCache` true and `transformationMode` to `TransformationMode.fromSystemProperty()` (ZETA unless overridden).

See change: split-etl-artifacts: core no longer references Epsilon; ETL branch delegates to the `-etl` add-on.
