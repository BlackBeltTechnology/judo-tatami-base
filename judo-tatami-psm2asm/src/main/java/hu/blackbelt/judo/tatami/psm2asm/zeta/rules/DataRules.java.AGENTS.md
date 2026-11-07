# `DataRules.java`

Port of `data.etl`. Emits `EClass` per `EntityType` (`createEntityClass`, carrying `abstract` and super types), `EAttribute` per `Attribute` (`createAttribute`), `EReference` per relation (`createAssociationEndRelation`, `createContainmentRelation`), sequence annotations (`createNamespaceSequence`, `createEntitySequence`), and unmapped-default-only markers (`addUnmappedDefaultOnlyAttributeAnnotation`, `addUnmappedDefaultOnlyReferenceAnnotation`).
Guard `isPrimitiveAttribute` filters attributes whose type is a PSM `Primitive`.
`createAssociationEndRelation` leaves `EOpposite` unset — `Psm2AsmZetaTransformation.postProcess` step 2 pairs partners afterwards, so rules must not read `getEOpposite()` during transform.