# Database scripts (record only)

`legacy/` holds the hand-run MongoDB scripts from before Mongock (old PascalCase collection names,
`ddl.js` starts by dropping collections). They are kept for the record and are **not** used by the
application — schema changes are Mongock changeunits in `src/main/java/ai/qorva/core/migrations`.
They used to sit in `src/main/resources` and so shipped inside the jar; they no longer do.
