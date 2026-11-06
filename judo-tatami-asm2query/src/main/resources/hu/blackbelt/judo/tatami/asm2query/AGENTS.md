# AGENTS.md — `judo-tatami-asm2query/src/main/resources/hu/blackbelt/judo/tatami/asm2query`

| File | Purpose |
| --- | --- |
| `asm2query-generator.properties` | Build-filtered (`maven-resources-plugin` via POM `<resources>`) generator identity of this bundle: `version=${project.version}`, `buildTimestamp=${timestamp}`. Owner-only stamp for `QueryModelInfo.generator`; read by `QueryModelPersistence.generatorIdentity()` through `QueryModelPersistence.class.getResourceAsStream("asm2query-generator.properties")` so Maven, Spring Boot and OSGi classloaders each read their own copy. Missing / unreadable counts as a mismatch. See change: persisted-query-model. |
