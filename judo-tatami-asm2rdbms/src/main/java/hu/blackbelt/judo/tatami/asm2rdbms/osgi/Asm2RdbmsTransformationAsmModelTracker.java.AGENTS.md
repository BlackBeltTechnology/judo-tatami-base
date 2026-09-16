# `Asm2RdbmsTransformationAsmModelTracker.java`

Extends `AbstractModelTracker<AsmModel>`; `@Component(immediate = true, configurationPolicy = REQUIRE)`. For each tracked `AsmModel` calls injected `Asm2RdbmsTransformationSerivce.install(asmModel, "hsqldb")` and registers the resulting `RdbmsModel` as an OSGi service under `rdbmsModel.toDictionary()`.

Exports `activate(ComponentContext)`, `deactivate()`, `install(AsmModel)`, `uninstall(AsmModel)`, `getModelClass()`.

Dialect is HARD-CODED to `hsqldb` (marked TODO) — this component cannot produce an Oracle schema. Keys registrations by `asmModel.getName()`; a duplicate name is logged and ignored, not replaced. A transformation failure is swallowed into an error log, so the absence of an `RdbmsModel` service is the only signal. `deactivate()` unregisters every registration.