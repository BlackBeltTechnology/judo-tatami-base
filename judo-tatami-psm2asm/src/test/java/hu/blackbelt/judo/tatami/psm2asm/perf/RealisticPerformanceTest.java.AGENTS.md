# `RealisticPerformanceTest.java`

Times both engines on RackInspect-shaped synthetic models produced by `RealisticPsmModelGenerator`.

## Tests

- `testSmallRealisticModel` (20 entities)
- `testRealisticModelPerformance` (70, "RackInspect-like")
- `testLargeRealisticModel` (100)

Each calls `runPerformanceTest(entityCount, name)`, counts elements via `RealisticPsmModelGenerator.countElements`, and compares outputs with `ModelComparator`.

## Invocation

`mvn test -pl judo-tatami-psm2asm -Dtest=RealisticPerformanceTest -Pperformance`