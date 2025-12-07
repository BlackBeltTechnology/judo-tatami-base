# Judo Tatami Base - Project Documentation

## Project Overview

**Repository:** BlackBeltTechnology/judo-tatami-base  
**License:** Eclipse Public License 2.0 (EPL-2.0)  
**Java Version:** 21  
**Build System:** Maven 3.9.4+ with OSGi bundles

This project contains the model transformation pipeline for the JUDO platform. It transforms source models (PSM - Platform Specific Model) into target models (ASM, RDBMS, Liquibase, Keycloak, Measure) using Epsilon ETL (Epsilon Transformation Language).

## Directory Structure

```
judo-tatami-base/
├── judo-tatami-psm2asm/          # PSM to ASM transformation
├── judo-tatami-psm2measure/      # PSM to Measure transformation
├── judo-tatami-asm2rdbms/        # ASM to RDBMS transformation
├── judo-tatami-rdbms2liquibase/  # RDBMS to Liquibase transformation
├── judo-tatami-asm2expression/   # ASM to Expression transformation
├── judo-tatami-asm2keycloak/     # ASM to Keycloak transformation
├── judo-tatami-psm-validation/   # PSM model validation
├── judo-tatami-asm-validation/   # ASM model validation
├── judo-tatami-expression-asm-validation/  # Expression on ASM validation
├── judo-tatami-expression-psm-validation/  # Expression on PSM validation
├── osgi-itest/                   # OSGi integration tests
├── p2/                           # Eclipse P2 repository
└── openspec/                     # OpenSpec change management
```

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

## Transformation Modules

| Module | Source | Target | ETL Files | Description |
|--------|--------|--------|-----------|-------------|
| judo-tatami-psm2asm | PSM | ASM | 9 | Main transformation from platform model to abstract semantic model |
| judo-tatami-psm2measure | PSM | Measure | 3 | Extract measurement units and definitions |
| judo-tatami-asm2rdbms | ASM | RDBMS | 8 | Generate relational database schema |
| judo-tatami-rdbms2liquibase | RDBMS | Liquibase | 12 | Generate database migration scripts |
| judo-tatami-asm2expression | ASM | Expression | 0 | Build expression model (Java-based) |
| judo-tatami-asm2keycloak | ASM | Keycloak | 3 | Generate Keycloak authentication config |

## ETL File Locations

Each transformation module follows this structure:
```
judo-tatami-<source>2<target>/
├── src/main/epsilon/
│   └── transformations/
│       ├── <source>To<Target>.etl   # Main transformation file
│       ├── modules/                  # Domain-specific transformations
│       │   ├── <domain1>.etl
│       │   └── <domain2>.etl
│       └── utils/                    # Utility functions
│           └── <utility>.eol
└── src/test/java/
    └── hu/blackbelt/judo/tatami/<source>2<target>/
        ├── <Source>2<Target>Test.java
        └── <Source>2<Target>WorkTest.java
```

## Technology Stack

### Core Technologies
- **Epsilon Runtime** 2.8.0 - ETL/EOL model transformation engine
- **Eclipse Modeling Framework (EMF)** - Metamodel foundation
- **Apache Felix** 5.1.8 - OSGi bundle plugin

### Testing
- **JUnit 5** - Test framework
- **Hamcrest** - Assertion library
- **Mockito** - Mocking framework

### Build & Quality
- **Maven 3.9.4+** with wrapper
- **JaCoCo** 0.8.12 - Code coverage
- **Lombok** 1.18.34 - Annotation processing

## Key Dependencies

```xml
<epsilon-runtime-version>2.8.0.20250820_074004_d018a661_develop</epsilon-runtime-version>
<judo-tatami-core-version>1.1.4.20250819_223206_e27883c8_develop</judo-tatami-core-version>
<judo-meta-psm-version>1.3.0.20250919_125549_76114348_develop</judo-meta-psm-version>
<judo-meta-asm-version>1.1.4.20250820_074844_0c801b69_develop</judo-meta-asm-version>
<judo-meta-rdbms-version>1.0.2.20250820_074908_e92e146c_develop</judo-meta-rdbms-version>
<judo-meta-liquibase-version>1.0.2.20250820_074950_9d7b02ff_develop</judo-meta-liquibase-version>
```

## Build Commands

```bash
# Standard build
mvn clean install
# or with wrapper
./mvnw clean install

# Run tests only
mvn clean test

# Skip tests
mvn clean install -DskipTests

# Build with specific profile
mvn clean install -Pmodules
```

## Test Files

### PSM2ASM Tests (11 files)
- `Psm2AsmTest.java` - Main transformation test
- `Psm2AsmWorkTest.java` - Work class test
- `Psm2AsmDataTest.java` - Data transformation test
- `Psm2AsmTypeTest.java` - Type transformation test
- `Psm2AsmDerivedTest.java` - Derived attribute test
- `Psm2AsmNamespaceTest.java` - Namespace test
- `Psm2AsmInheritanceTest.java` - Inheritance test
- `Psm2AsmAccessPointTest.java` - Access point test
- `Psm2AsmServiceTest.java` - Service test
- `OperationTest.java` - Operation test
- `AccessPointTest.java` - Actor/access point test

### Other Module Tests
- `Psm2MeasureTest.java`, `Psm2MeasureWorkTest.java`
- `Asm2RdbmsRelationMappingTest.java` and related tests
- `Rdbms2LiquibaseTest.java`, `Rdbms2LiquibaseContentTest.java`
- `Asm2KeycloakTest.java`, `Asm2KeycloakWorkTest.java`
- `Asm2ExpressionTest.java`, `Asm2ExpressionWorkTest.java`

## Code Patterns

### Transformation Execution

```java
// Execute PSM to ASM transformation
Psm2AsmTransformationTrace trace = executePsm2AsmTransformation(
    psm2AsmParameter()
        .psmModel(psmModel)
        .asmModel(asmModel)
);

// Access transformation trace
Map<EObject, List<EObject>> resolvedTrace = trace.getTransformationTrace();
```

### Work Class Pattern

```java
public class Psm2AsmWork {
    private final PsmModel psmModel;
    private final AsmModel asmModel;
    
    public void execute() {
        // Execute transformation
    }
}
```

## Related Projects

- **judo-zeta** (`/Users/robson/Project/judo-ng/runtime/judo-zeta`) - Zeta validation and transformation framework
- **judo-ng/models** (`/Users/robson/Project/judo-ng/models`) - All metamodel definitions (PSM, ASM, RDBMS, etc.)
- **judo-tatami-jsl** - JSL to PSM workflow using these transformations
- **judo-community** - Parent aggregator project

## OpenSpec Usage

This project uses OpenSpec for change management. See `openspec/AGENTS.md` for:
- Creating proposals
- Change workflow
- Spec format conventions

## Important Notes

1. **ETL files are the source of truth** for transformation logic
2. **Tests use the Northwind demo model** from judo-meta-psm
3. **OSGi compatibility** is maintained through Felix bundle plugin
4. **Transformation traces** allow mapping between source and target elements
5. **Validation modules** use Java WorkClass pattern (not EVL)
