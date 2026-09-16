# `Psm2AsmServiceTest.java`

Widest PSM→ASM suite in this directory: service layer (transfer object types,
transfer attributes, transfer object relations, bound and unbound operations).
888 lines — read at signature level (contract R5).

`public class Psm2AsmServiceTest`, `@Slf4j`, `MODEL_NAME` = `Test`, output under
`target/test-classes`.

## Tests

| Test | Shape |
|---|---|
| `testTransferObject(TransformationMode)` | `@ParameterizedTest` + `@EnumSource(TransformationMode.class)` — mapped/unmapped transfer object types, attributes, relations, embedded graphs |
| `testOperation(TransformationMode)` | same parameterization — bound/unbound operations, faults, input/output parameters |
| `testTransferObjectTypeAnnotationOnDefaultTransferObjects()` | `@Test` — default transfer objects generated for entity types carry the `transferObjectType` annotation |
| `testEtlAndZetaEquivalence()` | `@Test` — runs both engines fresh and compares via `ModelComparator` |

## Annotation URI constants asserted

Each is `AsmUtils.getAnnotationUri(<name>)`:
`mappedEntityType` (`MAPPED_SOURCE`), `constraints`, `binding`, `embedded`,
`permissions`, `script`, `outputParameterName`, `stateful`,
`instanceRepresentation`, `bound`, `abstract`, `initializer`, `behaviour`,
`transferObjectType`, `transient`.

## Contracts

- Two private `transform` overloads: `transform(testName, mode)` writes into the
  shared `asmModel` field, `transform(testName, mode, targetAsmModel)` writes
  into a caller-supplied model. Use the second when a test needs an isolated
  target, otherwise earlier cases' output leaks into the assertion.
- `transform` validates the PSM with `validatePsm(..., calculatePsmValidationScriptURI())`
  before transforming; an invalid PSM fails before any ASM is produced.
- `compareTransformations(testName)` is a no-op when
  `ModelComparator.isComparisonEnabled()` is false — a disabled comparison
  silently passes.
