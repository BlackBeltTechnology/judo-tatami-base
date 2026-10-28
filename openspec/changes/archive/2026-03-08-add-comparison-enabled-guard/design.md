## Context

All 5 `*DiscoveryComparisonTest` classes run both performance measurement and model comparison. The rest of the test suite uses `ModelComparator.isComparisonEnabled()` to conditionally skip comparison when `judo.test.comparison.enabled=false`. The discovery tests don't follow this convention.

## Goals / Non-Goals

**Goals:**
- Allow discovery tests to run in performance-only mode by respecting `judo.test.comparison.enabled`
- Follow the established pattern used across all other dual-transformation tests

**Non-Goals:**
- No new test classes or abstractions
- No changes to test tags or discovery logic

## Decisions

**Wrap comparison block with `isComparisonEnabled()` guard**: Same `if (!ModelComparator.isComparisonEnabled()) { return; }` pattern used in all other tests. When disabled, the test still runs ETL + Zeta, prints `printResults()`, then returns without comparing.

**Log a message when skipping comparison**: Add `log.info("Model comparison skipped (disabled via system property)")` so it's clear in output that comparison was intentionally skipped, not silently omitted.

## Risks / Trade-offs

None significant. This is a minimal consistency fix following an established pattern.
