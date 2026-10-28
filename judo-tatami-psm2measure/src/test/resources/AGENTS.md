# AGENTS.md — `judo-tatami-psm2measure/src/test/resources`

Test configuration properties defining external PSM model paths and search directories for PSM-to-MEASURE comparison tests.

| File | Purpose |
| --- | --- |
| `external-model-tests.properties` | Configures external model test suites mapping model names to paths with optional execution parameters (`warmup`, `iterations`); references `rackinspect` path expecting `<model-name>-psm.model`. |
| `model-search-directories.properties` | Defines comma-separated relative search directories scanned for external test models; missing directories are silently skipped. |
