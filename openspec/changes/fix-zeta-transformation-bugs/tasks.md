# Tasks

## Prerequisites

- [ ] **0.1** Update `judo-zeta` dependency to latest snapshot version in parent pom.xml
- [ ] **0.2** Verify build succeeds with updated dependency
- [ ] **0.3** Run all existing tests to establish baseline

## Bug 1: PSM2ASM - Missing Model Prefix

- [ ] **1.1** Analyze ETL `asmUtils.getClassifierFQName()` implementation to understand expected behavior
- [ ] **1.2** Fix `Psm2AsmHelper.getClassifierFQName()` to include root model package name
- [ ] **1.3** Verify fix produces same output as ETL for classifier FQN
- [ ] **1.4** Run `Psm2AsmExternalModelTest` with STRICT + XMI ID comparison
- [ ] **1.5** Run `Psm2AsmDualTransformationTest` to verify no regression

## Bug 2: PSM2Measure - Wrong Ordering

- [ ] **2.1** Analyze ETL measure iteration order to understand expected behavior
- [ ] **2.2** Identify source of ordering difference in Zeta transformation
- [ ] **2.3** Fix ordering to match ETL (likely need deterministic iteration)
- [ ] **2.4** Run `Psm2MeasureExternalModelTest` with STRICT comparison
- [ ] **2.5** Run `Psm2MeasureTest` to verify no regression

## Bug 3: RDBMS2Liquibase - Flaky Test

- [ ] **3.1** Run `Rdbms2LiquibaseExternalModelTest` multiple times (10+) to reproduce flakiness
- [ ] **3.2** Analyze changeSet generation for non-deterministic behavior
- [ ] **3.3** If flaky, identify and fix root cause (likely ordering issue)
- [ ] **3.4** Verify test is stable after fix

## Enable Parallel Execution

- [ ] **4.1** Change `parallel(false)` to `parallel(true)` in `Asm2RdbmsZetaTransformation.java`
- [ ] **4.2** Run `Asm2RdbmsExternalModelTest` multiple times to verify parallel execution is stable
- [ ] **4.3** Run `Asm2RdbmsTest` to verify no regression

## Final Verification

- [ ] **5.1** Run all external model tests with STRICT comparison:
  - `Psm2AsmExternalModelTest` (with XMI ID comparison)
  - `Asm2RdbmsExternalModelTest`
  - `Psm2MeasureExternalModelTest`
  - `Rdbms2LiquibaseExternalModelTest`
  - `Asm2KeycloakExternalModelTest`
- [ ] **5.2** Run all dual transformation tests
- [ ] **5.3** Verify all 5 transformations produce equivalent output
- [ ] **5.4** Run full test suite to ensure no regressions
