# ZETA Transformation Patterns Documentation

This document captures learnings about ZETA transformation patterns - what works, what doesn't, and what can cause regressions. **Always check this document before attempting fixes.**

---

## Table of Contents
- [Pattern Management Guidelines](#pattern-management-guidelines)
- [Problem Registry](#problem-registry)
- [ETL vs ZETA Framework Differences](#etl-vs-zeta-framework-differences)
- [ETL vs ZETA Differences (Project-Specific)](#etl-vs-zeta-differences-project-specific)
- [Pattern Clusters](#pattern-clusters)
- [Failed Approaches (DON'T RETRY)](#failed-approaches-dont-retry)
- [Successful Patterns](#successful-patterns)
- [Regression Risks](#regression-risks)
- [Orphan Element Analysis](#orphan-element-analysis)
- [Test Commands](#test-commands)

---

## Pattern Management Guidelines

### When to Split Patterns

**If items in a cluster behave differently during fix iterations, SPLIT into separate patterns.**

Signs that a cluster needs splitting:
- Fix works for some items but not others
- Some items have different error types
- Some items have additional dependencies
- Some items use containment, others use reference

**Benefits of splitting:**
- Each pattern becomes simpler to understand
- Fixes can be targeted to specific behaviors
- Reduces risk of applying wrong fix to wrong pattern
- Builds incremental understanding of the codebase

### Pattern Evolution Example

```
Iteration 1: Identify "TransferObject Rules" cluster (10 rules)
Iteration 2: Apply annotation fix
Iteration 3: Observe: 6 rules fixed, 4 still have issues
Iteration 4: SPLIT cluster:

  Cluster 1A: "Mapped TransferObjects" (6 rules)
    - Direct mapping to entity types
    - Fix works

  Cluster 1B: "Unmapped TransferObjects" (4 rules)
    - Generated types without direct mapping
    - Need different approach

Iteration 5: Document both patterns with their specific solutions
```

### Documenting Split Patterns

When splitting, always document:
1. **Original cluster name** - for traceability
2. **Split reason** - what behavior difference was observed
3. **New pattern names** - descriptive of the specific behavior
4. **Rules in each** - list which rules belong to which pattern
5. **Applicable fixes** - what works for each new pattern

---

## Problem Registry

**IMPORTANT: Never delete entries from this registry. Problems may resurface.**

### Template for New Problem Groups

```markdown
### Problem Group: [Descriptive Name]

**Status:** ACTIVE | RESOLVED | RECURRING

**Related Rules:**
- [Rule 1]
- [Rule 2]

**Symptoms:**
- [Observable behavior 1]
- [Observable behavior 2]

**Root Cause:**
[Explanation of why the problem occurs]

**Architectural Conflict:** [If applicable]
```
[Describe any paradoxes or mutually exclusive requirements]
```

**Solutions Tried:**
| Solution | Outcome | Side Effects |
|----------|---------|--------------|
| [Solution 1] | [Result] | [Any regressions] |

**Current Resolution:**
[What's working now, or "Not resolved"]
```

---

## ETL vs ZETA Framework Differences

**These are GENERAL framework differences that apply to ALL ZETA transformations.**

### Key Framework Difference: @lazy vs @Lazy @Greedy

**ETL Framework:**
- `@lazy` = Rule is registered AND only executes when called via `equivalent()`
- Rules are automatically registered during transformation initialization
- `equivalent()` can always find @lazy rules

**ZETA Framework:**
- `@Lazy` = Rule uses cached/lazy evaluation, but may NOT be registered for all sources
- `@Greedy` = Rule is registered for all matching sources
- `@Lazy @Greedy` = Rule is registered AND uses lazy evaluation (equivalent to ETL @lazy)

**Implication:**
- ETL @lazy rules → ZETA needs @Lazy @Greedy to ensure equivalent() can find them
- ETL @lazy @greedy rules → ZETA can use @Lazy @Greedy or just @Lazy depending on orphan concerns

### createTarget() Behavior Difference

**ETL Behavior:**
- Elements created by rules are automatically placed in correct containment
- No orphan cleanup needed

**ZETA Behavior:**
- `ctx.createTarget()` adds ALL elements to Resource.contents
- Even elements that should only exist via containment end up as root elements
- Requires post-processing to remove elements that have containers

**Root Cause:**
```
ETL: rule creates element → element exists only when referenced/contained
ZETA: ctx.createTarget() → element added to Resource.contents immediately
                        → containment later moves element (but it was already added)
```

**Workaround Options:**
- Option A: Post-process removes elements where `element.eContainer() != null`
- Option B: Use `@Detached` annotation for elements that are only used via containment
- Option C: Framework fix: `ctx.createTarget()` should NOT add to Resource.contents by default

### equivalent() Return Value Difference

**ETL Behavior:**
- `equivalent()` always returns result or rule creates it
- Caller can assume non-null return

**ZETA Behavior:**
- `ctx.equivalent()` may return null if guard fails or rule not triggered
- Caller must check null before using result

**Mitigation:** Always check for null after `ctx.equivalent()` calls

### @Detached Annotation

**Purpose:** Element is NOT added to Resource.contents when created. Caller must add to proper container.

**When to use:** Elements that are ONLY accessed via containment relationships (never referenced)

**Example:**
```java
@TransformRule(name = "CreateSomeElement")
@Lazy
@Detached  // Safe because element is only used via containment
public TransformFunction<Source, Target> createSomeElement() {
    return (source, ctx) -> {
        Target target = ctx.createTarget(Target.class);
        // ...
        return target;  // Caller adds to container (containment)
    };
}
```

---

## ETL vs ZETA Differences (Project-Specific)

**This section documents behavioral differences specific to this project's transformations.**
**Post-process functions are RED FLAGS indicating ZETA framework limitations.**

### Template for New ETL vs ZETA Differences

```markdown
#### Difference N: [Function Name or Behavior]

**Location:** `[File.java]` lines [X-Y]

**ETL Behavior:**
- [What ETL does]

**ZETA Behavior:**
- [What ZETA does differently]

**Root Cause Analysis:**
```
[Explanation of why behaviors differ]
```

**Workaround:** [Current workaround if any]

**Framework Fix Needed:**
- [Proposed fix for ZETA framework]

**Status:** WORKAROUND IN PLACE | NEEDS INVESTIGATION | FRAMEWORK FIX SUBMITTED
```

### All Transformations: Parallel Execution Disabled

**Location:** All `*ZetaTransformation.java` files

**ETL Behavior:**
- Framework-managed parallel execution

**ZETA Behavior:**
- All transformations use `parallel(false)` due to EMF thread-safety issues
- Parallel execution causes NPE when accessing model elements on large models
- Race conditions in changeSet creation cause non-deterministic results

**Status:** PERMANENT (requires EMF thread-safe collections)

---

### PSM2ASM Post-Process Functions

**Location:** `Psm2AsmZetaTransformation.java` lines 243-342

These functions exist ONLY because ZETA behaves differently than ETL:

#### Difference 1: addRootPackages() - Step 1

**ETL Behavior:**
- Packages are automatically added to model root during transformation

**ZETA Behavior:**
- Packages created by rules may not be added to model root
- Requires post-processing to add root packages
- Also applies pending XMI IDs recursively to all children

**Status:** WORKAROUND IN PLACE

---

#### Difference 2: setEOpposites() - Step 2

**ETL Behavior:**
- EReferences have their eOpposite set correctly during transformation

**ZETA Behavior:**
- AssociationEnd partners don't have eOpposite set automatically
- Requires post-processing to iterate all AssociationEnds and set EOpposite

**Status:** WORKAROUND IN PLACE

---

#### Difference 3: setTransferObjectRelationTypes() - Step 3

**ETL Behavior:**
- TransferObjectRelation target types are set during transformation

**ZETA Behavior:**
- Some EReferences for TransferObjectRelations have null eType
- Requires post-processing to set target types from PSM relation.target

**Status:** WORKAROUND IN PLACE

---

#### Difference 4: setReferenceClassInheritance() - Step 4

**ETL Behavior:**
- Reference class inheritance (EntityType__Reference) set during transformation

**ZETA Behavior:**
- Reference class super types not set automatically
- Requires post-processing to iterate EntityTypes with superTypes and set ESuperTypes

**Status:** WORKAROUND IN PLACE

---

#### Difference 5: enrichWithAnnotations() - Step 5

**ETL Behavior:**
- `asmUtils.enrichWithAnnotations()` called and produces correct annotation counts

**ZETA Behavior:**
- Same call but may produce different annotation counts if model state differs
- Debug logging added to compare model state before/after

**Status:** MONITORING

---

#### Difference 6: fixNullAttributeTypes() - Step 6

**ETL Behavior:**
- All EAttributes have their eType set correctly

**ZETA Behavior:**
- Extension transfer object types (_default_, _binding_) have null eType
- Cross-resource type references don't match transformed instances
- Requires post-processing to infer type from PSM source by name lookup

**Root Cause:**
```
ETL: Type references resolve correctly across resources
ZETA: ctx.equivalent() returns null for cross-resource type references
      because the dataType instance in PSM doesn't match the transformed type
```

**Status:** WORKAROUND IN PLACE

---

### ASM2RDBMS Post-Process Functions

**Location:** `Asm2RdbmsZetaTransformation.java` lines 266-290

#### Difference 7: addRootElements()

**ETL Behavior:**
- RdbmsModel automatically added to resource during transformation

**ZETA Behavior:**
- RdbmsModel not added to resource by rules
- Requires post-processing to add root RdbmsModel elements
- Also applies pending XMI IDs recursively

**Status:** WORKAROUND IN PLACE

---

#### Difference 8: applyNameMappings()

**ETL Behavior:**
- Name mappings applied during transformation

**ZETA Behavior:**
- Name mappings from Excel model applied in post-processing
- Iterates all RDBMS elements and matches by UUID

**Status:** WORKAROUND IN PLACE

---

### PSM2Measure Post-Process Functions

**Location:** `Psm2MeasureZetaTransformation.java` lines 178-223

#### Difference 9: Custom XMI ID Handling

**ETL Behavior:**
- XMI IDs generated automatically in ETL format

**ZETA Behavior:**
- Zeta's structured IDs don't match ETL format for measures
- Uses `context.setUseStructuredIds(false)` to disable auto-generation
- Custom XMI IDs stored in context attribute `customXmiIds`
- Post-processing applies custom IDs from the map

**Root Cause:**
```
ETL: IDs like "(psm/MeasureName)/Measure"
ZETA structured: Different format that doesn't match
Solution: Manual ID generation in rules, applied in post-process
```

**Status:** WORKAROUND IN PLACE

---

### RDBMS2Liquibase Differences

**Location:** `Rdbms2LiquibaseZetaTransformation.java` lines 79, 211-236

#### Difference 10: Thread-Safe ChangeSet Creation

**ETL Behavior:**
- ChangeSets created without thread-safety concerns

**ZETA Behavior:**
- Uses `ConcurrentHashMap` for changeSet cache
- Uses `synchronized` method for adding to changeLog
- Required because EMF ELists are not thread-safe

**Status:** WORKAROUND IN PLACE (even with parallel=false, kept for future)

---

## Pattern Clusters

### Template for Pattern Clusters

```markdown
### Cluster N: [Descriptive Name]
Similar transformation rules that create [element type]:

| File | Rules | Creates |
|------|-------|---------|
| `[File.java]` | `ruleName1`, `ruleName2` | `TargetType1`, `TargetType2` |

**Characteristics:**
- [Common behavior 1]
- [Common behavior 2]

**Applicable Fix:**
- [What works for this cluster]
```

---

## Failed Approaches (DON'T RETRY)

### Template for Failed Approaches

```markdown
### Attempt N: [Short Description]
**Date:** [Date]
**Problem:** [What problem was being solved]
**Approach:** [What was tried]
**Result:** FAILED

**Why it failed:**
- [Explanation]

**Files modified:**
- [List of files]

**Lesson:** [Key learning for future reference]
```

---

## Successful Patterns

### Pattern: @Detached for Contained-Only Elements
**When to use:** Elements that are ONLY accessed via containment relationships
**Example:** Elements added to parent containers by the caller

```java
@TransformRule(name = "CreateChildElement")
@Lazy
@Detached  // Safe because element is only used via containment
public TransformFunction<Source, ChildElement> createChildElement() {
    return (source, ctx) -> {
        ChildElement target = ctx.createTarget(ChildElement.class);
        // ...
        return target;  // Caller adds to parent.children (containment)
    };
}
```

### Pattern: Named Rule Lookup with Explicit Discriminator
**When to use:** When creating discriminated elements that need caching
**Best practice:** Always use non-null discriminator with `equivalentDiscriminated()`

```java
// GOOD: Explicit discriminator enables proper caching
String discriminatorId = source.getName() + "/(psm/" + getId(source) + ")/MyElement";
Target target = ctx.equivalentDiscriminated(source, Target.class, RULE_NAME, discriminatorId);

// AVOID: null discriminator uses non-discriminated cache, triggers @Lazy rules
Target target = ctx.equivalentDiscriminated(source, Target.class, RULE_NAME, null);
```

### Pattern: Inline Element Creation Fallback
**When to use:** When `ctx.equivalent()` may return null
**Best practice:** Check for null and create element inline if needed

```java
Target target = ctx.equivalent(source, Target.class);
if (target == null) {
    // Fallback: create inline
    target = ctx.createTarget(Target.class);
    target.setName(source.getName());
    // ... set other properties
}
```

---

## Regression Risks

### Risk 1: Removing @Greedy Breaks Element Creation
**Symptom:** Missing elements in output model
**Cause:** @Greedy rules auto-create elements for all matching sources; removing @Greedy means elements only created when explicitly requested
**Check:** Run comparison tests and verify element counts match expected

### Risk 2: Changing Containment Structure
**Symptom:** Null pointer exceptions or missing elements
**Cause:** Some code paths expect elements in Resource.contents (for reference lookup), others expect them contained
**Check:** Understand EMF containment vs reference semantics before making changes

### Risk 3: Discriminator Changes Affect Caching
**Symptom:** Duplicate elements or missing elements
**Cause:** Different discriminator values create different cached instances
**Check:** Ensure discriminator format matches ETL patterns exactly

### Risk 4: @Lazy Without @Greedy
**Symptom:** `ctx.equivalent()` returns null unexpectedly
**Cause:** @Lazy alone doesn't register the rule for all sources; equivalent() can't find unregistered rules
**Check:** Add @Greedy to @Lazy rules that need to be findable via equivalent()

---

## Orphan Element Analysis

### What Creates Orphan Elements

1. **@Lazy rules triggered via ctx.equivalent():**
   - Code calls `ctx.equivalent(source, RULE_NAME)`
   - @Lazy rule executes, calls `ctx.createTarget()`, adds to Resource.contents
   - If caller doesn't set as containment, element stays at root

2. **@Greedy rules creating orphan parent elements:**
   - Parent rule has @Greedy, runs for all matching sources
   - Parent calls `ctx.equivalent()` to get child
   - Child is set as parent.child (containment) - child moves to parent
   - But if parent itself is never contained, parent (with child inside) is orphaned at root

3. **Multiple code paths creating same logical element:**
   - One path uses `ctx.equivalentDiscriminated(..., discriminator1)`
   - Another path uses `ctx.equivalentDiscriminated(..., discriminator2)`
   - Creates multiple instances, only one gets properly contained

### Detecting Orphans

```java
// In post-processing or tests
Resource resource = model.getResourceSet().getResources().get(0);
int rootCount = resource.getContents().size();
if (rootCount > expectedRootCount) {
    for (EObject obj : resource.getContents()) {
        if (obj.eContainer() != null) {
            log.warn("[ORPHAN] {} has container but is in Resource.contents",
                     getPath(obj));
        }
    }
}
```

---

## Test Commands

### Phase 1: Simple JUnit Tests (Quick Feedback)

```bash
# Run specific test - fast iteration
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmServiceTest#testBoundOperation

# Run module tests
mvn test -pl judo-tatami-psm2asm
mvn test -pl judo-tatami-asm2rdbms
mvn test -pl judo-tatami-rdbms2liquibase
mvn test -pl judo-tatami-psm2measure
mvn test -pl judo-tatami-asm2keycloak

# Check for specific issues
mvn test -pl judo-tatami-psm2asm 2>&1 | grep -E "(ORPHAN|missing|Error)"
```

### Phase 2: External Model with Strict Comparison (REQUIRED)

```bash
# Strict comparison with ETL baseline using real-world model
# PSM2ASM
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest -Pperformance

# ASM2RDBMS
mvn test -pl judo-tatami-asm2rdbms -Dtest=Asm2RdbmsExternalModelTest -Pperformance

# RDBMS2Liquibase
mvn test -pl judo-tatami-rdbms2liquibase -Dtest=Rdbms2LiquibaseExternalModelTest -Pperformance

# PSM2Measure
mvn test -pl judo-tatami-psm2measure -Dtest=Psm2MeasureExternalModelTest -Pperformance

# ASM2Keycloak
mvn test -pl judo-tatami-asm2keycloak -Dtest=Asm2KeycloakExternalModelTest -Pperformance

# Run ALL external model tests
mvn test -Pperformance -Dtest="*ExternalModelTest"

# Check comparison output
mvn test -Dtest=Psm2AsmExternalModelTest -Pperformance 2>&1 | grep -E "(EQUIVALENT|difference|mismatch)"
```

**Why Phase 2 is critical:**
- Simple tests may pass but models can still differ
- External model tests catch subtle element differences
- Strict comparison ensures ZETA output matches ETL exactly
- Differences indicate:
  - Wrong discriminator values
  - Elements created by wrong rules
  - Containment hierarchy differences
  - Missing or extra elements

### Testing Workflow Summary

```
Fix Implementation
       │
       ▼
┌──────────────────┐
│ Phase 1: JUnit   │ ◄── Fast iteration
│ Simple tests     │
└────────┬─────────┘
         │ Pass?
         ▼
┌──────────────────┐
│ Phase 2: Strict  │ ◄── MUST pass before done
│ Model Compare    │
└────────┬─────────┘
         │ Pass?
         ▼
┌──────────────────┐
│ Document results │
│ in PATTERNS.md   │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ WAIT for user    │ ◄── DO NOT commit automatically
│ to decide commit │
└──────────────────┘
```

### Pattern Group Fix Application Workflow

**CRITICAL: When applying fixes across a pattern group, follow this exact workflow:**

```
┌─────────────────────────────────────────────────────────────────┐
│ Step 1: Fix ONE transformation in the pattern group            │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 2: Test with External Model (strict compare)              │
│         Command: mvn test -Dtest=Psm2AsmExternalModelTest      │
└────────────────────────┬────────────────────────────────────────┘
                         │
              ┌──────────┴──────────┐
              │                     │
           FAIL                   PASS
              │                     │
              ▼                     ▼
┌─────────────────────┐  ┌─────────────────────────────────────────┐
│ Fix issues, repeat  │  │ Step 3: Apply same fix to OTHER         │
│ Step 2              │  │ transformations in the pattern group    │
└─────────────────────┘  └────────────────────────┬────────────────┘
                                                  │
                                                  ▼
                         ┌─────────────────────────────────────────┐
                         │ Step 4: Test External Model again       │
                         │ (full strict compare)                   │
                         └────────────────────────┬────────────────┘
                                                  │
                         ┌────────────────────────┼────────────────┐
                         │                        │                │
                      ALL PASS               SOME FAIL         ALL FAIL
                         │                        │                │
                         ▼                        ▼                ▼
              ┌──────────────────┐    ┌──────────────────┐   ┌──────────────┐
              │ Document solution│    │ Step 5: Analyze  │   │ Revert, fix  │
              │ for pattern group│    │ failing ones     │   │ original     │
              │ WAIT for user to │    └────────┬─────────┘   └──────────────┘
              │ decide on commit │             │
              └──────────────────┘             │
                                    ┌─────────┴─────────┐
                                    │                   │
                              Same cause          Different behavior
                                    │                   │
                                    ▼                   ▼
                         ┌──────────────────┐  ┌───────────────────────┐
                         │ Fix and repeat   │  │ Step 6: SPLIT pattern │
                         │ Step 4           │  │ group, document why   │
                         └──────────────────┘  └───────────┬───────────┘
                                                           │
                                                           ▼
                                               ┌───────────────────────┐
                                               │ Step 7: Repeat from   │
                                               │ Step 1 for new group  │
                                               └───────────────────────┘
```

**Rules for Pattern Group Fixes:**

| Rule | Description |
|------|-------------|
| **Test before spreading** | NEVER apply fix to all transformations before External Model test passes for one |
| **Use strict comparison** | Simple JUnit tests are NOT sufficient - must use model comparison |
| **Analyze failures** | When some transformations fail, determine if same or different root cause |
| **Split when different** | Different behavior = different pattern group, don't force same solution |
| **Document splits** | Every split increases understanding of the codebase |
| **Iterate** | Each new pattern group goes through the same rigorous process |

---

## Summary of ETL vs ZETA Differences

| # | Transformation | Difference | Post-Process Step | Status |
|---|----------------|------------|-------------------|--------|
| 0 | ALL | Parallel execution disabled | N/A | PERMANENT |
| 1 | PSM2ASM | Root packages not added | Step 1 | WORKAROUND |
| 2 | PSM2ASM | EOpposite not set | Step 2 | WORKAROUND |
| 3 | PSM2ASM | TransferObjectRelation types null | Step 3 | WORKAROUND |
| 4 | PSM2ASM | Reference class inheritance | Step 4 | WORKAROUND |
| 5 | PSM2ASM | enrichWithAnnotations() | Step 5 | MONITORING |
| 6 | PSM2ASM | Extension type eType null | Step 6 | WORKAROUND |
| 7 | ASM2RDBMS | Root elements not added | postProcess | WORKAROUND |
| 8 | ASM2RDBMS | Name mappings | postProcess | WORKAROUND |
| 9 | PSM2Measure | XMI ID format mismatch | postProcess | WORKAROUND |
| 10 | RDBMS2Liquibase | Thread-safe changeSet | N/A | WORKAROUND |

---

## Version History

| Date | Author | Changes |
|------|--------|---------|
| 2026-01-16 | Claude | Added comprehensive ETL vs ZETA differences for all transformations |
| 2026-01-15 | Claude | Initial document adapted for judo-tatami-base with general ZETA patterns |
