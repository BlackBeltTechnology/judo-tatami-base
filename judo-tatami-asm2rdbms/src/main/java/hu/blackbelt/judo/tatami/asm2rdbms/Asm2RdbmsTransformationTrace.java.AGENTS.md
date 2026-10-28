# `Asm2RdbmsTransformationTrace.java`

Implements `TransformationTrace` over the ASM→RDBMS pair, carrying either the ETL trace (`Map<EObject, List<EObject>>`) or a Zeta trace — read through `isZetaTrace()`/`getZetaTrace()`/`getTransformationTrace()`, not the fields.

Exports `fromModelsAndTrace(...)`, `resolveAsm2RdbmsTrace(...)`, `createAsm2RdbmsTraceResource(...)`, `getAsm2RdbmsTrace(...)`, `save(...)`, `getSourceModelTypes()` = `[AsmModel]`, `getTargetModelType()` = `RdbmsModel`.

Trace URIs use postfix `asm2rdbms`. Round-tripping requires both source and target models registered in the resolving `ResourceSet`, else trace entries resolve to null.