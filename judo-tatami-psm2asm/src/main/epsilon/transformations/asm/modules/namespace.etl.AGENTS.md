# `namespace.etl`

Maps the PSM namespace tree to the ASM package tree and hosts the shared documentation base rule. Exports `@abstract NamespaceToPackage` → `ModelToPackage` (`JUDOPSM!Model` → root `EPackage`) and `PackageToPackage` (nested `EPackage`), `ModelToPackageVersion` (emits version annotation on the model package), and `@abstract CreateDocumentationAnnotation` (`NamedElement` → documentation `EAnnotation`).
Every other module imports this file for `CreateDocumentationAnnotation`; changing its target type or id scheme breaks all extending rules.