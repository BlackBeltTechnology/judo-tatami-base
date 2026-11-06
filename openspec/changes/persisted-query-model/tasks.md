# Tasks

Module slice of the master change `openspec/changes/persisted-query-model` (judo-ng superproject), folded into
feature branch `feature/JNG-6349_AddZetaValidatiob`.

## 1. Module and project metadata (rank 7)

- [x] 1.1 Add `judo-meta-query` to `judo-tatami-base` dependencies in `project-meta.yml`. Verify: judoctl reports
      rank 7.
- [x] 1.2 Scaffold `runtime/judo-tatami-base/judo-tatami-asm2query`: a bundle POM modelled on
      `judo-tatami-asm2expression` plus `judo-meta-query`, guava and commons-lang3, with **no direct** epsilon,
      `mapper-api` or `judo-dispatcher-api` dependency. Add a filtered `asm2query-generator.properties` (version,
      build timestamp), `Export-Package hu.blackbelt.judo.tatami.asm2query.*` and the bundle instructions, and
      register the module in the reactor. Verify:
      - the module builds;
      - the POM declares no `epsilon-*`;
      - `rg 'org.eclipse.epsilon' src/main` is empty.

## 2. Builder move and Asm2Query

- [x] 2.1 Move the builder (`FeatureFactory`, `JoinFactory`, `Context`, `Constants`, the feature converters, the
      build part of `QueryFactory`) and the binding classes (`TransferObjectTypeBindingsCollector`,
      `EntityTypeExpressions`, the bindings) into `hu.blackbelt.judo.tatami.asm2query.*`. Drop `Coercer` from the
      `FeatureFactory` and `ConstantToFeatureConverter` constructors. Name the source repo and commit in the commit
      message. Verify: the module compiles.
- [x] 2.2 Implement `Asm2Query`, which builds over a given expression `ResourceSet` into a resource with the logical
      URI `<name>-query.model`, including the custom-join value type. In the moved
      `TransferObjectTypeBindingsCollector`, replace the per-call `getExpressionElement(Class)` scan with a binding
      index built once over the whole expression `ResourceSet` (binding class → type FQN → bindings in
      `getAllContents()` encounter order, exactly as the scan); keep the old scan as a test-only reference
      implementation. `Asm2Query` emits `QueryModelInfo` (D2): keys,
      ordered relations, static sets (evaluated + empty variables only), entity expression map including empty
      entries, counters, `formatVersion`, `modelName`. Verify: a Northwind unit test asserts every `QueryModelInfo`
      field against the built index; a unit test shows the binding index answers every Northwind lookup exactly
      like the reference scan (same elements, same order).

## 3. QueryIndex and locator

- [x] 3.1 Implement `QueryIndex`: restore from `QueryModelInfo` with today's concurrent types, plus the restore
      rules (`isStatic*` via `getMappedX(...).map(set::contains).orElse(true)`, lookups, the entity expression map).
      Add the `PersistedQueryModel` locator (logical name + one stream supplier). Verify: a unit test asserts
      built-index vs restored-index equality, including `isStatic*` for every ASM feature.

## 4. QueryModelPersistence

- [x] 4.1 Implement `QueryModelPersistence` save (D1, D5):
      - rewrite only the ASM and expression resource URIs (`<name>-asm.model`, `<name>-expression.model`);
      - apply the reference guard (ASM objects whose `xmi:id` occurs exactly once, **counted** over the whole ASM
        resource; expression objects with a non-null, unique id; Ecore; the query resource; otherwise write
        nothing, delete a stale file, log);
      - stamp the owner-only generator identity (`asm2query-generator.properties`: version, plus build
        timestamp for a SNAPSHOT; unreadable counts as a mismatch);
      - record the ASM, measure and expression digests.

      Verify: unit tests for the hrefs, a duplicate-id ASM (no file, warning), a foreign-resource reference and the
      stamps.
- [x] 4.2 Implement `QueryModelPersistence` load: a stream under the logical URI in a dedicated `ResourceSet`
      mapped onto the live ASM and expression resources, `resolveAll`, an unresolved count, a digest helper over
      complete (drained) streams for the loaders, and `EJavaObject` decoding through
      the `XMLHelper` override with a per-stream `ObjectInputFilter` allow-list, including primitive arrays and
      limits (D7). Verify:
      - round-trip tests from a file, a jar URL and a nested path, with 0 unresolved proxies and ASM-reference
        identity;
      - `QueryModelInfo` → expression-model references resolve;
      - every observed constant type, including `BigInteger`/`BigDecimal`, loads through the filter;
      - a mismatched ASM gives > 0 unresolved;
      - a disallowed constant type fails the load.

## 5. Asm2QueryWork

- [x] 5.1 Add `Asm2QueryWork` and `Asm2QueryConfiguration` on tatami-core's work API, without `EmfUtils`. They read
      `AsmModel`, the run's `ExpressionModel` (absent, or not created in this run per `ExpressionModelOrigin`: skip with
      a WARN, no artifact) and optional `MeasureModel` (empty set when absent), and put the `QueryModel` into the
      `TransformationContext`. The output predicate is not in the Work; it lives in each `WorkflowHelper` (D6). Verify: a Work test on Northwind asserts that
      `getByClass(ExpressionModel.class)` is the same instance with the same content after the stage, and that a
      context without an expression model, or with a preloaded one, yields no `QueryModel`.

## 6. asm2expression lambda default

- [x] 6.1 `judo-tatami-asm2expression`: `Asm2ExpressionConfiguration.resolveOnlyCurrentLambdaScope` defaults to
      `false` (D4). `Asm2ExpressionWork` puts an `ExpressionModelOrigin` (created in this run, or preloaded) into the
      context (D4 A1). Update `Asm2ExpressionTest` / `Asm2ExpressionWorkTest` expectations and add the
      `asm2expression` spec delta (MODIFIED "Configure Lambda Scope Resolution"). Verify: the module's tests pass
      and assert the default and both origins; `Asm2ExpressionTranformationSerivce` (OSGi) and the
      `ExpressionValidationOn*WorkTest` classes stay green with the new default; `openspec validate` in the
      tatami-base store.

## 7. Skill package relocation

- [x] 7.1 Relocate `judo-runtime-core-expression`'s bundled skill package and `JarSkillPackageTest` into
      `judo-tatami-asm2query`, or retire them, coordinated with the runtime-core change
      `include-plugin-skills-in-jar`. Verify: the asm2query build passes, and that change's tasks no longer
      reference the removed module.

## 8. Documentation

- [x] 8.1 Documentation:
      - add the `asm2query` capability to the tatami-base store, absorbing the runtime-core `expression` spec
        content;
      - add `AGENTS.md` rows for every new file;
      - point `docs/query-externalization-analysis.md` at this change as superseding it;
      - at archive, hand-edit the prose the deltas cannot reach: `openspec/specs/asm2expression/spec.md` line 10
        still says the lambda default is `true`.

      Verify: `openspec validate` in that store; `kb_search --doc-type agents "Asm2Query"` hits.

## 9. Tests (test-plan)

- [x] 9.1 Add unit test: stage leaves context ExpressionModel unchanged — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind context after asm2expression / run `Asm2QueryWork` / `getByClass(ExpressionModel.class)` is the same instance, `EqualityHelper`-equal to a pre-stage copy; one `QueryModel` present (test-plan #TP-8)
- [x] 9.2 Add unit test: asm2expression default lambda=false + logical query URI — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind, default `Asm2ExpressionConfiguration` / `Asm2Expression` run, then `Asm2Query` / the extraction used `resolveOnlyCurrentLambdaScope=false` (config captured); the query resource URI is `NorthwindDemo-query.model` (test-plan #TP-10)
- [x] 9.3 Add unit test: hrefs use runtime ASM and expression file names — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind ASM and expression model under in-memory URIs / `QueryModelPersistence.save` / every ASM href prefix in `NorthwindDemo-query.model` is `NorthwindDemo-asm.model#`, every expression href prefix `NorthwindDemo-expression.model#`; no other prefix except Ecore `nsURI`s (test-plan #TP-13)
- [x] 9.4 Add unit test: duplicate-id guard (unreferenced vs referenced) — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind ASM with a synthetic duplicate `xmi:id` (a) on an unreferenced annotation, (b) on a referenced `EAttribute` / save / (a) artifact written; (b) build succeeds, no artifact, WARN contains the duplicated id (test-plan #TP-14)
- [x] 9.5 Add unit test: foreign-resource reference refused — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: query graph with one reference into a third resource / save / no artifact; WARN names the foreign resource URI (test-plan #TP-15)
- [x] 9.6 Add unit test: index round trip — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind / build → save → load → `QueryIndex` restore / equal `getQuery` for every mapped transfer type, `getNavigation`, `getDataQuery`, `isOrdered`, counters; `entityTypeExpressions` entry count and identity of expression objects equal (test-plan #TP-16)
      <!-- PART A: in-memory half done (Asm2QueryTest.restoredIndexEqualsBuiltIndex: build -> QueryIndex.restore, lookups,
           counters, entity expression identity). The save -> load leg: QueryModelPersistenceTest.savedAndLoadedIndexEqualsTheBuiltIndex (part B). -->
- [x] 9.7 Add unit test: isStatic* for every ASM feature — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind / restored vs built `isStaticAttribute` / `isStaticReference` for every declared ASM `EAttribute` / `EReference` / 0 mismatches; the static set contains only evaluated features with empty variable lists (test-plan #TP-18)
- [x] 9.8 Add unit test: binding index equals reference scan — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind expression model / every collector lookup through the binding index vs the test-only reference scan / identical elements in identical order for every binding class and type FQN (test-plan #TP-59)
- [x] 9.9 Add unit test: preloaded expression model: query stage skipped — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind ASM; a saved `-expression.model` put into the context before `Asm2ExpressionWork` / `Asm2ExpressionWork`, then `Asm2QueryWork` / `ExpressionModelOrigin` = preloaded; WARN "expression model not extracted in this run"; no `QueryModel` in context (test-plan #TP-62)
      <!-- PART A: Asm2ExpressionWork half done (Asm2ExpressionWorkTest.testSavedExpressionModelPreloadedBeforeWork:
           preloaded saved model reused, ExpressionModelOrigin = PRELOADED). The Asm2QueryWork half: Asm2QueryWorkTest.preloadedExpressionModelSkipsTheQueryStage (part B). -->
- [x] 9.10 Add unit test: disallowed constant type rejected — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: `Constant.value` hex-encodes a `java.util.Date`, and separately a custom `Serializable` / load / load fails with a filter rejection (the runtime fallback is TP-35) (test-plan #TP-36)
- [x] 9.11 Add unit test: allowed constant types load — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: constants of `BigInteger`, `BigDecimal`, `Boolean`, `Integer`, `String`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime` / load through the filter / all load; types preserved (`getClass()` equal) (test-plan #TP-37)
- [x] 9.12 Add unit test: measure model absent — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind without a measure model / build + save, then restore with no measure model / `measureDigest=none` on both sides; restore succeeds (test-plan #TP-54)
- [x] 9.13 Add unit test: restore vs build timing (measurement) — copy harness glue from runtime/judo-tatami-base/judo-tatami-asm2expression/src/test/java/hu/blackbelt/judo/tatami/asm2expression/Asm2ExpressionTest.java (Work tests: Asm2ExpressionWorkTest.java); Triple: Northwind artifacts (tatami-base test models), 5 restores after 1 warm-up / restore and build wall times logged to the test report; no pass/fail threshold (C1); asserts only that both complete / 5 iterations, median (test-plan #TP-46)
