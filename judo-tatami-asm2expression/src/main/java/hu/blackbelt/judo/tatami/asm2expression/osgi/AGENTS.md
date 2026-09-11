# AGENTS.md — asm2expression/osgi

OSGi Declarative Services wrapper around the ASM→Expression transformation.

| File | Purpose |
|---|---|
| `Asm2ExpressionTranformationSerivce.java` | Declares `@Component(service = Asm2ExpressionTranformationSerivce.class, immediate = true)`. Exports `install(AsmModel, MeasureModel) : ExpressionModel`. Builds empty `ExpressionModel` named/versioned after `asmModel`, URI `expression:<name>.model`, then runs `Asm2Expression.executeAsm2Expression` with `Asm2ExpressionParameter`. Callers get transformation log via `StringBuilderLogger`; on failure log dumps red and exception rethrows, so returned model is never partially transformed. Name misspells "Transformation" — bundle wiring depends on that literal, do not rename. |
| `Asm2ExpressionTransformationModelServiceTracker.java` | Extends `AbstractModelPairTracker<AsmModel, MeasureModel>`; `@Component(configurationPolicy = REQUIRE)`. Waits for an `AsmModel` + `MeasureModel` pair, calls injected `Asm2ExpressionTranformationSerivce.install`, registers resulting `ExpressionModel` in bundle context under `expressionModel.toDictionary()`. Exports `activate(ComponentContext)`, `deactivate()`, `install(AsmModel, MeasureModel)`, `uninstall(...)`, `getModelClass1/2()`, `getModel1/2NameExtractorFunction()`. Keys registrations by `asmModel.getName()` — a second model with same name is refused, not replaced. Requires component configuration present, else DS never activates it. |
