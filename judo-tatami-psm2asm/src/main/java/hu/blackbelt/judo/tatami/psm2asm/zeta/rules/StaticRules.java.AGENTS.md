# `StaticRules.java`

Port of `static.etl`. Exports `createUnmappedTransferObjectForStaticData` (`StaticData` → `EClass`) and `createUnmappedTransferObjectForStaticNavigation` (`StaticNavigation` → `EClass`), both `@Greedy`, producing synthetic unmapped transfer objects holding a single static query member.
Sources deliberately excluded from `DerivedRules` — the two classes partition PSM accessors and must be changed together.