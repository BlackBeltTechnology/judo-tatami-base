## Why

There are two separate test classes per module for external model testing: `*ExternalModelTest` (properties-file based, supports warmup/iterations) and `*DiscoveryComparisonTest` (auto-discovery based, single iteration). The discovery tests don't support properties-file models, and the external model tests don't use auto-discovery. This duplication means maintaining two parallel test classes with nearly identical transformation logic.

## What Changes

- Merge `*DiscoveryComparisonTest` to support both model sources: auto-discovery AND properties-file config
- Add warmup and iteration support from properties-file configs
- Delete `*ExternalModelTest` classes (5 files) - their functionality is absorbed
- Both `@Tag("comparison")` and `@Tag("performance")` on the unified class

## Capabilities

### New Capabilities

- `unified-external-model-test`: Single test class per module that combines auto-discovery and properties-file model sources with warmup/iterations support

### Modified Capabilities

## Impact

- 5 `*DiscoveryComparisonTest` files modified (add properties support, warmup, iterations)
- 5 `*ExternalModelTest` files deleted
- 5 `external-model-tests.properties` files remain unchanged (still work)
- `AbstractExternalModelTest` unchanged
