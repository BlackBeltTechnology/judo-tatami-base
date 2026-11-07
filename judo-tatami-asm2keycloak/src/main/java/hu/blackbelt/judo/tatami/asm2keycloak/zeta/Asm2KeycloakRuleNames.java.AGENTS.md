# `Asm2KeycloakRuleNames.java`

Final, non-instantiable holder of the rule-name string constants shared by Zeta rules and trace consumers: `CREATE_REALM = "CreateRealm"`, `CREATE_KEYCLOAK_CLIENT = "CreateKeycloakClient"`, `CREATE_KEYCLOAK_CLIENT_CLAIM = "CreateKeycloakClientClaim"`.
Values must stay byte-identical to the ETL rule names in `realm.etl`/`client.etl`, because trace element IDs embed them — renaming a constant silently orphans existing traces.