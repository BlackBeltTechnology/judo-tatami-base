# Contributing to JUDO

## Installing the correct versions of Java, Maven and necessary dependencies

Please make sure your development environment complies with the requirements discussed under the relevant section of the parent project's [CONTRIBUTING](https://github.com/BlackBeltTechnology/judo-community/blob/develop/CONTRIBUTING.adoc) guide.

## Code Structure

This project follows a standard Java project structure, governed by Maven, with potential Maven submodules.

### Project Layout

```
judo-tatami-base/
├── judo-tatami-psm2asm/          # PSM to ASM transformation
├── judo-tatami-psm2measure/      # PSM to Measure transformation
├── judo-tatami-asm2rdbms/        # ASM to RDBMS transformation
├── judo-tatami-rdbms2liquibase/  # RDBMS to Liquibase transformation
├── judo-tatami-asm2expression/   # ASM to Expression transformation
├── judo-tatami-asm2keycloak/     # ASM to Keycloak transformation
├── judo-tatami-psm-validation/   # PSM model validation
├── judo-tatami-asm-validation/   # ASM model validation
├── judo-tatami-expression-asm-validation/  # Expression on ASM validation
├── judo-tatami-expression-psm-validation/  # Expression on PSM validation
├── osgi-itest/                   # OSGi integration tests
└── p2/                           # Eclipse P2 repository
```

### Transformation Module Structure

Each transformation module follows this pattern:

```
judo-tatami-<source>2<target>/
├── pom.xml
├── src/main/java/
│   └── hu/blackbelt/judo/tatami/<source>2<target>/
│       ├── <Source>2<Target>.java           # Main transformer
│       ├── <Source>2<Target>Work.java       # Work class
│       └── zeta/                            # Zeta implementation
│           └── <Source>2<Target>RuleNames.java
├── src/main/epsilon/
│   └── transformations/
│       ├── <source>To<Target>.etl           # Main ETL file
│       └── modules/*.etl                    # Module ETL files
└── src/test/java/
    └── hu/blackbelt/judo/tatami/<source>2<target>/
        ├── <Source>2<Target>Test.java
        └── <Source>2<Target>WorkTest.java
```

## Submission Guidelines

### Submitting an Issue

Before you submit an issue, please search the issue tracker. An issue for your problem may already exist and has been resolved, or the discussion might inform you of workarounds readily available.

We want to fix all the issues as soon as possible, but before fixing a bug we need to reproduce and confirm it. Having a reproducible scenario gives us wealth of important information without going back and forth with you requiring additional information, such as:

- the output of `java -version`, `mvn -version`
- `pom.xml` or `.flattened-pom.xml` (when applicable)
- and most importantly - a use-case that fails

A minimal reproduction allows us to quickly confirm a bug (or point out a coding problem) as well as confirm that we are fixing the right problem.

We will be insisting on a minimal reproduction in order to save maintainers' time and ultimately be able to fix more bugs. We understand that sometimes it might be hard to extract essentials bits of code from a larger codebase, but we really need to isolate the problem before we can fix it.

You can file new issues by filling out our [issue form](https://github.com/BlackBeltTechnology/judo-tatami-base/issues/new/choose).

### Submitting a PR

This project follows [GitHub's standard forking model](https://guides.github.com/activities/forking/). Please fork the project to submit pull requests.

## Commands

### Run Tests

```bash
mvn clean test
```

### Run Full build

```bash
mvn clean install
```

### Run with specific profile

```bash
mvn clean install -Pmodules
```

## Development Guidelines

### Adding Transformation Rules

When adding new transformation rules:

1. **ETL Implementation**: Add the rule to the appropriate `.etl` file in `src/main/epsilon/transformations/`
2. **Zeta Implementation**: Add the corresponding Java method with `@TransformRule` annotation
3. **Rule Constants**: Add the rule name constant to `*RuleNames.java`
4. **Tests**: Add tests that verify both ETL and Zeta implementations produce equivalent output

### Testing

Tests should use the dual transformation testing framework:

```java
@ParameterizedTest
@EnumSource(TransformationMode.class)
void testMyTransformation(TransformationMode mode) {
    if (mode.isZeta()) {
        // Run Zeta transformation
    } else {
        // Run ETL transformation
    }
}
```

### Documentation

- Document transformation rules in `docs/transformations/`
- Update module documentation when adding features
- Keep AGENTS.md updated for AI assistant context
