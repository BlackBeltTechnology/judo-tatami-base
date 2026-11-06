# Installing JUDO Expression Skills

## Module Information
- **GroupId**: hu.blackbelt.judo.tatami
- **ArtifactId**: judo-tatami-asm2query
- **Version**: ${project.version}

## Installation

### Extract from Maven cache

```bash
JAR=~/.m2/repository/hu/blackbelt/judo/tatami/judo-tatami-asm2query/${project.version}/judo-tatami-asm2query-${project.version}.jar
unzip -o "$JAR" "claude/*" "agent-docs/*" -d .
```

## Available Skills

| Skill | Command | Description |
|-------|---------|-------------|
| Expression Syntax | `/judo-expression:expression-syntax` | Understand expression metamodel and bindings |
| Custom Functions | `/judo-expression:custom-functions` | Add custom expression translators |

## Key Classes

- `TransferObjectTypeBindingsCollector` - Expression tree orchestrator
- `MappedTransferObjectTypeBindings` - Expression tree nodes
- `EntityTypeExpressions` - Entity-level expression cache
