# Judo Tatami Base - Project Specification

## Project Overview

**Repository:** BlackBeltTechnology/judo-tatami-base  
**License:** Eclipse Public License 2.0 (EPL-2.0)  
**Java Version:** 21  
**Build System:** Maven 3.9.4+ with OSGi bundles

This project contains the transformation steps for the JUDO platform pipeline. It transforms source models (PSM - Platform Specific Model) to target models (ASM, RDBMS, Liquibase, etc.) using Epsilon ETL (Epsilon Transformation Language).

## Current Architecture

### Transformation Pipeline

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

### Transformation Modules

| Module | Source | Target | ETL Files |
|--------|--------|--------|-----------|
| judo-tatami-psm2asm | PSM | ASM | 9 |
| judo-tatami-psm2measure | PSM | Measure | 3 |
| judo-tatami-asm2rdbms | ASM | RDBMS | 8 |
| judo-tatami-rdbms2liquibase | RDBMS | Liquibase | 12 |
| judo-tatami-asm2expression | ASM | Expression | 0 (Java) |
| judo-tatami-asm2keycloak | ASM | Keycloak | 3 |

### Validation Modules

| Module | Model | Implementation |
|--------|-------|----------------|
| judo-tatami-psm-validation | PSM | Java WorkClass |
| judo-tatami-asm-validation | ASM | Java WorkClass |
| judo-tatami-expression-asm-validation | Expression/ASM | Java WorkClass |
| judo-tatami-expression-psm-validation | Expression/PSM | Java WorkClass |

## Technology Stack

- **Epsilon Runtime** 2.8.0 - ETL/EOL execution engine
- **EMF/Ecore** - Eclipse Modeling Framework
- **Apache Felix** 5.1.8 - OSGi bundle plugin
- **JUnit 5** - Testing framework
- **Lombok** - Code generation

## Key Dependencies

```xml
<epsilon-runtime-version>2.8.0.20250820_074004_d018a661_develop</epsilon-runtime-version>
<judo-tatami-core-version>1.1.4.20250819_223206_e27883c8_develop</judo-tatami-core-version>
<judo-meta-psm-version>1.3.0.20250919_125549_76114348_develop</judo-meta-psm-version>
<judo-meta-asm-version>1.1.4.20250820_074844_0c801b69_develop</judo-meta-asm-version>
```

## File Structure

```
judo-tatami-base/
├── judo-tatami-psm2asm/
│   └── src/main/epsilon/transformations/asm/
│       ├── psmToAsm.etl (main orchestrator)
│       ├── modules/*.etl (8 module transformations)
│       └── utils/*.eol (utility functions)
├── judo-tatami-asm2rdbms/
│   └── src/main/epsilon/transformations/
├── judo-tatami-rdbms2liquibase/
│   └── src/main/epsilon/transformations/
├── judo-tatami-psm2measure/
│   └── src/main/epsilon/transformations/
├── judo-tatami-asm2keycloak/
│   └── src/main/epsilon/transformations/
└── judo-tatami-*-validation/
    └── src/main/java/ (Java WorkClass pattern)
```

## Build Commands

```bash
# Standard build
mvn clean install

# Run tests only
mvn clean test

# Skip tests
mvn clean install -DskipTests
```

## Conventions

### ETL File Organization

- Main transformation file: `<source>To<Target>.etl`
- Module files: `modules/<domain>.etl`
- Utility files: `utils/<utility>.eol`

### Test Organization

- Unit tests: `src/test/java/.../*Test.java`
- Work tests: `src/test/java/.../*WorkTest.java`
- Integration tests: `osgi-itest/`

### Package Naming

- Transformation: `hu.blackbelt.judo.tatami.<source>2<target>`
- Validation: `hu.blackbelt.judo.tatami.<model>.validation`
