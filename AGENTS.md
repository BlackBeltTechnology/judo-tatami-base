# judo-tatami-base — module agent doctrine

## Module purpose

`judo-tatami-base` is the **middle of the JUDO transformation chain** — the
lowering steps that turn a canonical PSM into every concrete artifact a running
application needs. It starts where the editor-facing repository stops: given a
PSM, it derives the ASM (the application-level structure that runtime and SDK
generation both consume), the measure model, the relational schema, the
Liquibase changelogs that bring a database up to that schema, the resolved
expression model behind every derived attribute and filter, and the Keycloak
realm that guards the whole thing. It also owns the validation steps that fail a
model at the earliest layer where the defect is expressible — PSM, ASM, and
expressions against either.

This reactor is deliberately a **library of steps, not a pipeline**. Nothing here
decides what runs next: each module exposes the same three integration shapes —
a direct Java API (static method plus a Lombok `@Builder` parameter object), an
`AbstractTransformationWork` subclass for workflow embedding, and an OSGi service
with a model-service tracker — and the caller composes them. Every ETL-based step
emits a `TransformationTrace` mapping source `EObject`s to their targets, so the
composition stays debuggable across step boundaries.

Position relative to its neighbours:

- **`judo-tatami-core`** (external dependency) is *below* every module here: it
  provides `TransformationContext`, `Work`, `WorkFlow`, `WorkFlowEngine` and
  `AbstractTransformationWork`. This repository extends those primitives and
  never redefines them.
- **`judo-tatami`** (sibling repository) is *around* this one: it holds the two
  outer edges (ESM→PSM at the front, ASM→Script→Operation at the back) and, in
  `judo-tatami-workflow`, the definition that sequences the steps published here
  into `DefaultWorkflow` / `EsmDefaultWorkflow` / `PsmDefaultWorkflow`.
- **`judo-tatami-pipeline`** is *above* both: the newer pipeline runner that
  consumes these bundles as pipeline stages rather than as workflow `Work` items.

Each step ships its rules **twice**: as Eclipse Epsilon ETL/EOL/EGL scripts under
`src/main/epsilon/`, and as an annotated Java rule set under `.../zeta/rules`
driven by the Zeta transformation core. Comparison and trace-equivalence tests
hold the two engines to the same output — this dual-engine invariant, not any one
engine, is the contract of this repository.

**Repository:** BlackBeltTechnology/judo-tatami-base ·
**Artifact:** `hu.blackbelt.judo.tatami:judo-tatami-base-parent` (aggregator POM,
version `${revision}` = `1.1.6-SNAPSHOT`) ·
**License:** EPL-2.0 · **Java:** 21 ·
**Build:** Maven 3.9.4+ with the Apache Felix bundle plugin (OSGi) and the
Eclipse Epsilon runtime.

## Reactor map

The root `pom.xml` declares its `<modules>` inside the `modules` profile, which
activates on `skipModules != true` — on by default. Declared order is build
order, and it is not alphabetical by accident: `judo-tatami-test-utils` comes
first because every downstream module's performance and comparison tests depend
on it, and `psm2asm` precedes everything that needs an ASM to test against.

<modules>
  <module>judo-tatami-test-utils</module>
  <module>judo-tatami-psm2asm</module>
  <module>judo-tatami-psm2measure</module>
  <module>judo-tatami-asm2rdbms</module>
  <module>judo-tatami-rdbms2liquibase</module>
  <module>judo-tatami-asm2expression</module>
  <module>judo-tatami-psm-validation</module>
  <module>judo-tatami-asm-validation</module>
  <module>judo-tatami-expression-asm-validation</module>
  <module>judo-tatami-expression-psm-validation</module>
  <module>judo-tatami-asm2keycloak</module>
</modules>

| Reactor module | Packaging | Reads → writes, and what it is for |
|---|---|---|
| `judo-tatami-test-utils` | `jar` | **Test infrastructure the other ten depend on — the only non-bundle here.** Generates realistic PSM/ASM/RDBMS model data at chosen scale (`RealisticPsmModelGenerator` and siblings) so performance numbers come from model shapes resembling real projects; supplies `StructuralModelComparator` / `ModelChecksumCalculator` for engine-vs-engine output diffing, `DualTraceComparator` for trace equivalence, and a JUnit 5 `ProfilingExtension` that drives the jvm-profiler agent and an LLM-backed hotspot analyser. Published as a normal jar because it is consumed at `test` scope, not deployed to a container. |
| `judo-tatami-psm2asm` | `bundle` | **PSM → ASM.** The pivot of the estate: lowers platform-level namespaces, entity data, relations, transfer objects, actors, static/derived members and bound operations into the application-specific model every later step reads. Both engines present — ETL modules under `epsilon/transformations/asm/modules/` and `Psm2AsmZetaTransformation` with `zeta/rules/*Rules` — and `Psm2AsmTransformationTrace` records the source-to-target mapping consumers navigate. |
| `judo-tatami-psm2measure` | `bundle` | **PSM → Measure.** Splits the unit-of-measure world out of the PSM into a standalone measure model: measures, their units, and the conversion factors between them. Kept separate because the expression layer resolves a measured value against this model, not against the PSM — `asm2expression` and both expression validators consume it. |
| `judo-tatami-asm2rdbms` | `bundle` | **ASM → RDBMS.** Turns application classes, attributes and references into tables, fields, foreign keys and constraints. Dialect-aware: `ExcelMappingModels2Rdbms` converts the Excel-authored type/name-mapping and rule workbooks into dialect mapping models at build time, so HSQLDB, PostgreSQL and Oracle each get correct types and identifier lengths; `AbbreviateUtils` shortens generated identifiers to the target's limit deterministically. |
| `judo-tatami-rdbms2liquibase` | `bundle` | **RDBMS → Liquibase.** Emits the changelog that materialises a schema — full creation via `Rdbms2Liquibase`, and, via `Rdbms2LiquibaseIncremental`, the delta between two RDBMS model versions so an existing database can be migrated rather than rebuilt. The incremental path is decomposed into ordered sub-transformations (backup, checkup, data update before/after, drop backup); EGL templates generate the raw SQL fragments. Tests run against live HSQLDB/PostgreSQL/YugabyteDB containers. |
| `judo-tatami-asm2expression` | `bundle` | **ASM (+ Measure) → Expression.** The one step with no Epsilon scripts: it parses the JQL bodies attached to an ASM and builds typed expression trees through the JQL expression builder, resolving names against the ASM adapter and units against the measure adapter. Produces the expression model the runtime evaluates for derived attributes, filters and default values. |
| `judo-tatami-psm-validation` | `bundle` | **PSM → diagnostics.** Epsilon EVL constraints over the platform model, run before any lowering, so a structurally invalid PSM is rejected where the modeller can still recognise their own construct — rather than as an unexplainable ASM or SQL failure downstream. Produces no model. |
| `judo-tatami-asm-validation` | `bundle` | **ASM → diagnostics.** EVL constraints on the lowered application model, catching invariants that only become expressible after `psm2asm` has run (hence its dependency on that module for test fixtures). Both a classic work item and a Zeta work item are published. |
| `judo-tatami-expression-asm-validation` | `bundle` | **Expression + ASM (+ Measure) → diagnostics.** Checks that every built expression type-checks against the ASM it will actually be evaluated over, using the ASM and measure adapters. Depends on the real `psm2asm` / `psm2measure` / `asm2expression` chain so validation runs against genuinely produced models, never mocks. |
| `judo-tatami-expression-psm-validation` | `bundle` | **Expression + PSM (+ ASM) → diagnostics.** The same check pushed up to the authoring layer: expressions are validated against the PSM adapter, so an error is reported in terms of the model the author wrote instead of the derived one. Answers "is this expression legal where it was written", which the ASM-side validator structurally cannot. |
| `judo-tatami-asm2keycloak` | `bundle` | **ASM → Keycloak.** Derives the identity and authorisation model from the actors declared in an ASM: a `Realm` with its `Client`s, grant flags and attribute bindings, ready for `judo-meta-keycloak` to export as realm-import JSON. Makes the security configuration of a generated application a transformation output rather than hand-maintained infrastructure. |

## Module internal structure

Each transformation module follows this consistent layout:

```
judo-tatami-{module}/
├── src/main/java/hu/blackbelt/judo/tatami/{module}/
│   ├── {Module}.java                          # Transformation executor (static methods, @Builder parameter)
│   ├── {Module}Work.java                      # Workflow integration (extends AbstractTransformationWork)
│   ├── {Module}TransformationTrace.java       # Source→target EObject traceability
│   ├── osgi/
│   │   ├── {Module}Transformation{Model}Tracker.java  # OSGi model service tracker
│   │   └── {Module}TransformationSerivce.java         # OSGi service registration
│   └── zeta/
│       ├── {Module}ZetaTransformation.java    # Zeta-engine entry point
│       ├── {Module}RuleNames.java             # Rule-name constants shared with traces
│       └── rules/                             # Annotated Java transformation rules
├── src/main/epsilon/transformations/
│   ├── {sourceToTarget}.etl                   # Main ETL transformation script
│   ├── modules/                               # Modular sub-transformation ETL scripts
│   └── utils/                                 # Shared EOL utility operations
├── src/test/java/hu/blackbelt/judo/tatami/{module}/
│   ├── {Module}Test.java                      # JUnit 5 tests
│   └── perf/                                  # Performance + engine-comparison tests
└── pom.xml                                    # Module POM (bundle packaging)
```

> **Note:** The OSGi service class uses the legacy typo `Serivce` (not `Service`) in most modules.

## Build commands

```bash
# Full build and install to local repository
mvn clean install

# Run all tests
mvn clean test

# Build a single module
mvn clean install -pl judo-tatami-psm2asm

# Run tests for a single module
mvn clean test -pl judo-tatami-asm2rdbms

# Run a specific test class
mvn clean test -pl judo-tatami-psm2asm -Dtest=Psm2AsmTest

# Skip tests
mvn clean install -DskipTests
```

The Maven wrapper (`./mvnw`) is included. JVM memory and module flags are configured in `.mvn/jvm.config`.

### Maven profiles

| Profile | Purpose |
|---------|---------|
| `modules` | Activates all 11 test-utility/transformation/validation submodules (active by default; disable with `-DskipModules=true`) |
| `performance` | Runs only the `performance`-tagged tests, sets `judo.test.profiler.enabled=true` and `judo.test.profiler.thresholdMs=100`, and attaches the jvm-profiler Java agent writing `Stacktrace.json` / `CpuAndMemory.json` into `target/profiler-output` |
| `generate-checksum` | Generates SHA-256 checksums for build artifacts |
| `sign-artifacts` | Signs artifacts with GPG for release |
| `release-dummy` | Deploys to a local file-based repository (testing) |
| `release-judong` | Deploys to the JuDong Nexus repository |
| `release-central` | Deploys to Maven Central with Nexus staging |
| `generate-github-asciidoc-diagrams` | Renders PlantUML diagrams from documentation |
| `update-source-code-license` | Updates license headers in source files |

## Configuration surface

The root `pom.xml` is the single build contract: module declarations, all
dependency management, plugin configuration and version properties (`${revision}`
drives the version). Build JVM settings live in `.mvn/jvm.config` — `-Xms1024m
-Xmx2048m` plus the `--add-opens` flags Java 21 requires for EMF reflection —
and Maven extensions (flatten plugin and friends) in `.mvn/extensions.xml`. Test
logging is shared: every module reads the repository-root `logback-test.xml`, so
a logging change is made once at the root rather than per module. CI is defined
under `.github/workflows/`: `build.yml` (build, deploy, tag, release),
`release.yml` (manual release), `merge-pr-tagged.yml` (automated PR merge
routing) and `create-release-on-master.yml` (final release on master).
Architecture diagrams live in `images/`.

## Technology stack

### Core technologies
- **Eclipse Epsilon** (2.8.0) — ETL/EOL/EGL/EVL transformation and validation engine
- **Eclipse EMF** — Ecore-based metamodel and model framework (EObjects, Resources, URIs)
- **JUDO Zeta** (`zeta.annotations`, `zeta.transformation-core`, `zeta.common`) — annotated-Java transformation engine running beside Epsilon
- **OSGi** (6.0.0) — Module system for service registration, discovery, and lifecycle management
- **Lombok** (1.18.34) — `@Slf4j`, `@Builder`, `@Getter`, `@NonNull` annotations throughout
- **Google Guava** (30.0) — Collections, hashing utilities

### Metamodel dependencies

`judo-meta-psm` (Platform-Specific Model), `judo-meta-asm` (Application-Specific
Model), `judo-meta-rdbms` (RDBMS), `judo-meta-expression` (expression model),
`judo-meta-measure` (measurement), `judo-meta-keycloak` (Keycloak identity),
`judo-meta-liquibase` (Liquibase migration) and `judo-meta-jql` (JQL query
language). The `*.northwind` variants supply the demo model every test loads.

### Build & quality
- **Maven 3.9.4+** with Maven Bundle Plugin (Apache Felix) for OSGi packaging
- **JUnit Jupiter 5.9.1** with Hamcrest and Mockito for testing
- **TestContainers** — PostgreSQL, HSQL and YugabyteDB containers for `rdbms2liquibase` integration tests
- **Atomikos** — Transaction manager for integration tests
- **jvm-profiler** + **langchain4j** — Sampling profiler and LLM hotspot analysis behind the `performance` profile
- **JaCoCo** (0.8.12) — Code coverage
- **SonarQube** — Static analysis (host: sonar.judo.technology)

## Development environment

**Required:**
- Java 21 JDK
- Maven 3.9.4+ (or use `./mvnw`)
- Sufficient memory: the build requires at least 2 GB heap (configured in `.mvn/jvm.config`)

**Optional for integration tests:**
- Docker (required for TestContainers in `rdbms2liquibase` tests)

**Test model:** All transformation tests use the `NorthwindDemo` sample PSM model
from `judo-meta-psm`. The typical test pattern is: load sample model → execute
transformation → assert trace and model contents → verify serialization
round-trip.

## Git workflow

- **Main Branch:** `develop`
- **Release Branch:** `master` (contains latest stable release)
- **Versioning:** Semantic versioning; current `1.1.6-SNAPSHOT`
- **Branch naming:** `feature/JNG-XXXX_summary`, `bugfix/JNG-XXXX_summary`, `hotfix/JNG-XXXX_summary`
- **Commit rule:** Every commit must reference a JIRA ticket (`JNG-XXXX`)
- **CI:** GitHub Actions with automated build, deploy, tagging, and changelog generation
- See [CIFLOW.md](.github/CIFLOW.md) for detailed branching and CI/CD workflow documentation

## Scope guard — invariants an edit must not break

1. **Epsilon scripts are the primary transformation logic** — Java code handles orchestration and framework integration, but the actual model transformation rules live in `.etl`, `.eol`, `.egl`, and `.evl` files under `src/main/epsilon/`
2. **The `asm2expression` module is the exception** — it uses Java-based JQL expression building instead of Epsilon ETL scripts
3. **OSGi service class naming typo** — most modules use `TransformationSerivce` (note the typo) rather than `TransformationService`; this is legacy and should be preserved for compatibility
4. **Database dialect configuration** — `asm2rdbms` generates dialect-specific mapping models at compile time via `ExcelMappingModels2Rdbms` for HSQLDB, PostgreSQL, and Oracle
5. **The `rdbms2liquibase` module supports incremental migrations** — `Rdbms2LiquibaseIncremental` compares two RDBMS model versions to generate only the changes
6. **All transformations are traceable** — every ETL-based module produces `TransformationTrace` objects that map source EObjects to target EObjects, serializable for persistence
7. **Builder pattern is pervasive** — all transformation parameters use Lombok `@Builder` with sensible defaults; look for the static builder method (e.g., `psm2AsmParameter()`)
8. **Epsilon scripts are copied during build** — the `maven-resources-plugin` copies scripts from `src/main/epsilon/` to `target/classes/tatami/{module}/transformations/` at build time
9. **The dual engine must stay in step** — where a module ships both Epsilon scripts and a Zeta rule set, comparison and trace tests assert identical output. Changing one side alone breaks the equivalence contract, not merely a test.

## Code instructions

1. First think through the problem, read the codebase for relevant files.
2. Before you make any major changes, check in with me and I will verify the plan.
3. Please every step of the way just give me a high level explanation of what changes you made.
4. Make every task and code change you do as simple as possible. We want to avoid making any massive or complex changes. Every change should impact as little code as possible. Everything is about simplicity.
5. Maintain a documentation file that describes how the architecture of the app works inside and out.
6. Never speculate about code you have not opened. If the user references a specific file, you MUST read the file before answering. Make sure to investigate and read relevant files BEFORE answering questions about the codebase. Never make any claims about code before investigating unless you are certain of the correct answer - give grounded and hallucination-free answers.
7. For implementation use TDD (Test-Driven Development): write or update tests first to define the expected behaviour, verify they fail, then write the minimal implementation to make them pass.
8. Use DRY (Don't Repeat Yourself): extract reusable logic into separate classes, utilities, or components. If the same pattern appears in multiple places, refactor it into a shared helper.

## Related documentation

- [README.md](README.md) — Project overview and quickstart
- [CONTRIBUTING.md](CONTRIBUTING.md) — Development setup, issue reporting, and PR guidelines
- [.github/CIFLOW.md](.github/CIFLOW.md) — Detailed branching strategy and CI/CD workflow documentation
- [judo-community](https://github.com/BlackBeltTechnology/judo-community) — Parent aggregator project and ecosystem documentation

<!-- dox-doctrine -->
## Documentation Update Protocol (WRITE discipline)

Per-directory `AGENTS.md` files form a tree. Each directory `AGENTS.md` is the
per-file record for the files in that directory. This module-root `AGENTS.md`
holds doctrine + architecture pointers only — never a per-file index.

**Keep the root lean.** This file loads into every agent turn — every byte costs
tokens on every turn. A verbose root file buries the rules the model must follow
(signal dilution) and measurably degrades adherence; a lean file keeps doctrine
salient. Default assumption: your update does NOT belong in the root — route it
by the table below.

**Route every doc update by kind:**

| Kind of update | Goes in |
|---|---|
| New file in a directory, or its per-file detail / change history | Nearest directory `AGENTS.md`. Add a `` | `<basename>` | <purpose> | `` row, path-alphabetical. |
| Data flow, protocol, architecture rationale | `docs/architecture.md` or a `docs/<topic>.md` |
| End-user / developer setup | `README.md` |
| Cross-cutting rule every agent needs every turn (rare) | this module-root `AGENTS.md` |

**Read before editing (chain walk).** Before editing a file, read the nearest
`AGENTS.md` chain root→leaf so you know the file's recorded purpose, contracts,
and change history. Do not edit blind.

**Update after editing (closeout pass).** After changing a file, update its row
in the nearest directory `AGENTS.md`: find the file's row, update its purpose in
place; if absent, add it in path-alphabetical order. New directory → scaffold
its `AGENTS.md`. One row per file. The purpose carries a one-line summary, key
exported symbols, contracts/invariants, and `See change: <id>` history.

**Row style (caveman).** Short declarative fragments. Drop articles. Subject →
verb → object, present tense. One fact per row. Prefer concrete tokens (paths,
symbols, env vars) over prose. Keep identifiers verbatim.

**Size rule — split an over-large directory `AGENTS.md` file-based.** pi
auto-injects a directory `AGENTS.md` on every turn when cwd sits at/below it, so
an over-large directory `AGENTS.md` is not supported. Split it file-based: a row
exceeding the length threshold promotes to a per-file `<File>.AGENTS.md`
sidecar carrying that file's full detail (including every `See change:`). The
sidecar is pull-only — its name is not `AGENTS.md`, so pi never auto-injects it
— yet it stays search-indexed (`agents` doc_type). The directory `AGENTS.md`
keeps a one-line summary plus a `→ see `<File>.AGENTS.md`` pointer. Rows within
the threshold stay verbatim (lossless).

## Finding docs (READ discipline)

`kb_*` tools are faster and cheaper than raw search — they return a one-line
purpose + key exports per file, not raw bytes. **This fires on the ACTION, not
the intent** — before you `grep`/`rg` for a symbol, `cat`/read a file to learn
what it does, or chase an import, the kb call goes first. It fires **even
mid-task when you already know the file**; knowing the file does not exempt you.
When your reflex is the left column, run the right column instead:

| You're about to… | Do this FIRST instead |
|---|---|
| `grep -rn "SymbolName" src/` — find where a fn / type / const lives | `kb_search --doc-type agents "SymbolName"` — tree indexes key exports per file |
| `grep -rn "feature\|topic" src/` — how does X work / where's X handled | `kb_search "feature topic"` |
| `cat` / read a file just to learn its purpose before editing | `kb agents <path>` — one-line purpose + exports + change history |
| chase imports / callers across files | `kb_neighbors <path\|heading>` |
| read one doc section in full | `kb_get <path> <section>` |

**Fall-through (explicit):** if the kb call returns nothing relevant, `rg` /
source read is allowed — then add the missing directory `AGENTS.md` row per the
WRITE discipline. kb does NOT replace grep; it goes first.
