# Document ETL to Zeta Migration Patterns

## Summary

Create comprehensive documentation comparing ETL (Epsilon Transformation Language) and Zeta (Java-based transformation framework) implementations, including detailed migration patterns and testing strategies. This documentation will serve as a reusable guide for migrating transformation logic from ETL to Zeta in other projects.

## Motivation

The judo-tatami-base project has successfully implemented dual transformation engines (ETL and Zeta) for all five transformation modules. This presents a unique opportunity to:

1. **Document proven patterns** - Capture the patterns that worked for ETL-to-Zeta migration
2. **Enable knowledge transfer** - Allow other projects to adopt Zeta transformations using this project as a reference
3. **Establish testing best practices** - Document how to ensure output equivalence between ETL and Zeta implementations
4. **Preserve institutional knowledge** - Ensure the rationale behind transformation decisions is recorded

## Scope

### In Scope

1. **ETL vs Zeta Comparison Guide** (`docs/migration/etl-zeta-comparison.md`)
   - Side-by-side syntax comparison
   - Structural pattern mapping
   - Feature equivalence matrix
   - Key differences and their rationale

2. **Migration Guide** (`docs/migration/etl-to-zeta-migration.md`)
   - Step-by-step migration process
   - Common pitfalls and solutions
   - Refactoring patterns
   - Performance considerations

3. **Testing Strategy Guide** (`docs/migration/dual-engine-testing.md`)
   - Parameterized test pattern with TransformationMode enum (from judo-tatami-core)
   - ModelComparator usage and configuration
   - Performance test framework
   - Equivalence verification patterns

4. **Module-specific documentation** (update existing `docs/transformations/*.md`)
   - Add Zeta implementation details
   - Document rule-by-rule ETL to Zeta mapping

### Out of Scope

- Code changes to transformation implementations
- New transformation rules
- Automated migration tooling

## Approach

1. Analyze all ETL files and their corresponding Zeta implementations
2. Document pattern categories (simple rules, guard conditions, multi-output rules, lazy rules)
3. Create migration checklist and decision flowchart
4. Document the testing framework components and usage
5. Update existing module documentation with ETL-to-Zeta mapping tables

## Impact

- **Modules affected**: Documentation only (docs/, openspec/)
- **Breaking changes**: None
- **Dependencies**: None

## Risks

| Risk | Mitigation |
|------|------------|
| Documentation becomes outdated | Link to actual code files; use automated checks where possible |
| Patterns may not apply to all projects | Document assumptions and limitations clearly |

## Deliverables

1. `docs/migration/README.md` - Migration documentation index
2. `docs/migration/etl-zeta-comparison.md` - Detailed syntax and pattern comparison
3. `docs/migration/etl-to-zeta-migration.md` - Step-by-step migration guide
4. `docs/migration/dual-engine-testing.md` - Testing framework documentation
5. Updated `docs/transformations/*.md` files with Zeta rule mappings
