# `derived.eol`

Resolves ASM equivalents for PSM derived and static members. Exports `@cached asmEquivalent()` on `DataProperty` → `EAttribute` (`CreateDataProperty`), `NavigationProperty` → `EReference` (`CreateNavigationProperty`), `StaticData` → `EAttribute` (`CreateStaticQueryAttribute`), `StaticNavigation` → `EReference` (`CreateStaticQueryNavigation`).
Static members resolve to rules in `../modules/static.etl`, derived ones to `../modules/derived.etl` — the split must match `derived.etl`'s `not s.isKindOf(JUDOPSM!StaticData)` guards.