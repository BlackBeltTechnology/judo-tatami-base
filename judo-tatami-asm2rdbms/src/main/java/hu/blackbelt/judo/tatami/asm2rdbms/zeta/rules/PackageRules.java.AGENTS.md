# `PackageRules.java`

Maps root ASM `EPackage` to the RDBMS model root; port of `package.etl`. Exports guard `isRootPackage` (true only when `getESuperPackage() == null`) and rules `rootPackegeToModel` (emits `RdbmsModel`), `rootPackegeToConfiguration` (emits `RdbmsConfiguration` and attaches it to the model's `configuration`).
Configuration rule reads the default equivalent of the same `EPackage`, so it requires `rootPackegeToModel` to run first. Rule names keep the upstream ETL misspelling `rootPackege` — do not rename, `Asm2RdbmsRuleNames` constants and ETL parity depend on it.