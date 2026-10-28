# `DerivedRules.java`

Port of `derived.etl`. Emits `EAttribute` via `createDataPropertyForDerivedAttribute` and `EReference` via `createNavigationPropertyForDerivedReference`, plus JQL expression annotations `addPrimitiveAccessorExpressionAnnotation` / `addReferenceAccessorExpressionAnnotation`, constraint annotations `addStringPrimitiveAccessorConstraints`, `addNumericPrimitiveAccessorConstraints`, `addCustomPrimitiveAccessorConstraints`, and `createDocumentationAnnotationForDataProperty`.
Guards `isPrimitiveDataProperty`, `hasDocumentation`, `hasGetterExpression`, `hasReferenceGetterExpression`, `isStringPrimitiveAccessor`, `isNumericPrimitiveAccessor`, `isCustomTypePrimitiveAccessor`.
Three accessor-type guards must partition primitive accessors; overlap emits duplicate `constraints` annotations on one member.
Static accessors handled by `StaticRules`, not here.