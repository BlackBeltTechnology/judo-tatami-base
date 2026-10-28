# judo-tatami-psm2measure/src/main/epsilon/operations

Root of the EOL query-operation tree used by the PSM→measure ETL scripts. One import file per level; the actual operations live in `psm/measure/measure.eol`.

| File | Purpose |
|---|---|
| `_importAll.eol` | Single entry point for the whole EOL operation tree. Imports `psm/_importPsm.eol` and nothing else. ETL modules import this file rather than reaching into the tree; adding an operation family means extending the chain here, not importing a leaf directly. |
