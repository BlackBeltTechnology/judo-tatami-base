# `ActorRules.java`

Port of actor half of `accesspoint.etl`/`actor.etl`. Emits `EClass` per actor via `createActorTypeClass` (`ActorType`) and `createMappedActorTypeClass` (`MappedActorType`), plus `EAnnotation`s `createActorTypeAnnotation`, `createRealmAnnotation`, `createDocumentationAnnotationForActorType`.
Guards `hasDocumentation` (non-null, non-empty `NamedElement.getDocumentation()`) and `hasRealm` (non-null, non-empty `AbstractActorType.getRealm()`).
Depends on `TransferObjectRules` having produced the transfer-object classes — registered last (phase 8) for that reason.