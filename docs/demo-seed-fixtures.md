# Demo sample-data fixtures (S3)

`DemoSeedService` seeds a new demo tenant with sample CVs and job posts fetched from S3.
Fixture **content** is authored separately from the code; this document is the contract the
loader expects.

## S3 layout

```
s3://<bucket>/<prefix>/<version>/<recruitment-slug>/<lang>/{cvs.json,job-posts.json}
```

- `<bucket>`  — `qorva.demo-seed.bucket` (falls back to `aws.s3.bucket-name` when blank)
- `<prefix>`  — `qorva.demo-seed.prefix` (default `demo-seed`)
- `<version>` — `qorva.demo-seed.version` (default `v1`; bump for non-breaking fixture updates)
- `<recruitment-slug>` — one of the `RecruitmentTypeEnum` slugs (below)
- `<lang>` — base language code: `en`, `fr`, `de`, `es`, `it`, `nl`, `pt`

### Recruitment slugs

| Enum value                | Slug                        |
|---------------------------|-----------------------------|
| `TECH_IT`                 | `tech-it`                   |
| `ENGINEERING`             | `engineering`               |
| `FINANCE_ACCOUNTING`      | `finance-accounting`        |
| `SALES_MARKETING`         | `sales-marketing`           |
| `HEALTHCARE_LIFESCIENCES` | `healthcare-life-sciences`  |
| `EXECUTIVE_MANAGEMENT`    | `executive-management`      |
| `GENERALIST`              | `generalist`                |

## Volume (target)

- `cvs.json`       — JSON array of 22 `CVDTO` objects (v2; v1 had 20)
- `job-posts.json` — JSON array of ~5 `JobPostDTO` objects

Full matrix = 7 segments × 7 languages = 49 folders / 98 files.

## Relative date tokens (v2+)

Fixture JSON may carry relative date tokens, resolved by `DemoSeedService` at seed time so
fixture freshness never decays as the files age:

| Token    | Resolves to                      | Example (seeding in 2026-07) |
|----------|----------------------------------|------------------------------|
| `@M-n@`  | the month `n` months ago, `yyyy-MM` | `@M-10@` → `2025-09`      |
| `@Y-n@`  | the year `n` years ago, `yyyy`      | `@Y-4@`  → `2022`         |

Use `@M-n@` in `workExperience.from/to` and `@Y-n@` in `education.year` /
`certifications.year`. Literal dates still work but will age.

## v2 imperfection profile (Library Quality demo)

v2 fixtures are generated from v1 by `demo-seed/generate_v2.py` (do not hand-edit v2 —
change the generator and re-run). Each 22-CV file applies the same per-index persona
profile so demo users see every issue the Library Quality report can raise: missing
contact/enrichment fields, outdated and undateable CVs, two duplicate pairs (same email /
same phone), one unparseable "bad parse" CV, and low AI-confidence parses. Predicted
report per fresh demo tenant: completeness 92, freshness 38, uniqueness 91,
AI confidence 82 → **overall 79**.

## Fixture rules

- **No `embedding`** field — MongoDB Atlas auto-generates embeddings on insert.
- **No `id`, `tenantId`, `applicantNumber`, `jobReference`** — assigned by the services at seed time
  (`applicantNumber`/`jobReference` are always regenerated, so any value you include is ignored).
- Documents must satisfy the strict `cvs` / `job_posts` collection validators (required fields present,
  no undeclared fields).

## Rollout strategy

The loader falls back to the `en` fixture set when a `(segment, language)` set is missing.
Author **`en` for all 7 segments first** — every language then works immediately (non-`en` users see
English sample data). Backfill the other six languages per segment over time; each uploaded file
upgrades that locale with **no code change**.

## Manifest (optional, for authors)

A `manifest.json` at `<prefix>/<version>/manifest.json` is recommended for humans to track coverage.
It is not consumed by the loader. Example:

```json
{
  "version": "v1",
  "languages": ["en", "fr", "de", "es", "it", "nl", "pt"],
  "segments": {
    "tech-it":      { "en": { "cvs": 20, "jobPosts": 5 } },
    "generalist":   { "en": { "cvs": 20, "jobPosts": 5 } }
  }
}
```
