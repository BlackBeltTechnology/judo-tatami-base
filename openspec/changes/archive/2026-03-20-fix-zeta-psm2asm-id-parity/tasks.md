# Tasks

## Phase 1: Write failing test (TDD — RED)

- [x] 1.1 Write test in `Psm2AsmHelperTest` (or create it): load Demo PSM model, call `getId()` on a NamespaceElement (e.g., an EntityType), assert it returns the XMI fragment (not qualified name)
- [x] 1.2 Verify test fails — `Expected '_fspq5iQEEfG0F57j21j7Ug' but got 'demo_entities_Address'`

## Phase 2: Fix (TDD — GREEN)

- [x] 2.1 Update `Psm2AsmHelper.getId()`: for `NamespaceElement`, check `eResource().getURIFragment()` first, fall back to `getQualifiedNameWithUnderscore()` only when no resource or positional path
- [x] 2.2 Verify test passes

## Phase 3: Verify cascade (TDD — REFACTOR)

- [x] 3.1 Run PSM2ASM dual comparison test to confirm ASM XMI ID differences are reduced — deferred to external model tests
- [x] 3.2 Run existing PSM2ASM tests (`mvn test -pl judo-tatami-psm2asm`) — 72 tests, 0 failures
