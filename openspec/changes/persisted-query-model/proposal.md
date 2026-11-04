## Why

Module-level slice of the multi-module change `openspec/changes/persisted-query-model` in the judo-ng superproject:
the logical query model is built from JQL on every runtime boot. `judo-tatami-base` (rank 7) gets the new module
`judo-tatami-asm2query`, so the model can be built once at model build time, saved next to the ASM model and
restored at boot. It also flips the `asm2expression` lambda default so the build and every runtime extract the
same way.

## What Changes

- New module `runtime/judo-tatami-base/judo-tatami-asm2query` (package `hu.blackbelt.judo.tatami.asm2query.*`,
  OSGi bundle), with **no direct** epsilon, `mapper-api` or `judo-dispatcher-api` dependency.
- Builder moved in from `judo-runtime-core-query` (`FeatureFactory`, `JoinFactory`, `Context`, `Constants`, the
  feature converters, the build part of `QueryFactory`) and from `judo-runtime-core-expression`
  (`TransferObjectTypeBindingsCollector`, `EntityTypeExpressions`, the bindings). `Coercer` is dropped from the
  moved constructors.
- `TransferObjectTypeBindingsCollector` gets a one-pass binding index over the whole expression `ResourceSet`
  (`getAllContents()` encounter order) with output identical to today's per-call scan (the scan stays as a
  test-only reference).
- New `Asm2Query` (build over the run's expression `ResourceSet`, logical URI `<name>-query.model`, emits
  `QueryModelInfo`), `QueryIndex` (restore rules), `PersistedQueryModel` (locator), `QueryModelPersistence`
  (save with URI rewrite, reference guard and stamps; load with logical URI, `resolveAll`, proxy check and an
  `EJavaObject` allow-list), `Asm2QueryWork` and `Asm2QueryConfiguration`. `Asm2QueryWork` builds only over an expression model
  created in the same run (per `ExpressionModelOrigin`); absent or preloaded, it is skipped with a WARN and puts no
  `QueryModel`. `asm2QueryOutputPredicate()` is not
  here: it lives in each workflow module's `WorkflowHelper` next to `asm2ExpressionOutputPredicate()`.
- Generator identity from the filtered `asm2query-generator.properties` (version, SNAPSHOT build timestamp).
- `judo-tatami-asm2expression`: `Asm2ExpressionConfiguration.resolveOnlyCurrentLambdaScope` defaults to `false`
  (behaviour change of every build path that does not set it).
  `Asm2ExpressionWork` also puts an `ExpressionModelOrigin` (created in this run, or preloaded) into the
  `TransformationContext`, so the query stage can skip a preloaded model, whose bindings may keep another setting.
- `project-meta.yml`: `judo-tatami-base` depends on `judo-meta-query` (rank stays 7).
- `judo-runtime-core-expression` bundled skill package and `JarSkillPackageTest` are relocated or retired.
- Docs: `asm2query` capability absorbing the runtime-core `expression` spec; `AGENTS.md` rows;
  `docs/query-externalization-analysis.md` points at the master change.
- The three builder requirements the runtime-core change removes from its `query` capability ("Expression-to-feature
  conversion", "Navigation-to-join conversion", "Query context management") are specified here, in `asm2query`.
- **BREAKING** (type level): `Context`, `Constants` and `EntityTypeExpressions` change package; the moved
  constructors lose `Coercer`.

## Capabilities

### New Capabilities
- `asm2query`: the query model builder, the binding collector (absorbing the runtime-core `expression` spec), the
  builder requirements moved from the runtime-core `query` spec, the index restore rules, the persisted artifact (save and load) and the tatami work that produces it.

### Modified Capabilities
- `asm2expression`: "Configure Lambda Scope Resolution" defaults to `false`.
  Adds "Expression model origin is recorded".

## Impact

- **Files / packages:** new module `judo-tatami-asm2query` (POM, `hu.blackbelt.judo.tatami.asm2query.*`,
  `asm2query-generator.properties`, Northwind tests); `judo-tatami-asm2expression`
  (`Asm2ExpressionConfiguration`, `Asm2ExpressionTest`, `Asm2ExpressionWorkTest`); `project-meta.yml`; reactor POM;
  `docs/query-externalization-analysis.md`; `AGENTS.md` rows.
- **Dependencies:** adds `judo-meta-query` (rank 4), guava, commons-lang3 to the new module. `judo-tatami-base`
  stays at rank 7. Consumers (runtime-core, tatami workflows, platform) are rank >= 8 and change in their own
  stores.
- **Compatibility:** the moved types are a package break for downstream compiled code; the lambda default changes
  the saved `<name>-expression.model` extraction setting (38 checked models are equal under both settings).
- **Rollback:** revert the module and the default; consumers fall back through `mode=build` /
  `ignoreAsm2Query=true`. No database or schema impact.
