# Tasks: dual-comparison-test-infrastructure

## Implementation

- [x] Create `PerformanceMeasurement.java` in `judo-tatami-test-utils` with `TimedResult<T>` record, `measure()`, and `measureAvg()` static methods
- [x] Create `AbstractDualComparisonTest.java` in `judo-tatami-test-utils` extending `AbstractExternalModelTest` with 5 abstract methods, optional overrides, `@TestFactory compareExternalModels()`, and `assertDualEquivalent()` convenience methods
- [x] Add unit tests for `PerformanceMeasurement` (measure single, measure average, exception propagation)
- [x] Add unit tests for `AbstractDualComparisonTest` using a mock subclass with in-memory EMF models (test @TestFactory orchestration, assertDualEquivalent, empty model handling, failure-on-diff behavior)
- [x] Verify `judo-tatami-test-utils` compiles and all tests pass
