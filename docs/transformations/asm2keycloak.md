# ASM to Keycloak Transformation

## Overview

The ASM to Keycloak transformation converts actor types from the Abstract Semantic Model (ASM) into Keycloak configuration. This transformation generates realm definitions, client configurations, and attribute bindings for authentication and authorization.

**Module:** `judo-tatami-asm2keycloak`  
**Source Model:** ASM (hu.blackbelt.judo.meta.asm / Ecore)  
**Target Model:** Keycloak (hu.blackbelt.judo.meta.keycloak)

## ETL Files

| File | Purpose |
|------|---------|
| `asmToKeycloak.etl` | Main orchestrator, imports all modules |
| `keycloak/modules/realm.etl` | Realm creation from actor types |
| `keycloak/modules/client.etl` | Client and attribute binding creation |

## Transformation Rules

### Realm Rules

Realms are created dynamically in the `pre` block based on unique realm annotations:

| Operation | Source | Target | Description |
|-----------|--------|--------|-------------|
| Pre-block | ActorType (with realm annotation) | Realm | Create realm for each unique realm name |

Realm properties set during creation:
- `id`: Realm name
- `realm`: Realm name
- `enabled`: `true`
- `loginWithEmailAllowed`: `true`

### Client Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `CreateKeycloakClient` | EClass (ActorType with realm) | Client | Create Keycloak client for actor |
| `CreateKeycloakClientClaim` | EAttribute (on ActorType) | AttributeBinding | Create attribute bindings for claims |

## Guard Conditions

### Client Creation Guard
A client is created only when:
1. Source class is an ActorType (`asmUtils.isActorType(s)`)
2. Has a `realm` extension annotation
3. Realm value is non-empty

### Claim Binding Guard
An attribute binding is created only when:
1. Container class is an ActorType
2. Container has a `realm` extension annotation
3. Realm value is non-empty

## Client Properties

| Property | Value | Description |
|----------|-------|-------------|
| `name` | FQN with dots replaced by dashes | Client display name |
| `clientId` | Same as name | OAuth client ID |
| `enabled` | `true` | Client is active |
| `directAccessGrantsEnabled` | `true` | Allow direct access grants |
| `redirectUris` | `["*"]` | Allowed redirect URIs |
| `publicClient` | `true` | Public client (no secret) |
| `bearerOnly` | `false` | Not bearer-only |

## Attribute Bindings

Attribute bindings map actor attributes to Keycloak user attributes or claims:

| Claim Type | Attribute Name Mapping |
|------------|------------------------|
| `EMAIL` annotation | Maps to `email` |
| `USERNAME` annotation | Maps to `username` |
| Attribute named `email` | Maps to `_email` (prefixed to avoid collision) |
| Attribute named `username` | Maps to `_username` (prefixed to avoid collision) |
| Other attributes | Maps to attribute name directly |

## Extension Annotations

The transformation uses extension annotations on ASM elements:

| Annotation | Element | Description |
|------------|---------|-------------|
| `realm` | ActorType (EClass) | Keycloak realm name |
| `claim` | EAttribute | Claim type (`EMAIL`, `USERNAME`) |

## Generated Structure

```
Keycloak Model
└── Realm
    ├── id: String
    ├── realm: String
    ├── enabled: Boolean
    ├── loginWithEmailAllowed: Boolean
    └── clients: List<Client>
        └── Client
            ├── name: String
            ├── clientId: String
            ├── enabled: Boolean
            ├── directAccessGrantsEnabled: Boolean
            ├── redirectUris: List<String>
            ├── publicClient: Boolean
            ├── bearerOnly: Boolean
            └── attributeBindings: List<AttributeBinding>
                └── AttributeBinding
                    └── attributeName: String
```

## Usage Example

```java
// Create source ASM model with actor types
AsmModel asmModel = AsmModel.buildAsmModel().build();
// ... add actor types with realm annotations

// Create target Keycloak model
KeycloakModel keycloakModel = KeycloakModel.buildKeycloakModel().build();

// Execute transformation
Asm2KeycloakTransformationTrace trace = executeAsm2KeycloakTransformation(
    asm2KeycloakParameter()
        .asmModel(asmModel)
        .keycloakModel(keycloakModel)
);

// Access generated realms and clients
Collection<Realm> realms = keycloakModel.getKeycloakUtils().all(Realm.class);
Collection<Client> clients = keycloakModel.getKeycloakUtils().all(Client.class);
```

## ASM Actor Example

```java
// Create actor type with realm annotation
EClass actorType = EcoreFactory.eINSTANCE.createEClass();
actorType.setName("CustomerActor");

// Add realm annotation
EAnnotation realmAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
realmAnnotation.setSource("realm");
realmAnnotation.getDetails().put("value", "customer-realm");
actorType.getEAnnotations().add(realmAnnotation);

// Add email attribute with claim annotation
EAttribute emailAttr = EcoreFactory.eINSTANCE.createEAttribute();
emailAttr.setName("email");
EAnnotation claimAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();
claimAnnotation.setSource("claim");
claimAnnotation.getDetails().put("value", "EMAIL");
emailAttr.getEAnnotations().add(claimAnnotation);
actorType.getEStructuralFeatures().add(emailAttr);
```

## Zeta Implementation

The Zeta implementation uses Java classes with `@TransformRule` annotations:

```java
@TransformationContext(
    sourceModel = AsmModel.class,
    targetModel = KeycloakModel.class
)
public class Asm2KeycloakZetaTransformation {
    
    private final Set<String> createdRealms = new HashSet<>();
    
    @PreTransform
    public void createRealms(TransformationContext ctx) {
        asmUtils.getAllActorTypes().forEach(actor -> {
            Optional<String> realmOpt = asmUtils.getExtensionAnnotationValue(actor, "realm", false);
            if (realmOpt.isPresent() && !realmOpt.get().trim().isEmpty()) {
                String realmName = realmOpt.get().trim();
                if (createdRealms.add(realmName)) {
                    Realm realm = KeycloakFactory.eINSTANCE.createRealm();
                    realm.setId(realmName);
                    realm.setRealm(realmName);
                    realm.setEnabled(true);
                    realm.setLoginWithEmailAllowed(true);
                    keycloakModel.getResource().getContents().add(realm);
                }
            }
        });
    }
    
    @TransformRule(name = Asm2KeycloakRuleNames.CREATE_KEYCLOAK_CLIENT)
    public Client transformActorToClient(EClass actorType, TransformationContext ctx) {
        if (!asmUtils.isActorType(actorType)) {
            return null;
        }
        Optional<String> realmOpt = asmUtils.getExtensionAnnotationValue(actorType, "realm", false);
        if (!realmOpt.isPresent() || realmOpt.get().trim().isEmpty()) {
            return null;
        }
        
        String realmName = realmOpt.get().trim();
        Realm realm = findRealm(realmName);
        
        Client client = KeycloakFactory.eINSTANCE.createClient();
        client.setName(asmUtils.getClassifierFQName(actorType).replace(".", "-"));
        client.setClientId(client.getName());
        client.setEnabled(true);
        client.setDirectAccessGrantsEnabled(true);
        client.getRedirectUris().add("*");
        client.setPublicClient(true);
        client.setBearerOnly(false);
        
        realm.getClients().add(client);
        return client;
    }
    
    @TransformRule(name = Asm2KeycloakRuleNames.CREATE_KEYCLOAK_CLIENT_CLAIM)
    public AttributeBinding transformAttributeToBinding(EAttribute attr, TransformationContext ctx) {
        EClass container = attr.getEContainingClass();
        if (!asmUtils.isActorType(container)) {
            return null;
        }
        Optional<String> realmOpt = asmUtils.getExtensionAnnotationValue(container, "realm", false);
        if (!realmOpt.isPresent() || realmOpt.get().trim().isEmpty()) {
            return null;
        }
        
        AttributeBinding binding = KeycloakFactory.eINSTANCE.createAttributeBinding();
        
        Optional<String> claimType = asmUtils.getExtensionAnnotationValue(attr, "claim", false);
        if (claimType.isPresent() && "EMAIL".equals(claimType.get())) {
            binding.setAttributeName("email");
        } else if (claimType.isPresent() && "USERNAME".equals(claimType.get())) {
            binding.setAttributeName("username");
        } else if ("email".equals(attr.getName())) {
            binding.setAttributeName("_email");
        } else if ("username".equals(attr.getName())) {
            binding.setAttributeName("_username");
        } else {
            binding.setAttributeName(attr.getName());
        }
        
        Client client = ctx.resolve(container, Asm2KeycloakRuleNames.CREATE_KEYCLOAK_CLIENT);
        client.getAttributeBindings().add(binding);
        
        return binding;
    }
}
```

Rule name constants are defined in `Asm2KeycloakRuleNames`:

```java
public final class Asm2KeycloakRuleNames {
    public static final String CREATE_KEYCLOAK_CLIENT = "CreateKeycloakClient";
    public static final String CREATE_KEYCLOAK_CLIENT_CLAIM = "CreateKeycloakClientClaim";
}
```

## ETL to Zeta Rule Mapping

### File Structure Mapping

| ETL File | Zeta Class |
|----------|------------|
| `asmToKeycloak.etl` | `Asm2KeycloakZetaTransformation.java` |
| `keycloak/modules/realm.etl` | Inline in main transformation |
| `keycloak/modules/client.etl` | Inline in main transformation |

### Key Rule Mapping

| ETL Rule | Zeta Implementation | Notes |
|----------|---------------------|-------|
| `CreateRealm` | `@PreTransform createRealms()` | Pre-transformation phase |
| `CreateKeycloakClient` | `transformActorToClient()` | Guard: isActorType with realm |
| `CreateKeycloakClientClaim` | `transformAttributeToBinding()` | Claim type mapping |

### Implementation Notes

The ASM to Keycloak transformation has unique patterns:
- **Pre-transformation phase** - Realms created before rules execute via `@PreTransform`
- **State management** - `createdRealms` set tracks created realms to avoid duplicates
- **Claim mapping** - Special handling for EMAIL and USERNAME claim types

For detailed migration guidance, see the [ETL to Zeta Migration Guide](../migration/etl-to-zeta-migration.md).
