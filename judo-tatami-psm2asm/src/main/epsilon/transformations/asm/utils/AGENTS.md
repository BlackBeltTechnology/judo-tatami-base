# judo-tatami-psm2asm/src/main/epsilon/transformations/asm/utils

EOL query operations shared by the PSM→ASM rule modules. Core idiom: each PSM type gets an `asmEquivalent()` operation that resolves the ASM element produced by one *named* ETL rule, so rule modules never spell the rule name inline. Renaming an ETL rule in `../modules/` requires the matching rename here or `asmEquivalent()` returns undefined.

| File | Purpose |
|---|---|
| `_importUtils.eol` | Import aggregator: the one file every `../modules/` rule module imports (`derived.eol`, `operation.eol`, `package.eol`, `transferObject.eol`, `type.eol`, `id.eol`) → see `_importUtils.eol.AGENTS.md` |
| `derived.eol` | Resolves ASM equivalents for derived/static members via `@cached asmEquivalent()` on `DataProperty`/`NavigationProperty`/`StaticData`/`StaticNavigation` → see `derived.eol.AGENTS.md` |
| `id.eol` | Bridges synthetic element ids to the EMF resource via `Any setId(id : String)`/`Any getId()` delegating to `eResource.setId/getId` → see `id.eol.AGENTS.md` |
| `operation.eol` | Resolves ASM operation equivalents and parameter roles via `@cached asmEquivalent()` plus `isInput()`/`isOutput()`/`isFault()` predicates on `Parameter` → see `operation.eol.AGENTS.md` |
| `package.eol` | Resolves the ASM package for a PSM namespace. Exports `@cached asmEquivalent()` on `Model` → `EPackage` (`ModelToPackage`) and `Package` → `EPackage` (`PackageToPackage`). `NamespaceToPackage` is `@abstract` in `../modules/namespace.etl` and deliberately has no equivalent operation here — resolving it directly returns nothing. |
| `transferObject.eol` | Resolves transfer-object ASM equivalents via `@cached asmEquivalent()`; classifies metadata/range types via `isMetadataType()`/`isGetRangeInputType()` → see `transferObject.eol.AGENTS.md` |
| `type.eol` | Resolves the ASM `EDataType` for a PSM `Primitive` via `@cached asmEquivalent()` dispatch with `CreateCustomType` fallback → see `type.eol.AGENTS.md` |
