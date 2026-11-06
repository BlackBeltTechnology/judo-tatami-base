# Tasks

## Phase 1: Update Plugin Configuration

- [x] Update maven-javadoc-plugin from 3.4.1 to 3.11.2 in parent pom.xml
- [x] Update javadoc source configuration from 8 to 21
- [x] Add `<release>21</release>` configuration
- [x] Add `<doclint>none</doclint>` to suppress strict validation
- [x] Add lombok 1.18.34 as dependency override in lombok-maven-plugin (already configured via ${lombok-version})

## Phase 2: Test and Validate

- [x] Run `mvn javadoc:javadoc` and verify errors are reduced
- [x] Verify delombok output is generated correctly in target/delombok
- [x] Check generated javadoc for quality (no missing classes/methods)

## Phase 3: Fallback Implementation (if needed)

- [x] ~~If delombok still fails, implement Option 2 using exec-maven-plugin~~ (Not needed - Option 1 works)

## Phase 4: Documentation

- [x] Changes documented in proposal.md
