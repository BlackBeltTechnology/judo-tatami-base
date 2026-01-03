# judo-tatami-base

[![Build](https://github.com/BlackBeltTechnology/judo-tatami-base/actions/workflows/build.yml/badge.svg?branch=develop)](https://github.com/BlackBeltTechnology/judo-tatami-base/actions/workflows/build.yml)

## Introduction

This project contains the transformation steps which are used to make the full transformation pipeline to create application models from source models for the JUDO platform. This project contains pipeline steps only which can be utilized in workflows defined in other projects (for example [judo-tatami-jsl](https://github.com/BlackBeltTechnology/judo-tatami-jsl)).

The order of steps shows the relation between transformation steps source/destination model and how the steps can be chained.

## Transformation Pipeline

```
PSM (Platform Specific Model)
  ↓ (judo-tatami-psm2asm)
ASM (Abstract Semantic Model)
  ├→ (judo-tatami-asm2rdbms) → RDBMS Schema
  │   └→ (judo-tatami-rdbms2liquibase) → Liquibase Changelog
  ├→ (judo-tatami-asm2expression) → Expression Model
  └→ (judo-tatami-asm2keycloak) → Keycloak Config
PSM
  └→ (judo-tatami-psm2measure) → Measure Model
```

## Modules

| Module | Source | Target | Description |
|--------|--------|--------|-------------|
| judo-tatami-psm2asm | PSM | ASM | Transform platform model to abstract semantic model |
| judo-tatami-psm2measure | PSM | Measure | Extract measurement units and definitions |
| judo-tatami-asm2rdbms | ASM | RDBMS | Generate relational database schema |
| judo-tatami-rdbms2liquibase | RDBMS | Liquibase | Generate database migration scripts |
| judo-tatami-asm2expression | ASM | Expression | Build expression model |
| judo-tatami-asm2keycloak | ASM | Keycloak | Generate Keycloak authentication config |

### Validation Modules

| Module | Model | Description |
|--------|-------|-------------|
| judo-tatami-psm-validation | PSM | PSM model validation |
| judo-tatami-asm-validation | ASM | ASM model validation |
| judo-tatami-expression-asm-validation | Expression/ASM | Expression validation on ASM |
| judo-tatami-expression-psm-validation | Expression/PSM | Expression validation on PSM |

## Build

### Requirements

- Java 21
- Maven 3.9.4+

### Build Commands

```bash
# Standard build
mvn clean install

# Using Maven wrapper
./mvnw clean install

# Run tests only
mvn clean test

# Skip tests
mvn clean install -DskipTests
```

## External Model Testing

The project supports parametrized testing of ETL and ZETA transformations against external model files. This enables testing with real-world models without hardcoding paths.

### Configuration

Create `external-model-tests.properties` in your module's `src/test/resources/`:

```properties
# Format: <model-name>=<path>[;<param>=<value>]*

# Simple format (relative path from module root)
rackinspect=../../../rackinspect/application/model/target/generated-resources/model

# Extended format with parameters
myproject=/opt/models/myproject;dialect=postgresql;warmup=true;iterations=3
```

### Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `dialect` | `hsqldb` | Database dialect (for RDBMS transformations) |
| `warmup` | `false` | Run warmup before measurement |
| `iterations` | `1` | Number of iterations for averaging |

### Expected Model Files

| Test Class | Model Type | Expected File |
|------------|------------|---------------|
| `Psm2AsmExternalModelTest` | PSM | `<name>-psm.model` |
| `Psm2MeasureExternalModelTest` | PSM | `<name>-psm.model` |
| `Asm2RdbmsExternalModelTest` | ASM | `<name>-asm.model` |
| `Asm2KeycloakExternalModelTest` | ASM | `<name>-asm.model` |
| `Rdbms2LiquibaseExternalModelTest` | RDBMS | `<name>-rdbms_<dialect>.model` |

### Running Tests

```bash
# Run external model tests for a specific module
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest -Pperformance

# Run with STRICT comparison mode
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest -Pperformance -Djudo.test.comparison.mode=STRICT

# Run all external model tests
mvn test -Pperformance -Dtest=*ExternalModelTest

# Override module base directory
mvn test -Pperformance -Dtest=*ExternalModelTest -Djudo.test.module.root=/path/to/module
```

### Path Resolution

1. Absolute paths (starting with `/`) are used as-is
2. Relative paths are resolved from the module root directory
3. Module root is detected via JUnit's classpath or can be overridden with `-Djudo.test.module.root`

### Behavior

- If the properties file is missing: Tests skip gracefully (0 test cases)
- If the model directory doesn't exist: That model is skipped with a warning
- If the model file is missing in an existing directory: Test **fails** with a clear error

## Dual Transformation Architecture

This project supports two transformation engines:

1. **ETL (Epsilon Transformation Language)** - The original implementation using Epsilon scripts
2. **Zeta (Java-based)** - A new Java implementation using the Zeta framework

Both engines produce functionally equivalent output and can be selected at runtime. See [Transformation Documentation](docs/transformations/README.md) for details.

## Context

This project is a building block of the [judo-community](https://github.com/BlackBeltTechnology/judo-community) aggregator project. In order to better understand how this module fits into our ecosystem, please check the corresponding documentation!

## Contributing to the project

Everyone is welcome to contribute to JUDO! As a starter, please read the corresponding [CONTRIBUTING](CONTRIBUTING.md) guide for details!

## License

This project is licensed under the [Eclipse Public License - v 2.0](https://www.eclipse.org/legal/epl-2.0/).
