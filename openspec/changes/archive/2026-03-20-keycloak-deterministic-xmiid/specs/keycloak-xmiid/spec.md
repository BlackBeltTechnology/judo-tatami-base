## ADDED Requirements

### Requirement: Realm elements have deterministic XMI IDs
Both ETL and Zeta asm2keycloak transformations SHALL assign the XMI ID `Realm/{realmName}` to each created Realm element, where `{realmName}` is the realm name string.

#### Scenario: Realm gets deterministic ID
- **WHEN** a Realm is created for realm name `DEFAULT`
- **THEN** the Realm element has XMI resource ID `Realm/DEFAULT`

#### Scenario: ETL and Zeta produce identical Realm IDs
- **WHEN** the same ASM model is transformed by both ETL and Zeta
- **THEN** both produce Realm elements with identical XMI IDs

### Requirement: Client elements have deterministic XMI IDs
Both ETL and Zeta asm2keycloak transformations SHALL assign the XMI ID `(asm/{sourceId})/CreateKeycloakClient` to each created Client element, where `{sourceId}` is the XMI ID of the source ASM EClass.

#### Scenario: Client gets source-based ID
- **WHEN** an ASM actor type EClass with XMI ID `_abc123` is transformed to a Client
- **THEN** the Client element has XMI resource ID `(asm/_abc123)/CreateKeycloakClient`

#### Scenario: ETL and Zeta produce identical Client IDs
- **WHEN** the same ASM model is transformed by both ETL and Zeta
- **THEN** both produce Client elements with identical XMI IDs

### Requirement: AttributeBinding elements have deterministic XMI IDs
Both ETL and Zeta asm2keycloak transformations SHALL assign the XMI ID `(asm/{sourceId})/CreateKeycloakClientClaim` to each created AttributeBinding element, where `{sourceId}` is the XMI ID of the source ASM EAttribute.

#### Scenario: AttributeBinding gets source-based ID
- **WHEN** an ASM actor attribute EAttribute with XMI ID `_def456` is transformed to an AttributeBinding
- **THEN** the AttributeBinding element has XMI resource ID `(asm/_def456)/CreateKeycloakClientClaim`

#### Scenario: ETL and Zeta produce identical AttributeBinding IDs
- **WHEN** the same ASM model is transformed by both ETL and Zeta
- **THEN** both produce AttributeBinding elements with identical XMI IDs
