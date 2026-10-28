# AGENTS.md — `judo-tatami-psm2asm/src/test/resources`

Test configuration properties specifying external model paths and search locations for PSM-to-ASM test suites.

| File | Purpose |
| --- | --- |
| `external-model-tests.properties` | Configures external model test execution mapping model names to paths and optional parameters (`dialect`, `warmup`, `iterations`). Defines `rackinspect` path expecting `<model-name>-psm.model`. |
| `model-search-directories.properties` | Defines comma-separated `model.search.directories` relative paths scanned for external test models; missing directories are silently skipped at runtime. |
