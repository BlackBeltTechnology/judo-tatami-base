# `id.eol`

Bridges synthetic element ids to the EMF resource. Exports `Any setId(id : String)` and `Any getId() : String`, both delegating to `self.eResource.setId/getId`.
Every ETL rule builds target ids as `"(psm/" + s.getId() + ")/<RuleSuffix>"` through these operations; the underlying resource must support id assignment (`XMIResource`-style) or `setId` fails.
Declared on `Any`, so calling `getId()` on an element never registered returns undefined rather than raising.