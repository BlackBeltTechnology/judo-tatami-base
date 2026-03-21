## Context

Each module has two external model test classes with nearly identical transformation logic but different model source mechanisms. The DiscoveryComparisonTest uses `@TestFactory` + `DynamicTest` for auto-discovered models. The ExternalModelTest uses `@ParameterizedTest` + `@MethodSource` for properties-file configured models.

## Goals / Non-Goals

**Goals:**
- Single test class per module handles both discovery and properties-file models
- Properties-file models retain warmup and iteration support
- Discovery models run with 1 iteration (no warmup) as before

**Non-Goals:**
- Not changing AbstractExternalModelTest or ExternalModelConfig
- Not changing the properties file format

## Decisions

**Merge into DiscoveryComparisonTest**: The `@TestFactory` pattern is more flexible than `@ParameterizedTest` since it can combine multiple model sources into one collection of DynamicTests. The `compareEtlAndZetaForDiscoveredModels()` method will collect models from both `discoverModels()` AND `loadModelConfigs()`, deduplicating by name.

**Warmup and iteration support via ExternalModelConfig**: Discovery-created configs have empty parameters (default: no warmup, 1 iteration). Properties-file configs may specify `warmup=true;iterations=3`. The `testModel()` method checks `config.isWarmupEnabled()` and `config.getIterations()` - this works automatically since ExternalModelConfig already has these methods.

**Dual tags**: Add both `@Tag("comparison")` and `@Tag("performance")` so the tests can be selected by either tag.

**Model source priority**: Properties-file models override discovery models with the same name (properties may have custom parameters like warmup/iterations/dialect).

## Risks / Trade-offs

- Slightly longer test method with warmup/iteration loop, but follows the same proven pattern from ExternalModelTest
- Discovery models that also appear in properties will only run once (properties version takes priority)
