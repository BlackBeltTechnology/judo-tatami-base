# Successful Patterns - PSM2ASM ZETA Transformation

**IMPORTANT: Document every solution that works. These patterns should be reused.**

## Need Something Else?

| If you need to... | Go to |
|-------------------|-------|
| Understand problems | [PROBLEMS.md](PROBLEMS.md) |
| Know what NOT to try | [FAILED_APPROACHES.md](FAILED_APPROACHES.md) |
| Profile performance | [PROFILING.md](PROFILING.md) |

---

## Pattern: Type-Based Rule Filtering

**Status:** IMPLEMENTED - Working correctly

**Problem Solved:**
ZETA framework was evaluating all rules against all candidate elements (2M+ guard evaluations).

**Solution:**
Use specific PSM types in `@Transform` annotation to enable type-based filtering:

```java
// BEFORE: Broad type - many unnecessary evaluations
@Transform(type = TransferObject.class)
public TransformFunction<TransferObject, EClass> createTransferObjectType()

// AFTER: Specific type - only evaluates against matching elements
@Transform(type = BoundTransferObjectType.class)
public TransformFunction<BoundTransferObjectType, EClass> createBoundTransferObjectType()
```

**Impact:**
- Reduced guard evaluations from 2M+ to 80,695 (25x reduction)
- Still 87% of transformation time in guard evaluation

**Where Applied:**
- TypeRules.java - Most rules use specific types
- ConstraintRules.java - Uses constraint subtypes
- ActorRules.java - Uses actor type

---

## Pattern: Post-Processing for Cross-References

**Status:** IMPLEMENTED - Required for correctness

**Problem Solved:**
During parallel rule execution, elements created by different rules cannot reference each other until all rules complete.

**Solution:**
Six post-processing steps after parallel execution:

| Step | Purpose |
|------|---------|
| 1 | Add root packages to ASM resource |
| 2 | Set EOpposite for bidirectional associations |
| 3 | Set TransferObjectRelation eType references |
| 4 | Reference class inheritance (`__Reference`) |
| 5 | Enrich with annotations (exposedBy, etc.) |
| 6 | Fix null eType in extension types |

**Key Code:**
```java
// In Psm2AsmZetaTransformation.postProcess()
addRootPackagesFromCache(ctx);           // Step 1
setEOpposite(ctx);                       // Step 2
setTransferObjectRelationType(ctx);       // Step 3
setReferenceClassInheritance(ctx);        // Step 4
enrichWithAnnotations(ctx);               // Step 5
fixNullAttributeTypes(ctx);               // Step 6
```

**Impact:**
- Total post-processing: ~479ms
- Required for model correctness

---

## Pattern: Extension Type Fixup

**Status:** IMPLEMENTED - Fixed in step 6

**Problem Solved:**
Extension transfer object types (`_default_`, `_binding_`) have attributes with null eType after parallel execution.

**Solution:**
Post-processing step 6 (`fixNullAttributeTypes()`) resolves by:
1. Building type lookup map by name
2. Finding EAttributes with null eType in extension classes
3. Looking up PSM source to get original type name
4. Setting eType from lookup map

**Typical Fix Count:** ~16 attributes per transformation

---

## Pattern: Benchmark-Driven Validation

**Status:** RECOMMENDED - Use for all changes

**Problem Solved:**
Changes may break model equivalence or cause performance regressions.

**Solution:**
Always run the benchmark test with strict comparison:

```bash
# Quick compile check
mvn compile -pl judo-tatami-psm2asm -q

# Benchmark with strict XMI ID comparison
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest \
    -Djudo.test.comparison.mode=STRICT \
    -Djudo.test.comparison.xmiIds=true \
    -Pperformance
```

**Expected Results:**
- ZETA speedup: ~7.5x over ETL
- Guard evaluations: ~80,695
- Transformation time: ~5000ms for rackinspect model

---

## Template for New Patterns

```markdown
## Pattern: [Descriptive Name]

**Status:** IMPLEMENTED | RECOMMENDED | EXPERIMENTAL

**Problem Solved:**
[What problem does this pattern address?]

**Solution:**
[Code example or description of the solution]

**Impact:**
- [Measured benefit]
- [Any tradeoffs]

**Where Applied:**
- [Files/classes where this is used]
```
