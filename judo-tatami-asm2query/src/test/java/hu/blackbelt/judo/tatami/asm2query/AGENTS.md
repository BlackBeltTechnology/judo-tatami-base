# AGENTS.md — `judo-tatami-asm2query/src/test/java/hu/blackbelt/judo/tatami/asm2query`

Unit tests of the query builder over the Northwind Demo models. See change: persisted-query-model.

| File | Purpose |
| --- | --- |
| `Asm2QueryTest.java` | Builds `Asm2Query` over Northwind. Asserts logical URI `<name>-query.model` (TP-10), every `QueryModelInfo` field against the built `QueryIndex`, sorted entries, static sets = evaluated features with empty variable lists (TP-18), built vs restored `QueryIndex` (lookups, ordering, counters, entity expression identity, `isStatic*` for every ASM feature; in-memory part of TP-16), restore failures on 0 / 2+ info roots and unset / foreign format version. |
| `Asm2QueryWorkTest.java` | `Asm2QueryWork` on Northwind: TP-8 (context `ExpressionModel` same instance and `EqualityHelper`-equal per root to a pre-stage copy; one `QueryModel`), TP-62 Asm2QueryWork half (preloaded saved expression model → WARN "expression model not extracted in this run", no `QueryModel`), absent / origin-less expression model skipped, missing ASM fails, `preserveExpressionModel` on/off, `Asm2QueryConfiguration` defaults. See change: persisted-query-model. |
| `JarSkillPackageTest.java` | Relocated from `judo-runtime-core-expression`: the `claude/` skill package and `agent-docs/` ship in the jar (marketplace/plugin/INSTALL filtered, module name `judo-tatami-asm2query`). See change: persisted-query-model. |
| `LogCapture.java` | Test helper: captures WARN+ events of one class' logback logger (`warnings()`), `AutoCloseable`. |
| `MinimalFixture.java` | Test fixture: tiny PSM (entity with stored + derived string attribute, mapped DTO) through psm2asm and `Asm2ExpressionWork`, no measure model. Northwind cannot be built without one (units). Used by TP-54. |
| `NorthwindFixture.java` | Test helper: Northwind PSM → ASM, Measure, then `Asm2ExpressionWork` (default config) into one `TransformationContext`; `shared()` builds once per JVM, `create()` builds a private copy for mutating tests. |
| `PersistedQueryModelTest.java` | Locator is lazy: supplier not invoked on creation, once on `openStream()`. |
| `QueryModelPersistenceTest.java` | `QueryModelPersistence` over Northwind: TP-13 hrefs, TP-14 duplicate-id guard (unreferenced written / referenced + expression refused, WARN names id), TP-15 foreign resource, stamps (null stamp = mismatch), generator identity, file / jar URL / nested path round trip, reloaded ASM+expression files, unresolved proxies for a mismatched ASM, 0 / 2 info roots, TP-16 save→load→`QueryIndex` equality, digest helper, TP-36 / TP-37 constant allow-list, TP-54 no measure model. See change: persisted-query-model. |
| `QueryModelTimingTest.java` | TP-46 measurement: 5 restores (load + `QueryIndex.restore`) and 5 builds after one warm-up each; times and medians logged, only completion asserted. |
| `TransferObjectTypeBindingsCollectorTest.java` | TP-59. Binding index vs the test-only reference scan: identical elements and order for every binding class × type FQN; whole-model agreement (graphs, unmapped bindings, static flags, entity expressions) between the indexed collector and a scan-backed subclass. |
