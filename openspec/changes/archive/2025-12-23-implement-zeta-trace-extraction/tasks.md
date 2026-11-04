# Tasks

## Phase 1: Zeta Transformation Return Type Change

- [x] Change `Psm2AsmZetaTransformation.execute()` to return `TransformationTrace`
- [x] Change `Asm2RdbmsZetaTransformation.execute()` to return `TransformationTrace` (build from internal trace map)
- [N/A] Change `Rdbms2LiquibaseZetaTransformation.execute()` - no trace support
- [x] Change `Psm2MeasureZetaTransformation.execute()` to return `TransformationTrace`
- [x] Change `Asm2KeycloakZetaTransformation.execute()` to return `TransformationTrace` (refactored to use TransformationExecutor)
- [x] Remove `buildTraceResult()` methods (no longer needed for Executor-based transforms)

## Phase 2: TransformationTrace Class Updates

- [x] Add `zetaTrace` field to `Psm2AsmTransformationTrace`
- [x] Add `getZetaTrace()` method to `Psm2AsmTransformationTrace`
- [x] Add `isZetaTrace()` method to `Psm2AsmTransformationTrace`
- [x] Update `getTransformationTrace()` to return empty map when Zeta trace is set
- [x] Make `trace` field nullable (null when Zeta used)
- [x] Apply same changes to `Asm2RdbmsTransformationTrace`
- [N/A] Apply same changes to `Rdbms2LiquibaseTransformationTrace` - class doesn't exist
- [x] Apply same changes to `Psm2MeasureTransformationTrace`
- [x] Apply same changes to `Asm2KeycloakTransformationTrace`

## Phase 3: Work Class Updates (Dual Code Paths)

- [x] Update `Psm2AsmWork.executeZetaTransformation()` to use returned `TransformationTrace`
- [x] Update `Psm2AsmWork` to build trace with `zetaTrace` field for Zeta path
- [x] Update `Psm2AsmWork` to build trace with `trace` field for ETL path
- [x] Update `Asm2RdbmsWork` to use `zetaTrace` field for Zeta path
- [N/A] Update `Rdbms2LiquibaseWork` - no trace support
- [x] Update `Psm2MeasureWork` to use `zetaTrace` field for Zeta path
- [x] Update `Asm2KeycloakWork` to use `zetaTrace` field for Zeta path

## Phase 4: Testing

### Common Trace API Tests (per module)
- [x] Add test verifying Zeta trace is non-null after transformation
- [x] Add test verifying trace entries collection is accessible
- [x] Add test verifying trace JSON export works
- [x] Add test verifying trace entries have valid source/target/ruleName

### Trace Content Tests (per module)

#### psm2asm trace content - `Psm2AsmZetaTraceTest.java`
- [x] Verify trace entry count is non-zero
- [x] Verify `CreateEntityClass` rule maps `EntityType` → `EClass`
- [x] Verify `CreateAttribute` rule maps `Attribute` → `EAttribute`
- [x] Verify `CreateMappedTransferObjectTypeClass` rule maps `TransferObjectType` → `EClass`
- [x] Verify trace JSON export contains required fields (`traceEntries`, `ruleName`, `source`, `target`)

#### asm2rdbms trace content - `Asm2RdbmsZetaTraceTest.java`
- [x] Verify trace entry count is non-zero
- [x] Verify `EClassToRdbmsTable` rule maps `EClass` → `RdbmsTable`
- [x] Verify `EAttributeToTableValueField` rule maps `EAttribute` → `RdbmsField`
- [x] Verify `EReferenceToRdbmsTableForeignKey` rule maps `EReference` → `RdbmsForeignKey`
- [x] Verify trace JSON export contains required fields

#### psm2measure trace content - `Psm2MeasureZetaTraceTest.java`
- [x] Verify trace entry count is non-zero
- [x] Verify `CreateBaseMeasure`/`CreateDerivedMeasure` rules produce correct mappings
- [x] Verify `CreateUnit`/`CreateDurationUnit` rules produce correct mappings
- [x] Verify trace JSON export contains required fields

#### asm2keycloak trace content - `Asm2KeycloakZetaTraceTest.java`
- [x] Verify trace is non-null (entries may be empty if no realm-annotated actors)
- [x] Verify `CreateKeycloakClient` rule maps actor types → Keycloak clients (when present)
- [x] Verify trace JSON export contains required fields

## Phase 5: Documentation

- [x] Update migration docs with Zeta trace API (`getZetaTrace()`, `isZetaTrace()`)
- [x] Document dual trace strategy in `docs/migration/etl-to-zeta-migration.md`
- [x] Add trace JSON export example in migration docs
- [x] Add hand-written trace building example in migration docs

## Implementation Notes

### TransformationExecutor-based modules (return native Zeta TransformationTrace)
- **psm2asm** - Uses TransformationExecutor, returns `result.getTrace()`
- **psm2measure** - Uses TransformationExecutor, returns `result.getTrace()`
- **asm2keycloak** - Refactored to use TransformationExecutor, returns `result.getTrace()`

### Hand-written modules (must build native Zeta TransformationTrace)
- **asm2rdbms** - Hand-written (~960 lines), must call `buildZetaTrace()` from internal trace map
- **rdbms2liquibase** - Hand-written, no trace support (N/A)

### Key requirement
**All Zeta-based transformations must produce native Zeta trace format.** Hand-written transformations that don't use `TransformationExecutor` must build the trace manually from their internal trace tracking.
