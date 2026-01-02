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
