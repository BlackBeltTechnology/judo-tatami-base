# AGENTS.md — asm2keycloak/zeta

Zeta-engine port of the ASM→Keycloak ETL rules — the default engine when `TransformationMode` is ZETA.

| File | Purpose |
|---|---|
| `Asm2KeycloakRuleNames.java` | Holds `CREATE_REALM`/`CREATE_KEYCLOAK_CLIENT`/`CREATE_KEYCLOAK_CLIENT_CLAIM` rule-name constants → see `Asm2KeycloakRuleNames.java.AGENTS.md` |
| `Asm2KeycloakZetaTransformation.java` | `execute()` lowers via Zeta registry/executor, registers `RealmRules`→`ClientRules` → see `Asm2KeycloakZetaTransformation.java.AGENTS.md` |
| `CLAUDE.md` | `!! GENERATED` — `claude-mem` auto-writes the `<claude-mem-context>` block. Currently holds no activity. Not a source file; hand edits inside the tags are overwritten. |
