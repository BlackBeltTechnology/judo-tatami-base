# judo-tatami-psm2measure/src/main/epsilon/operations/psm

PSM-side level of the EOL operation import chain: `../_importAll.eol` → this file → `measure/_importMeasure.eol` → `measure/measure.eol`.

| File | Purpose |
|---|---|
| `_importPsm.eol` | PSM level of the import chain. Imports `measure/_importMeasure.eol` and nothing else — the only PSM operation family in this module is the measure one. A new PSM operation family belongs here as an extra import, keeping `../_importAll.eol` unchanged. |
