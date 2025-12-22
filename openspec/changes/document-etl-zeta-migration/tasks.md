# Tasks

## Phase 1: Create Migration Documentation Structure

- [ ] Create `docs/migration/` directory structure
- [ ] Create `docs/migration/README.md` with documentation index

## Phase 2: ETL vs Zeta Comparison Documentation

- [ ] Document syntax comparison table (ETL constructs vs Zeta annotations)
- [ ] Document rule structure patterns (@greedy, guards, lazy rules)
- [ ] Document multi-output transformation patterns (equivalents/equivalentAll)
- [ ] Document post-processing patterns
- [ ] Create feature equivalence matrix

## Phase 3: Migration Guide Documentation

- [ ] Document step-by-step migration process
- [ ] Document common pitfalls and solutions:
  - Handling order dependencies
  - Managing bidirectional references in post-processing
  - Avoiding recursive update issues
- [ ] Document helper method extraction patterns
- [ ] Document XMI ID handling patterns
- [ ] Create migration checklist

## Phase 4: Testing Framework Documentation

- [ ] Document TransformationMode enum pattern (from judo-tatami-core)
- [ ] Document parameterized test setup
- [ ] Document ModelComparator usage and configuration
- [ ] Document performance testing framework
- [ ] Document RealisticModelGenerator usage
- [ ] Create test setup examples

## Phase 5: Update Module Documentation

- [ ] Update `docs/transformations/psm2asm.md` with detailed ETL-to-Zeta rule mapping
- [ ] Update `docs/transformations/asm2rdbms.md` with detailed ETL-to-Zeta rule mapping
- [ ] Update `docs/transformations/rdbms2liquibase.md` with detailed ETL-to-Zeta rule mapping
- [ ] Update `docs/transformations/psm2measure.md` with detailed ETL-to-Zeta rule mapping
- [ ] Update `docs/transformations/asm2keycloak.md` with detailed ETL-to-Zeta rule mapping

## Phase 6: Validation and Review

- [ ] Verify all documentation links are correct
- [ ] Review code examples for accuracy
- [ ] Update `docs/transformations/README.md` with migration documentation links
