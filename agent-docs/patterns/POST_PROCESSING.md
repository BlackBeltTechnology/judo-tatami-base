# Post-Processing Steps - PSM2ASM ZETA Transformation

## Need Something Else?

| If you need to... | Go to |
|-------------------|-------|
| Understand problems | [PROBLEMS.md](PROBLEMS.md) |
| Profile performance | [PROFILING.md](PROFILING.md) |
| Find working solutions | [SUCCESSFUL_PATTERNS.md](SUCCESSFUL_PATTERNS.md) |

---

## Why Post-Processing?

ZETA executes transformation rules in parallel. This means:
- Elements are created concurrently
- Cross-references between elements cannot be set during creation
- Some operations require all elements to exist first

Post-processing runs **after** all rules complete to fix up these cross-references.

---

## Post-Processing Steps

### Step 1: Add Root Packages (~110ms)

**Purpose:** Add EPackages to ASM resource and apply XMI IDs

**Code Location:** `Psm2AsmZetaTransformation.addRootPackagesFromCache()`

**What It Does:**
- Retrieves created packages from transformation cache
- Adds them to ASM resource root
- Applies deterministic XMI IDs

---

### Step 2: Set EOpposite (~5ms)

**Purpose:** Set bidirectional association opposites

**Code Location:** `Psm2AsmZetaTransformation.setEOpposite()`

**What It Does:**
- Finds EReference pairs that should be opposites
- Sets `eOpposite` property on both ends
- Required for EMF bidirectional navigation

---

### Step 3: Set TransferObjectRelation Types (~10ms)

**Purpose:** Set EReference.eType for transfer object relations

**Code Location:** `Psm2AsmZetaTransformation.setTransferObjectRelationType()`

**What It Does:**
- Finds EReferences representing transfer object relations
- Looks up target type from transformation cache
- Sets `eType` property

---

### Step 4: Reference Class Inheritance (~5ms)

**Purpose:** Set up `__Reference` class supertypes

**Code Location:** `Psm2AsmZetaTransformation.setReferenceClassInheritance()`

**What It Does:**
- Finds `__Reference` marker classes
- Sets inheritance hierarchy
- Required for reference class pattern

---

### Step 5: Enrich with Annotations (~239ms)

**Purpose:** Add cross-referencing annotations (exposedBy, etc.)

**Code Location:** `Psm2AsmZetaTransformation.enrichWithAnnotations()`

**What It Does:**
- Adds `exposedBy` annotations linking operations to exposing types
- Sets up other cross-reference annotations
- Most time-consuming post-processing step

---

### Step 6: Fix Null eType (~108ms)

**Purpose:** Fix null eType in extension transfer object types

**Code Location:** `Psm2AsmZetaTransformation.fixNullAttributeTypes()`

**What It Does:**
1. Builds type lookup map by name
2. Finds EAttributes with null eType in `_default_` / `_binding_` classes
3. Looks up PSM source attribute to get original type name
4. Sets eType from lookup map

**Typical Fix Count:** ~16 attributes per transformation

**Why Needed:**
Extension transfer object types (`_default_`, `_binding_`) have attributes whose PSM `dataType` references don't match instances transformed by TypeRules. This causes `ctx.equivalent()` to return null during parallel execution.

---

## Performance Summary

| Step | Time | % of Post-Processing |
|------|------|---------------------|
| 1. Add root packages | ~110ms | 23% |
| 2. Set EOpposite | ~5ms | 1% |
| 3. Set TOR types | ~10ms | 2% |
| 4. Reference inheritance | ~5ms | 1% |
| 5. Enrich annotations | ~239ms | 50% |
| 6. Fix null eType | ~108ms | 23% |
| **Total** | **~479ms** | **100%** |

---

## Debugging Post-Processing

### Null eType Errors
- Check step 6 ran correctly
- Verify extension type patterns (`_default_`, `_binding_`)
- Check type lookup map was built

### Missing EOpposite
- Verify step 2 completed
- Check source PSM has bidirectional association

### Missing Annotations
- Step 5 may have incomplete source data
- Check transformation cache has required elements
