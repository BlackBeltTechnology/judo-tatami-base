# JUDO Tatami Base - Project Documentation

## Project Overview


**Repository:** BlackBeltTechnology/judo-tatami-base
**License:** Eclipse Public License 2.0 (EPL-2.0)
**Java Version:** 21
**Build System:** Maven 3.9.4+ with Maven Bundle Plugin (OSGi), Eclipse Epsilon runtime

1. Implements the complete model transformation pipeline for the JUDO platform, converting Platform-Specific Models (PSM) through intermediate representations into concrete artifacts (database schemas, migration scripts, expression models, identity configurations)
2. Each transformation step is a self-contained Maven module packaged as an OSGi bundle, with transformation logic written in Eclipse Epsilon ETL/EOL/EGL/EVL scripts
3. Provides three integration patterns: direct Java API (static builder methods), workflow integration (`AbstractTransformationWork` subclasses), and OSGi service discovery
4. Supports multiple database dialects (HSQLDB, PostgreSQL, Oracle) with configurable naming conventions and both full and incremental Liquibase migration generation
5. All transformations produce `TransformationTrace` objects for full source-to-target traceability

## Code Instructions

1. First think through the problem, read the codebase for relevant files.
2. Before you make any major changes, check in with me and I will verify the plan.
3. Please every step of the way just give me a high level explanation of what changes you made.
4. Make every task and code change you do as simple as possible. We want to avoid making any massive or complex changes. Every change should impact as little code as possible. Everything is about simplicity.
5. Maintain a documentation file that describes how the architecture of the app works inside and out.
6. Never speculate about code you have not opened. If the user references a specific file, you MUST read the file before answering. Make sure to investigate and read relevant files BEFORE answering questions about the codebase. Never make any claims about code before investigating unless you are certain of the correct answer - give grounded and hallucination-free answers.
7. For implementation use TDD (Test-Driven Development): write or update tests first to define the expected behaviour, verify they fail, then write the minimal implementation to make them pass.
8. Use DRY (Don't Repeat Yourself): extract reusable logic into separate classes, utilities, or components. If the same pattern appears in multiple places, refactor it into a shared helper.

## Directory Structure

```
judo-tatami-base/
├── judo-tatami-psm2asm/          # PSM → ASM transformation
├── judo-tatami-psm2measure/      # PSM → Measure transformation
├── judo-tatami-asm2rdbms/        # ASM → RDBMS transformation (multi-dialect)
├── judo-tatami-asm2expression/   # ASM → Expression transformation (JQL-based)
├── judo-tatami-asm2keycloak/     # ASM → Keycloak transformation
├── judo-tatami-rdbms2liquibase/  # RDBMS → Liquibase migration generation
├── judo-tatami-psm-validation/   # PSM model validation
├── judo-tatami-asm-validation/   # ASM model validation
├── judo-tatami-expression-asm-validation/  # Expression-on-ASM validation
├── judo-tatami-expression-psm-validation/  # Expression-on-PSM validation
├── images/                       # Architecture diagrams (PNG)
├── .github/workflows/            # CI/CD pipelines
├── .mvn/                         # Maven wrapper config, JVM args
└── pom.xml                       # Parent POM (all module/dependency management)
```

## Core Modules

### Transformation Modules

| Module | Type | Input → Output | Description |
|--------|------|---------------|-------------|
| `judo-tatami-psm2asm/` | ETL | PSM → ASM | Converts platform-specific types, entities, and relations into application-specific model structures |
| `judo-tatami-psm2measure/` | ETL | PSM → Measure | Extracts measurement unit definitions and conversions from PSM |
| `judo-tatami-asm2rdbms/` | ETL | ASM → RDBMS | Generates relational database schema with HSQLDB/PostgreSQL/Oracle dialect support |
| `judo-tatami-asm2expression/` | Java | ASM → Expression | Builds expression trees from JQL queries using the expression builder API |
| `judo-tatami-asm2keycloak/` | ETL | ASM → Keycloak | Generates identity provider and authorization model for Keycloak |
| `judo-tatami-rdbms2liquibase/` | ETL/EGL | RDBMS → Liquibase | Generates database migration changesets (full and incremental support) |

### Validation Modules

| Module | Type | Models Validated | Description |
|--------|------|-----------------|-------------|
| `judo-tatami-psm-validation/` | EVL | PSM | Validates PSM model constraints and invariants |
| `judo-tatami-asm-validation/` | EVL | ASM | Validates ASM model constraints |
| `judo-tatami-expression-asm-validation/` | EVL | Expression + ASM | Validates expression models against ASM structure |
| `judo-tatami-expression-psm-validation/` | EVL | Expression + PSM + ASM | Validates expression models against PSM structure |

### Module Internal Structure

Each transformation module follows this consistent layout:

```
judo-tatami-{module}/
├── src/main/java/hu/blackbelt/judo/tatami/{module}/
│   ├── {Module}.java                          # Transformation executor (static methods, @Builder parameter)
│   ├── {Module}Work.java                      # Workflow integration (extends AbstractTransformationWork)
│   ├── {Module}TransformationTrace.java       # Source→target EObject traceability
│   └── osgi/
│       ├── {Module}Transformation{Model}Tracker.java  # OSGi model service tracker
│       └── {Module}TransformationSerivce.java         # OSGi service registration
├── src/main/epsilon/transformations/
│   ├── {sourceToTarget}.etl                   # Main ETL transformation script
│   ├── modules/                               # Modular sub-transformation ETL scripts
│   └── utils/                                 # Shared EOL utility operations
├── src/test/java/hu/blackbelt/judo/tatami/{module}/
│   └── {Module}Test.java                      # JUnit 5 tests
└── pom.xml                                    # Module POM (bundle packaging)
```

> **Note:** The OSGi service class uses the legacy typo `Serivce` (not `Service`) in most modules.

## Technology Stack

### Core Technologies
- **Eclipse Epsilon** (2.8.0) — ETL/EOL/EGL/EVL transformation and validation engine
- **Eclipse EMF** — Ecore-based metamodel and model framework (EObjects, Resources, URIs)
- **OSGi** (6.0.0) — Module system for service registration, discovery, and lifecycle management
- **Lombok** (1.18.34) — `@Slf4j`, `@Builder`, `@Getter`, `@NonNull` annotations throughout
- **Google Guava** (30.0) — Collections, hashing utilities

### Meta-Model Dependencies
- `judo-meta-psm` — Platform-Specific Model metamodel
- `judo-meta-asm` — Application-Specific Model metamodel
- `judo-meta-rdbms` — RDBMS metamodel
- `judo-meta-expression` — Expression metamodel
- `judo-meta-measure` — Measurement metamodel
- `judo-meta-keycloak` — Keycloak identity metamodel
- `judo-meta-liquibase` — Liquibase migration metamodel
- `judo-meta-jql` — JQL query language metamodel

### Build & Quality
- **Maven 3.9.4+** with Maven Bundle Plugin (Apache Felix) for OSGi packaging
- **JUnit Jupiter 5.9.1** with Hamcrest and Mockito for testing
- **TestContainers** — PostgreSQL and HSQL containers for `rdbms2liquibase` integration tests
- **Atomikos** — Transaction manager for integration tests
- **JaCoCo** (0.8.12) — Code coverage
- **SonarQube** — Static analysis (host: sonar.judo.technology)

## Build Commands

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

### Maven Profiles

| Profile | Purpose |
|---------|---------|
| `modules` | Activates all 10 transformation/validation submodules (active by default) |
| `generate-checksum` | Generates SHA-256 checksums for build artifacts |
| `sign-artifacts` | Signs artifacts with GPG for release |
| `release-dummy` | Deploys to a local file-based repository (testing) |
| `release-judong` | Deploys to the JuDong Nexus repository |
| `release-central` | Deploys to Maven Central with Nexus staging |
| `generate-github-asciidoc-diagrams` | Renders PlantUML diagrams from documentation |
| `update-source-code-license` | Updates license headers in source files |

## Key Configuration Files

| File | Purpose |
|------|---------|
| `pom.xml` | Parent POM — all module declarations, dependency management, plugin configuration, version properties |
| `.mvn/jvm.config` | JVM flags: `-Xms1024m -Xmx2048m`, `--add-opens` for Java 21 compatibility |
| `.mvn/extensions.xml` | Maven extensions (flatten plugin, etc.) |
| `logback-test.xml` | Shared Logback configuration for test logging across all modules |
| `.github/workflows/build.yml` | Main CI pipeline (build, deploy, tag, release) |
| `.github/workflows/release.yml` | Manual release workflow |
| `.github/workflows/merge-pr-tagged.yml` | Automated PR merge routing |
| `.github/workflows/create-release-on-master.yml` | Final release on master branch |

## Development Environment

**Required:**
- Java 21 JDK
- Maven 3.9.4+ (or use `./mvnw`)
- Sufficient memory: the build requires at least 2 GB heap (configured in `.mvn/jvm.config`)

**Optional for integration tests:**
- Docker (required for TestContainers in `rdbms2liquibase` tests)

**Test model:** All transformation tests use the `NorthwindDemo` sample PSM model from `judo-meta-psm`. The typical test pattern is: load sample model → execute transformation → assert trace and model contents → verify serialization round-trip.

## Git Workflow

- **Main Branch:** `develop`
- **Release Branch:** `master` (contains latest stable release)
- **Versioning:** Semantic versioning; current `1.1.6-SNAPSHOT`
- **Branch naming:** `feature/JNG-XXXX_summary`, `bugfix/JNG-XXXX_summary`, `hotfix/JNG-XXXX_summary`
- **Commit rule:** Every commit must reference a JIRA ticket (`JNG-XXXX`)
- **CI:** GitHub Actions with automated build, deploy, tagging, and changelog generation
- See [CIFLOW.md](.github/CIFLOW.md) for detailed branching and CI/CD workflow documentation

## Important Notes

1. **Epsilon scripts are the primary transformation logic** — Java code handles orchestration and framework integration, but the actual model transformation rules live in `.etl`, `.eol`, `.egl`, and `.evl` files under `src/main/epsilon/`
2. **The `asm2expression` module is the exception** — it uses Java-based JQL expression building instead of Epsilon ETL scripts
3. **OSGi service class naming typo** — most modules use `TransformationSerivce` (note the typo) rather than `TransformationService`; this is legacy and should be preserved for compatibility
4. **Database dialect configuration** — `asm2rdbms` generates dialect-specific mapping models at compile time via `ExcelMappingModels2Rdbms` for HSQLDB, PostgreSQL, and Oracle
5. **The `rdbms2liquibase` module supports incremental migrations** — `Rdbms2LiquibaseIncremental` compares two RDBMS model versions to generate only the changes
6. **All transformations are traceable** — every ETL-based module produces `TransformationTrace` objects that map source EObjects to target EObjects, serializable for persistence
7. **Builder pattern is pervasive** — all transformation parameters use Lombok `@Builder` with sensible defaults; look for the static builder method (e.g., `psm2AsmParameter()`)
8. **Epsilon scripts are copied during build** — the `maven-resources-plugin` copies scripts from `src/main/epsilon/` to `target/classes/tatami/{module}/transformations/` at build time

## Related Documentation

- [README.md](README.md) — Project overview and quickstart
- [CONTRIBUTING.md](CONTRIBUTING.md) — Development setup, issue reporting, and PR guidelines
- [.github/CIFLOW.md](.github/CIFLOW.md) — Detailed branching strategy and CI/CD workflow documentation
- [judo-community](https://github.com/BlackBeltTechnology/judo-community) — Parent aggregator project and ecosystem documentation
