# `Psm2AsmRuleNames.java`

641 lines, `hu.blackbelt.judo.tatami.psm2asm.zeta`. `public final class Psm2AsmRuleNames` with a private constructor — constant holder only, no behaviour.

## What it holds

179 `public static final String` constants, one per PSM→ASM rule name. Each constant's value is the **exact ETL rule name** from `../../../../../../../epsilon/transformations/asm/modules/*.etl`, e.g. `NAMESPACE_TO_PACKAGE = "NamespaceToPackage"`, `MODEL_TO_PACKAGE = "ModelToPackage"`, `MODEL_TO_PACKAGE_VERSION = "ModelToPackageVersion"`, `PACKAGE_TO_PACKAGE = "PackageToPackage"`, `CREATE_DOCUMENTATION_ANNOTATION = "CreateDocumentationAnnotation"`, `CREATE_ENUMERATION = "CreateEnumeration"`.

## Sections (each names its ETL source module)

`Namespace Rules (namespace.etl)`, `Type Rules (type.etl)`, `Data Rules (data.etl)`, `Derived Rules (derived.etl)`, `Operation Rules (operation.etl)`, `Transfer Object Rules (transferObject.etl)`, `Actor Rules (actor.etl)`, `Static Rules (static.etl)`, plus Zeta-only groups with no single ETL origin: `Additional Operation Rules`, `Split TransferOperation Rules (Type-optimized)`, `Documentation Annotation Rules`, `Primitive Accessor Constraint Rules`, `Navigation and Accessor Rules`, `Additional Static Binding Rules`, `Additional Operation Stateful Rules`, `Additional Operation Parameter Rules`, `BoundTransferOperation Annotation Rules`.

## Contracts

- Constant **values** are the trace/equivalent lookup keys used by `ctx.equivalent(source, RULE_NAME)` and `ctx.executeParentRule(RULE_NAME, source)`. Changing a value silently breaks every equivalent lookup that used it — lookups return null rather than failing.
- Values must stay byte-identical to the ETL rule names, including upstream misspellings, so the ETL and Zeta engines produce comparable traces.
- Rule classes in `rules/` import these statically (`import static ...Psm2AsmRuleNames.*`). Never inline a rule name string in a rule class.
- The `Split TransferOperation Rules (Type-optimized)` group has no ETL counterpart: those names exist only in the Zeta engine, so an ETL/Zeta trace diff will show them as Zeta-only entries.
