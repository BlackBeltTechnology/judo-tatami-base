# Dual Engine Testing

## MODIFIED Requirements

### Requirement: Tests Must Use TransformationMode Enum

All dual-engine parameterized tests MUST use the `TransformationMode` enum from `judo-tatami-core` instead of module-specific `TransformationType` enums.

**Rationale**: Consolidates transformation mode selection into a single, centralized enum that is used by both production code (`*Work` classes) and test code, eliminating ambiguity and duplication.

**Previous Behavior**: Each module defined its own `TransformationType` enum in the test package with identical `ETL` and `ZETA` values.

**New Behavior**: All modules import and use `hu.blackbelt.judo.tatami.core.TransformationMode`.

#### Scenario: Parameterized test uses TransformationMode
Given a dual-engine transformation test class
When defining a parameterized test method
Then the method must import `hu.blackbelt.judo.tatami.core.TransformationMode`
And use `@EnumSource(TransformationMode.class)` annotation
And accept `TransformationMode` as the parameter type
And use `transformationMode.isZeta()` or `transformationMode == TransformationMode.ZETA` for conditional logic

Example:
```java
import hu.blackbelt.judo.tatami.core.TransformationMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedTest
@EnumSource(TransformationMode.class)
void testTransformation(TransformationMode transformationMode) throws Exception {
    if (transformationMode.isZeta()) {
        // Execute Zeta transformation
    } else {
        // Execute ETL transformation
    }
}
```

#### Scenario: No module-specific TransformationType exists
Given a transformation module test package
When examining the package structure
Then there must not be any `TransformationType.java` file in the test package
And all transformation mode references must use `hu.blackbelt.judo.tatami.core.TransformationMode`

#### Scenario: Production and test code use same enum
Given a `*Work` class that executes transformations
When the work class determines which transformation engine to use
Then it must use `TransformationMode.fromSystemProperty()`
And test code for that module must use the same `TransformationMode` enum
And there must be no discrepancy between production mode selection and test mode selection

## REMOVED Requirements

### ~~Requirement: Module-Specific TransformationType Enum~~

**REMOVED**: This requirement is obsolete. Module-specific `TransformationType` enums have been replaced by the centralized `TransformationMode` from judo-tatami-core.

~~Each transformation module MUST define a `TransformationType` enum in its test package with `ETL` and `ZETA` values for parameterized testing.~~

## Related Requirements

This change affects the following existing requirements:
- **zeta-transformations**: Rule classes and transformation execution patterns remain unchanged
- **psm2asm-transformation**: Transformation logic remains unchanged, only test infrastructure updated
