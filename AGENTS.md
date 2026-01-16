<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

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

--

## ⚠️ CRITICAL: Before Implementing Fixes - Check TRANSFORMATION_PATTERNS.md

**ALWAYS read `TRANSFORMATION_PATTERNS.md` before attempting to fix transformation issues.**

This file documents:
- **Patterns that DON'T work** - avoid retrying failed approaches
- **Patterns that DO work** - proven solutions
- **Regression risks** - what can break when making changes
- **Pattern clusters** - similar transformation types grouped together

### Development Workflow for Transformation Fixes

1. **Check TRANSFORMATION_PATTERNS.md first** - see if the problem/solution is documented
2. **Identify similar pattern cluster** - group related transformations before fixing
3. **Test on ONE case first** - pick ONE transformation from the cluster to test the fix
4. **Run targeted test** - e.g., `mvn test -Dtest=Psm2AsmServiceTest#testBoundOperation`
5. **Verify success** - check element counts, annotation counts, no new errors
6. **Apply to rest of cluster** - only after confirming the fix works on one case
7. **Run full test suite** - verify no regressions: `mvn test`
8. **Document results** - update TRANSFORMATION_PATTERNS.md with findings (success OR failure)
9. **DO NOT commit automatically** - wait for user to review and decide on commit

### Testing Strategy (IMPORTANT)

**Two-phase testing approach:**

**Phase 1: Simple JUnit Tests (Quick Feedback)**
```bash
# Fast iteration during development
mvn test -Dtest=Psm2AsmServiceTest#testBoundOperation
mvn test -Dtest=Psm2AsmServiceTest
mvn test -pl judo-tatami-psm2asm
```
- Use for quick feedback during development
- Checks basic functionality and element counts
- Fast execution, good for iterating on fixes

**Phase 2: External Model with Strict Comparison (Thorough Validation)**
```bash
# Strict comparison with ETL baseline
mvn test -Dtest=Psm2AsmExternalModelTest -Pperformance
```
- Use external real-world model (RackInspect)
- Enables strict comparison between ETL and ZETA output
- Detects subtle differences that simple tests miss
- **MUST pass before considering fix complete**

**What strict comparison catches:**
- Elements created with wrong IDs
- Elements in wrong containers
- Missing elements that simple count checks miss
- Missing annotations
- Discriminator mismatches

**Testing workflow:**
```
1. Make fix
2. Run simple JUnit test (Phase 1) → iterate until passing
3. Run strict External Model test (Phase 2) → verify models match
4. If Phase 2 fails → analyze differences → fix → repeat
5. Both phases pass → document in TRANSFORMATION_PATTERNS.md
6. WAIT for user to decide on commit (DO NOT commit automatically)
```

### Pattern Group Fix Application (CRITICAL WORKFLOW)

**DO NOT commit automatically - always wait for user to review and decide on commit.**

**When fixing transformations in a pattern group, follow this strict order:**

```
Step 1: Fix ONE transformation only
        │
        ▼
Step 2: Test with External Model (strict compare)
        │
        ├── FAIL → Fix issues → repeat Step 2
        │
        ▼ PASS
Step 3: Apply same fix to OTHER transformations in pattern group
        │
        ▼
Step 4: Test with External Model again (full strict compare)
        │
        ├── ALL PASS → Document solution → WAIT for user to decide on commit
        │
        ▼ SOME FAIL
Step 5: Analyze failing transformations
        │
        ├── Same root cause? → Fix and repeat Step 4
        │
        ▼ Different behavior detected
Step 6: SPLIT pattern group
        │
        ├── Create new pattern group for failing transformations
        ├── Document behavior difference
        ▼
Step 7: Repeat process for new pattern group (back to Step 1)
```

**Key rules:**
1. **NEVER apply fix to all transformations before testing one** - verify solution works first
2. **ALWAYS use External Model with strict compare** - simple tests are not enough
3. **When some transformations fail after group-wide fix:**
   - Don't force the same solution on all
   - Analyze WHY they fail differently
   - Split into separate pattern groups if behavior differs
4. **Document every split** - captures incremental understanding of the codebase

**Example scenario:**
```
Pattern Group: "TransferObjectType Rules" (10 rules)

1. Fix rule #1 (CreateMappedTransferObjectTypeClass)
2. Test External Model → PASS
3. Apply fix to rules #2-#10
4. Test External Model → rules #2-#7 PASS, rules #8-#10 FAIL
5. Analyze: rules #8-#10 use different annotation pattern
6. Split:
   - Group A: "TransferObjectType Rules - Standard" (#1-#7) → fix works
   - Group B: "TransferObjectType Rules - Custom Annotation" (#8-#10) → needs different fix
7. Repeat process for Group B
```

### Problem Tracking Rule (CRITICAL)

**Track ALL problems persistently - NEVER remove documented problems even when "solved".**

Why this matters:
- Solutions can bring back previously solved problems
- Some problems have underlying architectural paradoxes
- Tracking history helps identify root causes
- Prevents circular debugging (solving A breaks B, fixing B breaks A)

**Problem tracking workflow:**
1. **Document every problem** encountered during transformation fixes
2. **Group similar problems** - same symptoms often have same root cause
3. **Track current state** - mark as ACTIVE, RESOLVED, or RECURRING
4. **Never delete** - resolved problems may resurface with new changes
5. **Analyze paradoxes** - when fixing A breaks B and vice versa, document the architectural conflict

**Problem Group Structure:**
```
## Problem Group: [Name]
Status: ACTIVE | RESOLVED | RECURRING
Related Rules: [list of affected rules]
Symptoms: [what you observe]
Root Cause: [why it happens]
Architectural Conflict: [if applicable - describe the paradox]
Solutions Tried: [list with outcomes]
Current Resolution: [what's working now, if any]
```

**Detecting Architectural Paradoxes:**
- Same fix solves problem A but causes problem B
- Two requirements are mutually exclusive
- Circular dependencies between transformation rules
- When detected: STOP and document the paradox before attempting more fixes

---

### ETL vs ZETA Difference Tracking (CRITICAL)

**Always compare ZETA implementation with original ETL to identify framework differences.**

Why this matters:
- ZETA should produce identical output to ETL
- Differences indicate either implementation bugs OR framework limitations
- Post-processing functions not in ETL are RED FLAGS - they compensate for ZETA limitations
- Proper tracking enables Zeta framework improvements or documented workarounds

**What to track:**
1. **Post-process functions** - Any cleanup/fix code after transformation that ETL doesn't need
2. **Workarounds** - Code that exists only because ZETA behaves differently than ETL
3. **Missing features** - ETL capabilities not available in ZETA
4. **Behavioral differences** - Same rule produces different results

**When you find a difference:**
1. Document in TRANSFORMATION_PATTERNS.md under "ETL vs ZETA Differences"
2. Reference the specific ETL file and line
3. Describe the expected ETL behavior
4. Describe the actual ZETA behavior
5. Note if workaround exists or if framework fix needed

**Post-Process Function Red Flags:**
```java
// RED FLAG: This function exists because ZETA creates different output than ETL
private void fixNullETypes(AsmModel asmModel) { ... }

// RED FLAG: This function exists because ZETA doesn't set references correctly
private void setEOpposites(AsmModel asmModel) { ... }

// RED FLAG: This function exists because ZETA containment differs from ETL
private void addRootPackages(AsmModel asmModel) { ... }
```

Each post-process function should be documented with:
- Why it's needed (what ZETA does wrong)
- What ETL does instead (correct behavior)
- Whether it's a ZETA framework issue or implementation issue
- Proposed fix for ZETA framework (if applicable)

---

### Pattern Splitting Rule

**When items in a cluster behave differently, SPLIT the cluster into new patterns:**

- If a fix works for items A, B but NOT for C, D → split into two separate patterns
- Different behaviors indicate different underlying mechanics
- Splitting helps incremental understanding - each pattern becomes simpler
- Document WHY they behave differently (containment vs reference, annotation pattern, etc.)

Example:
```
Original Cluster: "Operation Rules" (10 rules)
  ↓ Fix applied, 6 rules fixed, 4 still have issues
  ↓ SPLIT into:

Pattern 1A: "Operation Rules - Bound Operations" (6 rules)
  - Operations bound to entity methods
  - @Greedy with standard annotation pattern

Pattern 1B: "Operation Rules - Unbound Operations" (4 rules)
  - Standalone operations without entity binding
  - Different annotation requirements
```

### Key Files for Transformation Debugging

```
TRANSFORMATION_PATTERNS.md              # Patterns documentation (THIS IS CRITICAL)
BOTTLENECK_ANALYSIS.md                  # Performance bottleneck analysis
PERFORMANCE_REPORT.md                   # Benchmark results

judo-tatami-psm2asm/
├── src/main/java/.../zeta/
│   ├── Psm2AsmRuleNames.java           # All rule name constants
│   ├── Psm2AsmZetaTransformation.java  # Main transformation + cleanup logic
│   └── rules/
│       ├── NamespaceRules.java         # Package transformations
│       ├── DataRules.java              # Data type transformations
│       ├── TransferObjectRules.java    # Transfer object transformations
│       ├── OperationRules.java         # Operation transformations
│       ├── DerivedRules.java           # Derived attribute transformations
│       └── StaticRules.java            # Static data transformations
├── src/test/java/.../
│   ├── Psm2AsmServiceTest.java         # Service/operation test
│   ├── Psm2AsmDualTransformationTest.java  # ETL vs ZETA comparison test
│   └── perf/
│       └── Psm2AsmExternalModelTest.java   # External model benchmark test

judo-zeta/transformation-core/
├── src/main/java/.../core/
│   ├── ElementResolutionCache.java     # Transformation cache (performance critical)
│   ├── TransformationContext.java      # Context with equivalent(), createTarget()
│   └── TransformationExecutor.java     # Main executor with parallel/sequential mode
```

---

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

---

## ETL-Zeta Model Comparison

The project includes infrastructure for comparing ETL and Zeta transformation outputs. This is useful for validating that both transformation engines produce equivalent models.

### ModelComparator

Each transformation module includes a `ModelComparator` utility class that provides:
- **Order-independent comparison** - Collections matched by identifier, not position
- **Typed difference reporting** - `MissingElement`, `ExtraElement`, `ValueMismatch`, `TypeMismatch`
- **Comparison modes**:
  - `STRICT` - All attributes and references must match exactly
  - `STRUCTURAL` - Element structure must match, annotation differences tolerated (default)
  - `LENIENT` - Major structural elements must match, minor differences allowed

### Configuration via System Properties

```bash
# Enable/disable comparison (default: true)
-Djudo.test.comparison.enabled=true

# Comparison mode (default: STRUCTURAL)
-Djudo.test.comparison.mode=STRICT|STRUCTURAL|LENIENT

# Maximum differences to report (default: 50)
-Djudo.test.comparison.maxDifferences=100

# Output file for diff report (optional)
-Djudo.test.comparison.reportFile=target/comparison-report.txt
```

### Usage in Tests

```java
// Assert models are equivalent (uses configured mode)
ModelComparator.assertEquivalent(expectedModel, actualModel);

// Compare with specific mode
ModelComparator.assertEquivalent(expectedModel, actualModel, ComparisonMode.STRICT);

// Get detailed comparison result
ComparisonResult result = ModelComparator.compare(model1, model2);
if (!result.isEquivalent()) {
    System.out.println(result.getSummary());
    System.out.println(result.getDetailedReport());
}

// Filter differences by type
List<MissingElement> missing = result.getDifferencesOfType(MissingElement.class);
```

### Running Comparison Tests

```bash
# Run with default STRUCTURAL mode
mvn test -Dtest=Psm2AsmDualTransformationTest

# Run with STRICT mode
mvn test -Dtest=Psm2AsmDualTransformationTest -Djudo.test.comparison.mode=STRICT

# Disable comparison (skip comparison tests)
mvn test -Djudo.test.comparison.enabled=false
```

## Zeta Transformation Implementation

The project includes a high-performance Zeta-based transformation engine alongside the original ETL engine. Zeta transformations are implemented in Java and provide significant performance improvements (30-45x faster than ETL).

### Architecture

Each transformation module that supports Zeta has this structure:
```
judo-tatami-<source>2<target>/
├── src/main/java/hu/blackbelt/judo/tatami/<source>2<target>/
│   ├── <Source>2<Target>Work.java           # Main work class (supports both engines)
│   └── zeta/
│       ├── <Source>2<Target>ZetaTransformation.java  # Main Zeta transformation
│       ├── <Source>2<Target>RuleNames.java           # Rule name constants
│       ├── <Source>2<Target>Helper.java              # Helper utilities
│       └── rules/                                     # Rule implementations
│           ├── NamespaceRules.java
│           ├── DataRules.java
│           ├── TransferObjectRules.java
│           ├── OperationRules.java
│           ├── DerivedRules.java
│           └── StaticRules.java
```

### Rule Implementation Pattern

Zeta rules use annotations to define transformations:

```java
@TransformRule(name = "CreateMappedTransferObject", description = "Transform MappedTransferObjectType to EClass")
@Transform(type = MappedTransferObjectType.class)
@To(type = EClass.class)
@Greedy
public TransformFunction<MappedTransferObjectType, EClass> createMappedTransferObject() {
    return (s, ctx) -> {
        // Guard condition - return null to skip
        if (!guardCondition(s)) {
            return null;
        }
        
        // Create target element
        EClass t = ctx.createTarget(EClass.class);
        setId(t, "(psm/" + getId(s) + ")/MappedTransferObject");
        t.setName(s.getName());
        
        // Add to container
        EPackage pkg = ctx.equivalent(s.eContainer(), EPackage.class);
        pkg.getEClassifiers().add(t);
        
        // Add annotations inline (consolidate related rules)
        EAnnotation annotation = createAnnotation(
                "(psm/" + getId(s) + ")/TypeAnnotation",
                getAnnotationUri("transferObjectType"));
        addAnnotationDetail(annotation, "value", "true");
        t.getEAnnotations().add(annotation);
        
        return t;
    };
}
```

### Key Patterns

1. **Guards**: Return `null` from the transform function to skip elements that don't match the guard condition
2. **Inline consolidation**: Combine multiple related ETL rules into a single Zeta rule for efficiency
3. **ID convention**: Use `(psm/{sourceId})/{RuleName}` pattern for traceability
4. **Helper methods**: Use static imports from `*Helper.java` for common operations

### Common Issues and Solutions

**See `TRANSFORMATION_PATTERNS.md` for documented transformation issues and solutions.**

The TRANSFORMATION_PATTERNS.md file contains:
- Known issues with symptoms and root causes
- Proven solutions and patterns that work
- Patterns that DON'T work (to avoid retrying)
- Pattern clusters for related transformations

**Always check TRANSFORMATION_PATTERNS.md before attempting to fix transformation issues.**

### Performance Testing

Performance tests use the RackInspect real-world model. See the **Testing Strategy** section above for the two-phase testing approach.

```bash
# Run external model benchmark (strict ETL vs ZETA comparison)
mvn test -Dtest=Psm2AsmExternalModelTest -Pperformance

# Run all performance tests
mvn test -Pperformance -Dgroups=performance
```

Expected results:
- **Psm2Asm**: ~8-10x faster than ETL (with sequential mode optimization)
- **Rdbms2Liquibase**: ~40-50x faster than ETL

### Adding New Zeta Rules

Follow the **Development Workflow for Transformation Fixes** in the CRITICAL section above:

1. Add rule name constant to `*RuleNames.java`
2. Implement rule in appropriate `*Rules.java` class
3. Register the rules class in transformation initialization
4. **Phase 1**: Run targeted JUnit test for quick feedback
5. **Phase 2**: Run `Psm2AsmExternalModelTest -Pperformance` for strict comparison
6. Document results in `TRANSFORMATION_PATTERNS.md`

## JVM Profiler Integration

The `judo-tatami-test-utils` module provides a JUnit 5 profiling extension using async-profiler for performance analysis of transformation tests.

### Quick Start

```java
import hu.blackbelt.judo.tatami.test.profiler.Profile;

@Profile
class Psm2AsmPerformanceTest {

    @Test
    void testLargeModelTransformation() {
        // This test will be automatically profiled
        Psm2AsmWork work = new Psm2AsmWork(largeModel);
        work.execute();
    }
}
```

### Configuration via System Properties

```bash
# Enable/disable profiling globally
-Djudo.test.profiler.enabled=true

# Output directory (default: target/profiler-output/)
-Djudo.test.profiler.outputPath=target/profiler-output/

# Output format: collapsed | flamegraph | jfr (default: collapsed)
-Djudo.test.profiler.format=collapsed

# Profiling event: cpu | wall | alloc | lock (default: cpu)
-Djudo.test.profiler.event=cpu

# Minimum test duration to trigger profiling (ms)
-Djudo.test.profiler.thresholdMs=100

# Sampling interval in nanoseconds (default: 1000000 = 1ms)
-Djudo.test.profiler.interval=1000000
```

### Running Profiled Tests

```bash
# Profile specific tests
mvn test -pl judo-tatami-psm2asm -Dtest=*PerformanceTest -Djudo.test.profiler.enabled=true

# Profile Zeta vs ETL comparison
mvn test -pl judo-tatami-psm2asm \
    -Dtest=Psm2AsmDualTransformationTest \
    -Djudo.test.profiler.enabled=true \
    -Djudo.test.profiler.thresholdMs=50
```

### Output Formats

| Format | Extension | Use Case |
|--------|-----------|----------|
| `collapsed` | `.txt` | LLM analysis (token-efficient) |
| `flamegraph` | `.svg` | Visual analysis |
| `jfr` | `.jfr` | JDK Mission Control |

### LLM Analysis Workflow

The collapsed format is optimized for external LLM tools:

```bash
# 1. Run profiled tests
mvn test -Dtest=Psm2AsmPerformanceTest -Djudo.test.profiler.enabled=true

# 2. Ask Claude Code/Cursor/Copilot to analyze:
# "Analyze target/profiler-output/Psm2AsmPerformanceTest_testLargeModel.txt
#  and suggest optimizations"
```

### Optional Integrated LLM Analyzer

For automated analysis during test runs (disabled by default):

```bash
# Enable with OpenRouter
mvn test -Dtest=*PerformanceTest \
    -Djudo.test.profiler.enabled=true \
    -Djudo.test.profiler.llm.enabled=true \
    -Djudo.test.profiler.llm.provider=openrouter

# Supported providers: openai, anthropic, openrouter, deepseek, minimax, groq, together, ollama
```

### Environment Prerequisites

**Linux:**
```bash
# Allow profiling (requires root)
echo 1 | sudo tee /proc/sys/kernel/perf_event_paranoid
```

**GitHub Actions:**
```yaml
- name: Configure perf_event
  run: echo 1 | sudo tee /proc/sys/kernel/perf_event_paranoid

- name: Run profiled tests
  run: mvn test -Djudo.test.profiler.enabled=true

- name: Upload profiles
  uses: actions/upload-artifact@v4
  with:
    name: profiler-output
    path: '**/target/profiler-output/'
```

### Full Specification

See [judo-tatami-test-utils/JVM_PROFILER_SPEC.md](judo-tatami-test-utils/JVM_PROFILER_SPEC.md) for complete documentation including:
- Multi-provider LLM support
- Native library bundling
- Advanced configuration options
- Token management strategies

## Important Notes

1. **Read the CRITICAL section first** - follow the transformation fix workflow before making changes
2. **Check TRANSFORMATION_PATTERNS.md** - see documented patterns before implementing fixes
3. **ETL files are the source of truth** for transformation logic - ZETA must produce identical output
4. **Two-phase testing is mandatory** - JUnit tests + External Model strict comparison
5. **DO NOT commit automatically** - always wait for user to review and decide
6. **Tests use the Northwind demo model** from judo-meta-psm for unit tests
7. **External model tests use RackInspect** for thorough validation
8. **Document all findings** in TRANSFORMATION_PATTERNS.md (successes AND failures)
9. **OSGi compatibility** is maintained through Felix bundle plugin
10. **JVM Profiler** outputs collapsed format optimized for LLM analysis
