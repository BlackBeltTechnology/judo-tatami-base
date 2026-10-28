# ETL to Zeta Migration Documentation

This documentation provides comprehensive guidance for migrating transformation logic from Epsilon ETL (Epsilon Transformation Language) to Zeta (Java-based transformation framework).

## Overview

The judo-tatami-base project implements dual transformation engines for all transformation modules. Both engines produce functionally equivalent output, allowing seamless switching between ETL and Zeta implementations.

| Engine | Language | Execution | Use Case |
|--------|----------|-----------|----------|
| ETL | Epsilon ETL (.etl files) | Interpreted | Rapid prototyping, readable specifications |
| Zeta | Java with annotations | Compiled | Type safety, IDE support, performance |

## Documentation Index

### Core Guides

| Document | Description |
|----------|-------------|
| [ETL vs Zeta Comparison](etl-zeta-comparison.md) | Side-by-side syntax comparison, pattern mapping, feature equivalence |
| [Migration Guide](etl-to-zeta-migration.md) | Step-by-step migration process, common pitfalls, best practices |
| [Dual-Engine Testing](dual-engine-testing.md) | Testing framework, ModelComparator usage, performance testing |

### Module Documentation

For module-specific ETL-to-Zeta rule mappings, see the transformation documentation:

| Module | Documentation |
|--------|---------------|
| PSM to ASM | [psm2asm.md](../transformations/psm2asm.md) |
| ASM to RDBMS | [asm2rdbms.md](../transformations/asm2rdbms.md) |
| RDBMS to Liquibase | [rdbms2liquibase.md](../transformations/rdbms2liquibase.md) |
| PSM to Measure | [psm2measure.md](../transformations/psm2measure.md) |
| ASM to Keycloak | [asm2keycloak.md](../transformations/asm2keycloak.md) |

## Quick Start

### For New Projects

1. Read the [ETL vs Zeta Comparison](etl-zeta-comparison.md) to understand the syntax differences
2. Follow the [Migration Guide](etl-to-zeta-migration.md) for step-by-step instructions
3. Set up [Dual-Engine Testing](dual-engine-testing.md) to verify equivalence

### For Existing Projects

If you have existing ETL transformations and want to add Zeta implementations:

```
src/main/java/.../<module>/zeta/
├── <Module>ZetaTransformation.java   # Main transformation class
├── <Module>Helper.java               # Utility methods (optional)
├── <Module>RuleNames.java            # Rule name constants
└── rules/                            # Rule classes by domain
    ├── NamespaceRules.java
    ├── TypeRules.java
    └── ...
```

## Key Concepts

### Transformation Registry

Zeta uses `TransformationRegistry` to collect and organize rules:

```java
TransformationRegistry registry = new TransformationRegistry();
registry.register(NamespaceRules.class);
registry.register(TypeRules.class);
registry.register(DataRules.class);
```

### Rule Annotations

Rules are defined using annotations:

```java
@TransformRule(name = "CreateEntityClass")
@Greedy
@Transform(type = EntityType.class)
@To(type = EClass.class)
public TransformFunction<EntityType, EClass> createEntityClass() {
    return (source, ctx) -> {
        EClass target = ctx.createTarget(EClass.class);
        // transformation logic
        return target;
    };
}
```

### Equivalent Resolution

Access transformed elements via context:

```java
// Get equivalent of source element
EPackage pkg = ctx.equivalent(source.eContainer(), EPackage.class);

// Get named equivalent (from multi-output rule)
EClass refClass = ctx.equivalent(entity, EClass.class, "Reference");
```

## Related Resources

- [Transformation Rules Documentation](../transformations/README.md)
- [Zeta Framework Documentation](https://github.com/BlackBeltTechnology/judo-zeta)
- [Epsilon ETL Documentation](https://eclipse.dev/epsilon/doc/etl/)
