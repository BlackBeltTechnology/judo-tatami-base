## Context

The `JqlTransformers` class in `judo-meta-expression` has an instance-level `AtomicInteger` counter that generates iterator variable names (`_iterator_1`, `_iterator_2`, ...). The counter increments cumulatively across all expressions extracted in a single `AsmJqlExtractor.extractExpressions()` call. A `resetIteratorCounter()` method already exists but is never called.

Both ETL and Zeta use the same `AsmJqlExtractor` Java class. The only difference is the ASM model element ordering, which determines which expressions are processed first and thus which counter values they get.

## Goals / Non-Goals

**Goals:**
- Iterator variable names are deterministic regardless of ASM element processing order
- RackInspect and SimpleOrderManagement comparison tests produce 9/9 EQUIVALENT

**Non-Goals:**
- Changing how iterator variables work semantically
- Changes in judo-tatami-base (the fix is entirely in judo-meta-expression)

## Decisions

### Decision 1: Reset counter before each independent expression

Call `builder.resetIteratorCounter()` before each `createExpression()` invocation in the extractor. Each expression independently starts at `_iterator_1`.

**Why not per-entity-type or per-transfer-object-type:** Per-expression is the most granular and robust — even if attributes within the same entity are processed in different order, each attribute's expression is self-contained.

**Why this is safe:** Lambda iterators are scoped within their expression. Two expressions can both have `_iterator_1` without collision because they live in separate expression trees.

### Decision 2: Change is in judo-meta-expression repo

The `AsmJqlExtractor` and `AdaptableJqlExtractor` live in `judo-meta-expression/builder-jql`. After the fix is released, update the dependency version in `judo-tatami-base/pom.xml`.

## Risks / Trade-offs

- **[Risk] Expression sharing iterators** → If any expression reuses an iterator from a different scope, resetting would cause name collisions. → Mitigated: each `createExpression()` call builds an independent expression tree.
- **[Trade-off] Dependency release required** → judo-meta-expression must be released before tatami-base benefits. → Can be tested locally with SNAPSHOT.
