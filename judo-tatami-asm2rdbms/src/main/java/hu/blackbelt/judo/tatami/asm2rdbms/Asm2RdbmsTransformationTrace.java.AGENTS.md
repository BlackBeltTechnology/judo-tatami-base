# `Asm2RdbmsTransformationTrace.java`

Implements `TransformationTrace` over the ASM→RDBMS pair, carrying either the ETL trace (`Map<EObject, List<EObject>>`) or a Zeta trace — read through `isZetaTrace()`/`getZetaTrace()`/`getTransformationTrace()`, not the fields.

Exports `fromModelsAndTrace(...)`, `resolveAsm2RdbmsTrace(...)`, `createAsm2RdbmsTraceResource(...)`, `getAsm2RdbmsTrace(...)`, `save(...)`, `getSourceModelTypes()` = `[AsmModel]`, `getTargetModelType()` = `RdbmsModel`.

`getTransformationTrace()` Zeta branch flattens `getZetaTrace().getEntries()` into `Map<EObject, List<EObject>>` and dedupes targets per source (`contains` check, EObject identity). Zeta emits one `TraceEntry` per (source, rule[, discriminator]) so the same target repeats; without dedupe `TransformationTraceService.getDescendantOfInstanceByModelType` returned the same `RdbmsField` twice and `RdbmsResolver.rdbmsField` failed "One and only one field have to be mapped for attributes" (judo-runtime-core-esm-itest). See change: split-etl-artifacts (esm-itest Zeta fixes).

Trace URIs use postfix `asm2rdbms`. Round-tripping requires both source and target models registered in the resolving `ResourceSet`, else trace entries resolve to null.