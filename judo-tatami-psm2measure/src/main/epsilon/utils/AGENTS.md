# AGENTS.md — `judo-tatami-psm2measure/src/main/epsilon/utils`

Common EOL utility scripts providing EMF resource identifier operations for PSM to measure transformations.

| File | Purpose |
| --- | --- |
| `_importAll.eol` | Aggregation import script. Imports `id.eol` to expose EMF identifier operations across transformation scripts. |
| `id.eol` | Extends `Any` with EMF resource identifier accessors. Exports `setId(id: String)` and `getId() : String` which delegate to `self.eResource.setId` and `getId`. Requires elements to be attached to an `eResource`. |
