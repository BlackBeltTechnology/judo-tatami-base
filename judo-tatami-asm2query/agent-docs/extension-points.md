# Expression Module Extension Points

## TransferObjectTypeBindingsCollector

**Purpose**: Main entry point for expression resolution

**Location**: `hu.blackbelt.judo.tatami.asm2query.TransferObjectTypeBindingsCollector (module `judo-tatami-asm2query`, moved from `judo-runtime-core-expression`)`

**Key Methods**:

```java
// Get expression tree for mapped transfer object
MappedTransferObjectTypeBindings getTransferObjectGraph(
    EClass mappedTransferObjectType);

// Get bindings for unmapped transfer object
UnmappedTransferObjectTypeBindings getTransferObjectBindings(
    EClass unmappedTransferObjectType);

// Entity getter expressions of the whole model (also persisted in QueryModelInfo)
Map<EClass, EntityTypeExpressions> getEntityTypeExpressionsMap();

// Static flags derived from the GETTER bindings
Boolean isStaticAttribute(EAttribute attribute);
Boolean isStaticReference(EReference reference);
```

---

## MappedTransferObjectTypeBindings

**Purpose**: Expression tree node for mapped transfer objects

**Key Properties**:

| Property | Type | Description |
|----------|------|-------------|
| `entityType` | `EClass` | Mapped entity type |
| `getterAttributeExpressions` | `Map<EAttribute, DataExpression>` | Getter expressions |
| `setterAttributeExpressions` | `Map<EAttribute, DataExpression>` | Setter expressions |
| `getterReferenceExpressions` | `Map<EReference, ReferenceExpression>` | Reference expressions |
| `references` | `Map<EReference, MappedTransferObjectTypeBindings>` | Nested bindings |
| `filter` | `LogicalExpression` | Filter expression |

---

## EntityTypeExpressions

**Purpose**: Entity-level expression cache

**Key Properties**:

| Property | Type | Description |
|----------|------|-------------|
| `getterExpressions` | `Map<EAttribute, DataExpression>` | Attribute expressions |
| `referenceExpressions` | `Map<EReference, ReferenceExpression>` | Reference expressions |

---

## UnmappedTransferObjectTypeBindings

**Purpose**: Expression holder for unmapped DTOs

**Key Properties**:

| Property | Type | Description |
|----------|------|-------------|
| `dataExpressions` | `Map<EAttribute, DataExpression>` | Data expressions |
| `navigationExpressions` | `Map<EReference, ReferenceExpression>` | Navigation expressions |

---

## Integration Points

### With QueryFactory

```java
// QueryFactory uses bindings to build queries
MappedTransferObjectTypeBindings bindings = 
    collector.getTransferObjectGraph(transferObjectType);

bindings.getGetterAttributeExpressions().forEach((attr, expr) -> {
    if (isDerived(attr)) {
        // Inline expression into SQL SELECT
        selectBuilder.addDerivedColumn(attr.getName(), expr);
    }
});
```

### With AttributeSelectorTranslator

```java
// Translator uses entity expressions
EntityTypeExpressions entityExprs = 
    collector.getEntityTypeExpressions(entityType);

DataExpression expr = entityExprs.getGetterExpressions().get(attribute);
if (expr != null) {
    // Translate expression to SQL
    String sql = translateToSql(expr);
}
```
