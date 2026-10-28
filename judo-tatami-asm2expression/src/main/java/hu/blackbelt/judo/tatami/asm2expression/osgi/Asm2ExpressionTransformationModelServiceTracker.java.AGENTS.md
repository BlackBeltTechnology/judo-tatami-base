# `Asm2ExpressionTransformationModelServiceTracker.java`

Extends `AbstractModelPairTracker<AsmModel, MeasureModel>`; `@Component(configurationPolicy = REQUIRE)`.
Waits for an `AsmModel` + `MeasureModel` pair, calls injected `Asm2ExpressionTranformationSerivce.install`, registers resulting `ExpressionModel` in bundle context under `expressionModel.toDictionary()`.
Exports `activate(ComponentContext)`, `deactivate()`, `install(AsmModel, MeasureModel)`, `uninstall(...)`, `getModelClass1/2()`, `getModel1/2NameExtractorFunction()`. Keys registrations by `asmModel.getName()` — a second model with the same name is refused, not replaced. Requires component configuration present, else DS never activates it.