# Transformation Rules Documentation

This directory contains documentation for all transformation rules in the judo-tatami-base project.

## Overview

The transformation pipeline converts Platform Specific Models (PSM) through a series of transformations to generate various target artifacts:

```
PSM (Platform Specific Model)
  ↓ (psm2asm)
ASM (Abstract Semantic Model)
  ├→ (asm2rdbms) → RDBMS Schema
  │   └→ (rdbms2liquibase) → Liquibase Changelog
  ├→ (asm2expression) → Expression Model
  └→ (asm2keycloak) → Keycloak Config
PSM
  └→ (psm2measure) → Measure Model
```

## Transformation Modules

| Module | Documentation | Description |
|--------|---------------|-------------|
| [PSM to ASM](psm2asm.md) | PSM → ASM | Main transformation from platform model to abstract semantic model |
| [PSM to Measure](psm2measure.md) | PSM → Measure | Extract measurement units and definitions |
| [ASM to RDBMS](asm2rdbms.md) | ASM → RDBMS | Generate relational database schema |
| [RDBMS to Liquibase](rdbms2liquibase.md) | RDBMS → Liquibase | Generate database migration scripts |
| [ASM to Keycloak](asm2keycloak.md) | ASM → Keycloak | Generate Keycloak authentication configuration |

## Dual Transformation Architecture

Each transformation module supports two execution modes:

1. **ETL Mode** - Uses Epsilon ETL (Epsilon Transformation Language) scripts
2. **Zeta Mode** - Uses Java-based Zeta framework transformations

Both modes produce functionally equivalent output and can be selected at runtime.

### ETL Implementation

ETL files are located in each module under:
```
src/main/epsilon/transformations/
├── <source>To<Target>.etl    # Main transformation
├── modules/                   # Domain-specific rules
│   ├── namespace.etl
│   ├── type.etl
│   └── ...
└── utils/                     # Utility functions
    └── *.eol
```

### Zeta Implementation

Zeta transformation classes are located in each module under:
```
src/main/java/hu/blackbelt/judo/tatami/<module>/zeta/
├── <Source>2<Target>RuleNames.java      # Rule name constants
├── <Source>2<Target>Transformation.java  # Main transformer
└── rules/                                # Domain-specific rules
    ├── NamespaceRules.java
    ├── TypeRules.java
    └── ...
```

## Rule Naming Convention

All transformation rules follow a consistent naming pattern:

- **ETL Rules**: PascalCase names in ETL files (e.g., `CreateEntityClass`)
- **Zeta Constants**: UPPER_SNAKE_CASE in Java constants (e.g., `CREATE_ENTITY_CLASS`)

Rule names are defined as constants in `*RuleNames.java` files to ensure consistency.

## Testing

Transformation tests use parameterized testing to verify both ETL and Zeta implementations:

```java
import hu.blackbelt.judo.tatami.core.TransformationMode;

@ParameterizedTest
@EnumSource(TransformationMode.class)
void testTransformation(TransformationMode mode) {
    AsmModel result;
    if (mode.isZeta()) {
        result = runZetaTransformation(source);
    } else {
        result = runEtlTransformation(source);
    }
    verifyResult(result);
}
```

A dual equivalence test ensures both engines produce identical output:

```java
@Test
void testDualEquivalence() {
    AsmModel etlResult = runEtlTransformation(source);
    AsmModel zetaResult = runZetaTransformation(source);
    ModelComparator.assertEquivalent(etlResult, zetaResult);
}
```

## Performance

Performance benchmarks compare execution times:

| Transformation | ETL Time | Zeta Time | Model Size |
|---------------|----------|-----------|------------|
| PSM to ASM | TBD | TBD | 10,000 elements |
| ASM to RDBMS | TBD | TBD | 10,000 elements |
| RDBMS to Liquibase | TBD | TBD | 10,000 elements |

## Migration Documentation

For guidance on migrating ETL transformations to Zeta, see the [Migration Documentation](../migration/README.md):

| Document | Description |
|----------|-------------|
| [ETL vs Zeta Comparison](../migration/etl-zeta-comparison.md) | Side-by-side syntax comparison, pattern mapping |
| [Migration Guide](../migration/etl-to-zeta-migration.md) | Step-by-step migration process, common pitfalls |
| [Dual-Engine Testing](../migration/dual-engine-testing.md) | Testing framework, ModelComparator usage |

Each module documentation also includes an "ETL to Zeta Rule Mapping" section with:
- File structure mapping (ETL files to Zeta classes)
- Key rule mapping (ETL rules to Zeta methods)
- Implementation notes specific to that module
