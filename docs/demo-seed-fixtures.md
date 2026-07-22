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

- `cvs.json`       — JSON array of ~20 `CVDTO` objects
- `job-posts.json` — JSON array of ~5 `JobPostDTO` objects

Full matrix = 7 segments × 7 languages = 49 folders / 98 files.

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
