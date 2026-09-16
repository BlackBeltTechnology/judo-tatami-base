# `static.etl`

Maps `JUDOPSM!StaticData` and `JUDOPSM!StaticNavigation` to synthetic unmapped transfer-object `EClass`es holding a single query member. `CreateUnmappedTransferObjectForStaticData` + `CreateStaticQueryAttribute` emit class + `EAttribute`; `CreateUnmappedTransferObjectForStaticNavigation` + `CreateStaticQueryNavigation` emit class + `EReference`.
Supporting annotation rules mark transfer-object type, static query, parameterized transfer members, and the data/navigation expression bindings (`CreateDataReferenceBindingForStaticData`, `CreateNavigationReferenceBindingForStaticNavigation`). All rules `@greedy`.
Static accessors are excluded from `derived.etl` by its `not s.isKindOf(JUDOPSM!StaticData)` guard — the two modules must stay in sync.