# judo-tatami-util-etl — module agent doctrine

ETL half of the trace utilities. Holds the two Epsilon-context trace methods moved verbatim from `judo-tatami-util` `TransformationTraceExtractor`, so `judo-tatami-util` stays Epsilon-free.

## Contents

- `src/main/java/.../util/etl/EtlTransformationTraceExtractor.java` — `getTraceEObjectMapFromEtlExecutionContext(EtlExecutionContext)` and `getTransformationTraceFromEtlExecutionContext(String, EtlExecutionContext)`.

## Invariants

- Depends on `judo-tatami-util` and `epsilon-runtime-execution`; never the reverse.
- Bundle exports `hu.blackbelt.judo.tatami.util.etl.*`.
- Callers (`Esm2Psm`, `Jsl2Psm`, `-etl` entry classes) import this class; Karaf feature `judo-tatami-util-etl` ships it.
- Sub-module of the judo-tatami-base reactor, not of the single-module judo-tatami-util repo.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
