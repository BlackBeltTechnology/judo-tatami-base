# judo-tatami-util-etl/src/main/java/hu/blackbelt/judo/tatami/util/etl

Epsilon-dependent trace extraction, split out of `judo-tatami-util`.

| File | Purpose |
|---|---|
| `EtlTransformationTraceExtractor.java` | Static utility; `getTraceEObjectMapFromEtlExecutionContext(EtlExecutionContext)` flattens the ETL trace to `Map<EObject, List<EObject>>` (drops non-`EObject`, merges duplicate sources); `getTransformationTraceFromEtlExecutionContext(String nameSpace, EtlExecutionContext)` converts it via core `TransformationTraceExtractor`. Moved verbatim. See change: split-etl-artifacts. |
