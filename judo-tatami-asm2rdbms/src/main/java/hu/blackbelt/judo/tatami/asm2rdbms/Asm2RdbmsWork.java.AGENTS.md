# `Asm2RdbmsWork.java`

`AbstractTransformationWork` unit, one instance per SQL dialect. Context keys are dialect-qualified: `putModel`/`getRdbmsModel` use `"rdbms:" + dialect`, `putAsm2RdbmsTrace`/`getAsm2RdbmsTrace` use `"asm2rdbmstrace:" + dialect` — two dialects can coexist in one `TransformationContext`.

`execute()` requires an `AsmModel` in the context (throws `IllegalArgumentException` otherwise), registers the NameMapping/DataTypes/TableMappingRules metamodels on the RDBMS resource set, then dispatches on `Asm2RdbmsWorkParameter.transformationMode`.

Zeta path loads `mapping-<dialect>-rdbms.model` itself (via `UriResolver.resolve`, Epsilon-free), runs `Asm2RdbmsZetaTransformation`, and flattens the Zeta trace entries into the legacy `Map<EObject, List<EObject>>` so the result stays XMI-serialisable.

ETL/DUAL path: `EtlEngineRegistry.require(Asm2RdbmsEtlEngine.class, ARTIFACT_COORDINATES).execute(asm, rdbms, dialect, log, workParameter)`; missing add-on throws `EtlEngineMissingException` naming `judo-tatami-asm2rdbms-etl`. Constructors `(ctx, URI modelRoot, dialect)` and `(ctx, dialect)` (default root `ExcelMappingModels2Rdbms.calculateExcelMappingModelURI()`); the script-root `URI` parameter was removed. `Asm2RdbmsWorkParameter` has `@Getter` so the `-etl` engine reads it.

`Asm2RdbmsWorkParameter` defaults `transformationMode` to `TransformationMode.fromSystemProperty()` and mirrors the naming knobs — note its field is `columnMaxNameSize` while `Asm2RdbmsParameter` calls it `columnNameMaxSize`.

See change: split-etl-artifacts: core no longer references Epsilon; ETL branch delegates to the `-etl` add-on.
