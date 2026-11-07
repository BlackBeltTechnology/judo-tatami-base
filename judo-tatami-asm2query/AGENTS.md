# AGENTS.md — `judo-tatami-asm2query`

Module root of the Asm2Query bundle (see change: persisted-query-model). Code is documented in the `src/main/java`,
`src/main/resources` and `src/test/java` directory `AGENTS.md` files.

| File | Purpose |
| --- | --- |
| `agent-docs/README.md` | Agent docs of the skill package (moved from `judo-runtime-core-expression`): module overview, quick start with `new TransferObjectTypeBindingsCollector(asmResourceSet, expressionResourceSet)`. Copied to `/agent-docs` in the jar by the POM `<resource>` (`targetPath agent-docs`). |
| `agent-docs/architecture.md` | Agent docs: mermaid diagrams of the expression binding tree (collector, mapped / unmapped bindings, `EntityTypeExpressions`). `JarSkillPackageTest` asserts the mermaid block. |
| `agent-docs/extension-points.md` | Agent docs: `TransferObjectTypeBindingsCollector` (location `hu.blackbelt.judo.tatami.asm2query`), `MappedTransferObjectTypeBindings`, entity expressions and static flags. |
| `pom.xml` | `bundle` POM. Filters `asm2query-generator.properties` (version, build timestamp) and the `claude/` skill files (`${project.version}`); `Export-Package hu.blackbelt.judo.tatami.asm2query.*`; no direct `epsilon-*`, `mapper-api` or `judo-dispatcher-api` dependency. |
| `src/main/resources/claude/INSTALL.md` | Skill package install notes (artifact `hu.blackbelt.judo.tatami:judo-tatami-asm2query`). Relocated from `judo-runtime-core-expression`; the runtime-core copy is removed at rank 8. |
| `src/main/resources/claude/marketplace.json` | Claude marketplace descriptor (name / artifactId `judo-tatami-asm2query`, `agentDocs ./agent-docs`). Filtered. |
| `src/main/resources/claude/plugins/judo-expression/.claude-plugin/plugin.json` | Plugin descriptor of the `judo-expression` plugin (skills `judo-runtime:expression-syntax`, `judo-runtime:custom-functions`, names kept). Filtered. |
| `src/main/resources/claude/plugins/judo-expression/skills/custom-functions/SKILL.md` | Skill: add custom expression translators. Filtered. |
| `src/main/resources/claude/plugins/judo-expression/skills/expression-syntax/SKILL.md` | Skill: expression metamodel and bindings. Filtered. |
