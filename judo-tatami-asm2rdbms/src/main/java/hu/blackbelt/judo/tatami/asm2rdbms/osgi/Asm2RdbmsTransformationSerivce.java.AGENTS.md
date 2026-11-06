# `Asm2RdbmsTransformationSerivce.java`

Declares `@Component(service = Asm2RdbmsTransformationSerivce.class, immediate = true)`. Exports `activate(BundleContext)`, `install(AsmModel, String dialect) : RdbmsModel`, `uninstall(AsmModel)`.

Builds an `RdbmsModel` at URI `rdbms:<asmName>.model`, puts both models into a new `TransformationContext`, runs `new Asm2RdbmsWork(ctx, dialect).execute()` (which registers the NameMapping/DataTypes/TableMappingRules metamodels) and registers the trace from `Asm2RdbmsWork.getAsm2RdbmsTrace(ctx, dialect)` (absent → `IllegalStateException`) as a `TransformationTrace` service keyed by `AsmModel`.

Engine = `TransformationMode.fromSystemProperty()`, ZETA by default; ETL needs the `judo-tatami-asm2rdbms-etl` bundle. No bundle-entry script/model URI lookup, no Epsilon import. Failure logged with `log.error`, rethrown. Name misspells "Service" — DS wiring depends on the literal.

See change: split-etl-artifacts: service runs through `Asm2RdbmsWork`; ETL branch no longer hard-wired.
