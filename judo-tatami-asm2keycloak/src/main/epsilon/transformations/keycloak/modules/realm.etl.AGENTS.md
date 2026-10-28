# `realm.etl`

`pre` block, not a rule: scans `asmUtils.getAllActorTypes()`, collects distinct `realm` extension annotation values, and instantiates one `KEYCLOAK!Realm` per value with `id` = `realm` = the annotation value, `enabled` and `loginWithEmailAllowed` true, `eResource` ID `Realm/<name>`.
Runs before every ETL rule, which is what lets `client.etl` assume the realm exists. Deduplicates via a `Set`, so two actors sharing a realm name yield one `Realm`.