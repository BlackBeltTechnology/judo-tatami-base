# `Psm2AsmTransformationSerivce.java`

Runs PSM→ASM inside OSGi through `Psm2AsmWork`, publishes its trace. `@Component(immediate = true, service = Psm2AsmTransformationSerivce.class)`; exports `activate(BundleContext)`, `install(PsmModel)` → `AsmModel`, `uninstall(PsmModel)`.
`install` builds empty `AsmModel` at URI `asm:<psmModelName>.asm`, puts both models into a new `TransformationContext` and runs `new Psm2AsmWork(ctx).execute()`. Engine = `TransformationMode.fromSystemProperty()`, ZETA by default; ETL needs the `judo-tatami-psm2asm-etl` bundle (its DS component registers the engine). Trace read back from context as `Psm2AsmTransformationTrace` (absent → `IllegalStateException`) and registered as `TransformationTrace` service with empty properties — consumers cannot filter by model name.
Failure is logged with `log.error` and rethrown. No bundle-entry script URI lookup and no Epsilon import any more. `psm2AsmTransformationTraceRegistration` keyed by `PsmModel` identity in plain `HashMap`; `uninstall` on unknown model only logs error.

See change: split-etl-artifacts: service no longer calls Epsilon directly; runs through `Psm2AsmWork`.
