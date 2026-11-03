# `Asm2KeycloakTransformationSerivce.java`

Declares `@Component(service = Asm2KeycloakTransformationSerivce.class, immediate = true)`. Exports `activate(BundleContext)` and `install(AsmModel) : KeycloakModel`.
Builds an empty `KeycloakModel` named/versioned after the ASM model, puts both models into a new `TransformationContext`, runs `new Asm2KeycloakWork(ctx).execute()` and registers the resulting `Asm2KeycloakTransformationTrace` (read from context; absent → `IllegalStateException`) as a `TransformationTrace` service keyed by `AsmModel`. Engine = `TransformationMode.fromSystemProperty()`, ZETA by default; ETL needs the `judo-tatami-asm2keycloak-etl` bundle. No bundle-entry script lookup, no Epsilon import. Failure logged with `log.error`, rethrown. Name misspells "Service" — DS wiring depends on the literal.

See change: split-etl-artifacts: service runs through `Asm2KeycloakWork`; ETL branch no longer hard-wired.
