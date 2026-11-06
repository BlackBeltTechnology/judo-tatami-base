# judo-tatami-asm2keycloak-etl/src/test/java/hu/blackbelt/judo/tatami/asm2keycloak

ASM→Keycloak ETL vs Zeta equivalence test (moved from `judo-tatami-asm2keycloak`). ETL path imports `hu.blackbelt.judo.tatami.asm2keycloak.etl.Asm2Keycloak`.

| File | Purpose |
|---|---|
| `Asm2KeycloakTest.java` | Tests ASM to Keycloak transformation across ETL and Zeta engines. Exports `testAsm2KeycloakTransformation()`, `testRealmCreation()`, `testEtlAndZetaEquivalence()`. Compares generated Keycloak models using `ModelComparator` across `TransformationMode` variants. |
