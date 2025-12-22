# OpenSpec Agent Instructions

This document provides guidance for AI assistants working with OpenSpec in the judo-tatami-base project.

## OpenSpec Workflow

### Creating a Proposal

1. **Explore First**: Understand the codebase before proposing changes
   ```bash
   rg <keyword>  # Search for patterns
   ls <directory>  # List structure
   ```

2. **Create Change Directory**:
   ```
   openspec/changes/<change-id>/
   ├── proposal.md    # Change overview and rationale
   ├── tasks.md       # Implementation tasks
   ├── design.md      # Architecture details (optional)
   └── specs/         # Spec deltas
       └── <capability>/
           └── spec.md
   ```

3. **Change ID Convention**: Use verb-led identifiers
   - `add-zeta-transformations`
   - `migrate-etl-to-java`
   - `implement-dual-testing`

### Proposal Structure

```markdown
# <Change Title>

## Summary
Brief description of what this change accomplishes.

## Motivation
Why is this change needed?

## Approach
High-level technical approach.

## Impact
- Modules affected
- Breaking changes
- Dependencies

## Risks
Potential issues and mitigations.
```

### Tasks Structure

```markdown
# Tasks

## Phase 1: <Phase Name>
- [ ] Task 1 description
- [ ] Task 2 description

## Phase 2: <Phase Name>
- [ ] Task 3 description
```

### Spec Delta Structure

```markdown
# <Capability Name>

## ADDED Requirements

### REQ-001: <Requirement Name>
Description of the requirement.

#### Scenario: <Scenario Name>
Given <precondition>
When <action>
Then <expected outcome>
```

## Project-Specific Guidelines

### Transformation Module Structure

When adding a new transformation:
```
judo-tatami-<source>2<target>/
├── pom.xml
├── src/main/java/
│   └── hu/blackbelt/judo/tatami/<source>2<target>/
│       ├── <Source>2<Target>.java           # Main transformer
│       ├── <Source>2<Target>Work.java       # Work class
│       └── <Source>2<Target>TransformationTrace.java
├── src/main/epsilon/
│   └── transformations/
└── src/test/java/
    └── hu/blackbelt/judo/tatami/<source>2<target>/
        ├── <Source>2<Target>Test.java
        └── <Source>2<Target>WorkTest.java
```

### Zeta Transformation Structure

When implementing Java-based transformations with Zeta:
```java
@TransformationContext(
    sourceModel = PsmModel.class,
    targetModel = AsmModel.class
)
public class Psm2AsmTransformation {
    
    @TransformRule(name = "EntityToClass")
    public EClass transformEntity(Entity entity, TransformationContext ctx) {
        // transformation logic
    }
}
```

### Test Patterns

For dual ETL/Zeta testing:
```java
@ParameterizedTest
@EnumSource(TransformationType.class)
void testTransformation(TransformationType type) {
    // Run transformation with specified type
    // Compare results
}
```

## Commands

```bash
# Validate a change
openspec validate <change-id> --strict

# List changes
openspec list

# Show change details
openspec show <change-id>

# Apply a change
openspec apply <change-id>
```

## Related Documentation

- `project.md` - Project conventions and structure
- `README.md` - Project overview
- `CONTRIBUTING.md` - Contribution guidelines
- `.claude/skills/zeta-validation.md` - Zeta validation framework guide
