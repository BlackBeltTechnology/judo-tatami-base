# `Asm2RdbmsTransformationSerivce.java`

Declares `@Component(service = Asm2RdbmsTransformationSerivce.class, immediate = true)`. Exports `activate(BundleContext)`, `install(AsmModel, String dialect) : RdbmsModel`, `uninstall(AsmModel)`.

Builds an `RdbmsModel` at URI `rdbms:<asmName>.model`, registers the NameMapping/DataTypes/TableMappingRules metamodels on its resource set, resolves the ETL script root from bundle entry `/tatami/asm2rdbms/transformations/asmToRdbms.etl` and the Excel model root from `/tatami/asm2rdbms/model/typemapping.xml`, runs `Asm2Rdbms.executeAsm2RdbmsTransformation`, and registers the trace as a `TransformationTrace` service keyed by `AsmModel`.

Always takes the ETL path — the Zeta engine is not reachable here. Bundle must ship both entries at those exact paths or `getEntry(...)` returns null and `install` throws. Name misspells "Service" — DS wiring depends on the literal.