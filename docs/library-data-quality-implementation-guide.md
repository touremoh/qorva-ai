# Library Data Quality — Full Implementation Guide

Status: **implemented (Parts A–D, 2026-07-25)** — Part E (remediation actions) remains future work
Scope: backend (`qorva-ai`) + frontend (`qorva-ai-app`)
Branch context: builds on `integrate-demo-mode` work (demo seeding, S3 logo storage).

This guide consolidates the whole feature discussion:

- **Part A** — Foundation: replace the dead `attachment` Binary field with S3 document storage.
- **Part B** — Foundation: raw extracted text + content-based freshness (`contentDate`).
- **Part C** — Library Quality backend (report endpoint + drill-down issues endpoint).
- **Part D** — Library Quality frontend (dedicated section, Option B).
- **Part E** — Remediation actions roadmap (how the recruiter improves each dimension).
- **Part F** — Rollout order, verification, open decisions.

---

## Feature vision

Give the recruiter an at-a-glance health score of their CV library across four dimensions,
each with drill-down lists and one-click remediation:

| Dimension | Question it answers | Primary signal |
|---|---|---|
| Completeness | "Can I contact and evaluate these candidates?" | field presence (email, phone, skills, …) |
| Freshness | "Is this data still true?" | `contentDate` (see Part B) — NOT `lastUpdatedAt` |
| Uniqueness | "Am I double-counting people?" | existing email/phone duplicate aggregations |
| Parse confidence | "Did the AI extraction work?" | `clusterConfidenceScore` + structural anomalies |

Design principles agreed:

- **Score with a breakdown, distributions not averages** ("70 excellent / 40 fair / 12 poor").
- **Every finding has a button** — a quality score without remediation is a dead widget.
- **Deterministic and cheap** — pure Mongo aggregations, no LLM calls, compute on read (v1).
- **Honest freshness** — content-derived, so a bulk import of 3-year-old CVs scores as outdated
  on day one; "unknown" is its own bucket, excluded from the score denominator.
- Pre-customer advantage: **no backfill migrations needed** — schema changes freely, sample
  data gets re-seeded through the new pipeline.

---

# Part A — CV attachment: remove Binary, store originals in S3

## A.0 Current state (verified)

- `attachment` (`org.bson.types.Binary`) is declared in `CV.java:58` and `CVDTO.java:45`,
  ignored in `OpenAIResultMapper`, and **written by nothing**. Removing it breaks nothing.
- S3 infra already exists and is reused as-is: `S3Client` bean (`QorvaConfig`),
  `S3Properties` (`aws.s3.bucket-name/region` in local/tst/prd yml),
  `S3StorageService` (tenant logos), `DemoSeedService` (fixture reads).

## A.1 New embedded type

`src/main/java/ai/qorva/core/dto/common/AttachmentInfo.java` — same Lombok style as
`SalaryExpectation`:

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AttachmentInfo implements Serializable {
    private String s3Key;
    private String fileName;      // original upload filename (future download UX)
    private String contentType;
    private Long sizeBytes;
}
```

## A.2 Entity + DTO changes

- `CV.java:58` → `private AttachmentInfo attachment;` (drop `Binary` import).
- `CVDTO.java:45` → same.
- Mappers: no change — `CVMapper` maps by name/type; `OpenAIResultMapper`'s
  `@Mapping(target = "attachment", ignore = true)` stays valid.
- Frontend impact: none (field was always `null`; becomes a small metadata object).

## A.3 `S3StorageService` additions

Key layout, consistent with logos (`tenants/{tenantId}/logos/…`):

```
tenants/{tenantId}/cvs/{uuid}.{ext}
```

UUID-based key; original filename lives in metadata only (no sanitization pitfalls).

```java
public AttachmentInfo uploadCvDocument(String tenantId, MultipartFile file) throws QorvaException
    // ext from original filename; fallback by content type:
    //   application/pdf → pdf, application/msword → doc,
    //   application/vnd.openxmlformats-officedocument.wordprocessingml.document → docx, else "bin"
    // putObject with contentType; returns populated AttachmentInfo
    // throws QorvaException(CV_ATTACHMENT_UPLOAD_FAILED) on IO/S3 error

public void deleteObject(String key)
    // best-effort: try/catch, WARN on failure, never throws

public void deleteCvDocumentsForTenant(String tenantId)
    // ListObjectsV2 paginated over "tenants/{tenantId}/cvs/" + batched DeleteObjects
    // best-effort, WARN on failure
```

New error code in `QorvaErrorCodes` (cv section):
`CV_ATTACHMENT_UPLOAD_FAILED = "error.cv.attachment_upload_failed"`
+ one entry per bundle in `src/main/resources/messages/`.

## A.4 `CVService` upload pipeline

Inject `S3StorageService`. Restructure so persistence happens **last**
(today `extractCVData` calls `createOne` internally):

```
processFile(file, tenantId):
    fileContent   = read file                                  // unchanged
    dto           = extractCVData(fileContent, tenantId)       // LLM extract + map — NO createOne inside anymore
    attachmentInfo = s3StorageService.uploadCvDocument(...)    // failure policy: A.6
    dto.setAttachment(attachmentInfo)
    try   { return createOne(dto) }
    catch { s3StorageService.deleteObject(key); rethrow }      // no orphaned S3 objects
```

Ordering rationale:
- LLM extraction first → a failed/unreadable CV never uploads to S3 (no orphans).
- S3 upload before `createOne` → a persisted CV always references an object that exists.
- Usage metering (`SCREENING_ACTIONS`) stays where it is (after successful extraction).

## A.5 Deletion lifecycle

- **Single delete** (`CVService`):
  - Override `preProcessDeleteOneById`: call `super` (tenant checks), then stash the
    attachment `s3Key` in a `ThreadLocal<String>` (same pattern as
    `existingDTOForUpdate` in `AbstractQorvaService`).
  - Extend `postProcessDeleteOneById` (already deletes reports + chats): after DB delete,
    `deleteObject(stashedKey)` if present; clear ThreadLocal in `finally`.
- **Tenant purge** (`DemoDataPurgeService.java:52`, demo→paid upgrade): inject
  `S3StorageService`, add `deleteCvDocumentsForTenant(tenantId)` wrapped in the existing
  `safeDelete`-style swallowing.
- Demo-seeded CVs (JSON fixtures via `createOne`, no files — `DemoSeedService.java:88`)
  simply have `attachment: null`. Valid, no special handling.

## A.6 Decision point — S3 failure policy

- **(A) Best-effort — recommended:** on S3 failure, WARN and persist the CV *without*
  attachment. Core value (parsed CV) survives an S3 outage; quality feature later flags
  "original document missing". Matches codebase resilience style
  (`incrementUsageSilently`, demo seeding never throws).
- **(B) Fail-hard:** the file fails processing entirely (lands in per-file error handling
  in `upload()`). Guarantees every CV has its original, at the cost of losing the parse.

Guide assumes **A** unless overridden.

## A.7 Migration (Mongock)

Follow the `V2026071902UpdateTenantsCollection` pattern:

**`V2026072401UpdateCvsCollection.java`** —
`@ChangeUnit(id="V20260724_01__UpdateCvsCollection", order="20260724_01", author="qorva")`:

1. `updateMany({attachment: {$type: "binData"}}, {$unset: {attachment: ""}})`
   — clears any legacy binary values so the new validator can't reject docs
   (no-op on current data, but correct).
2. `updateCollection(db, "20260724_01__update_cvs_collection.json", …)`.
3. `@RollbackExecution`: `collMod` validator off (same as existing migrations).

**`20260724_01__update_cvs_collection.json`** — full copy of the current `collMod`
validator from `20260514_03__create_cvs_collection.json` (405 lines), with the
`attachment` block (lines 280–283) replaced by:

```json
"attachment": {
  "bsonType": "object",
  "description": "reference to the original CV file stored in S3",
  "properties": {
    "s3Key":       { "bsonType": "string" },
    "fileName":    { "bsonType": "string" },
    "contentType": { "bsonType": "string" },
    "sizeBytes":   { "bsonType": "long" }
  }
}
```

(Part B adds two more fields to this same JSON — write the migration once, covering both.)

Also update the reference copies in `sql/scripts/ddl.js:668` and
`sql/scripts/updates.js:433` so the doc scripts stay truthful.

## A.8 Infra prerequisite (ops, not code)

App Runner instance role + local AWS profile need, on the existing bucket:

- `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` on `tenants/*`
- `s3:ListBucket` on the bucket (prefix purge)

Logos already use Put/Get, so likely only **DeleteObject + ListBucket** are new.

---

# Part B — Raw text + content-based freshness

## B.1 Why `lastUpdatedAt` is the wrong freshness signal

`lastUpdatedAt` measures *record* recency. A recruiter bulk-importing 5,000 CVs of which
40% are 3+ years old would score 100% fresh on day one — exactly backwards. Freshness must
measure *content* recency, derived from evidence:

1. **CV's own timeline (strongest, already parsed):** the latest date mentioned in
   `workExperience.from/to`, education years, certification years is a hard ceiling on
   freshness. Latest role ended 2023 with nothing marked current → content is at best
   from 2023. Roles marked "present" are ambiguous → fall through to signal 2.
2. **Document metadata (cheap, good):** PDF `CreationDate`/`ModDate` (PDFBox) and Word
   core properties (POI), read at upload time from the in-memory file. Resolves most
   "2019–present" ambiguity. Heuristic, not tamper-proof — fine in combination.
3. **Human/candidate confirmation (trump card):** a separate `dataVerifiedAt`-style
   signal set ONLY by acts that verify data (recruiter "confirmed current" click,
   candidate self-update, newer CV replacing an old one). Deliberately not
   `lastUpdatedAt` — editing a tag must never make a CV look fresh.
   *(The verification actions themselves are Part E scope; the field design here just
   leaves room for them.)*

## B.2 New CV fields

Add to `CV` + `CVDTO` (+ validator JSON in the same A.7 migration):

```java
private String rawText;            // extracted text produced by the file readers, pre-LLM
private Instant contentDate;       // best evidence of when the CV content was current
private String contentDateSource;  // "WORK_HISTORY" | "DOC_METADATA" | "VERIFIED" | "UNKNOWN"
```

Why `rawText`: a few KB per CV, enables **Re-analyze** (re-run LLM extraction with a
better prompt/model without the original file), a "source text vs parsed fields" verify
view, and second attempts on low-confidence parses. The S3 original (Part A) additionally
enables download/viewing of the actual document.

## B.3 Computation at upload

In the upload pipeline (`processFile` / a small helper called before `createOne`):

- File readers additionally return the document metadata date
  (extend `QorvaFileReader` contract or add a metadata probe next to it —
  PDFBox `PDDocumentInformation`, POI `CoreProperties`).
- After LLM extraction, compute deterministically in Java (no LLM cost):

```
contentDate = max(latest parseable date in workExperience/education/certifications,
                  document metadata date)
contentDateSource = whichever source won; UNKNOWN if neither yielded a date
```

- Date parsing of `WorkExperience.from/to` (strings): tolerant parser
  (yyyy, MM/yyyy, "March 2023", "present"/"current"/localized equivalents → ignore as
  end-date evidence). Unparseable → skip.

## B.4 Sample data

No backfill migration (no customers). Re-seed sample data through the new pipeline.
Deliberately tune `demo-seed/` fixtures so the quality report is *interesting*
(some missing phones, a couple of outdated CVs, one low-confidence parse) — a 100%
score makes the feature invisible in the demo funnel; a 68% with three actionable
issues sells it.

---

# Part C — Library Quality backend

## C.1 Response DTO — `src/main/java/ai/qorva/core/dto/LibraryQualityReport.java`

```java
@Builder
public record LibraryQualityReport(
    long totalCVs,
    Integer overallScore,                     // null when totalCVs == 0
    DimensionScore completeness,
    DimensionScore freshness,
    DimensionScore uniqueness,
    DimensionScore parseConfidence,
    List<QualityIssue> issues                 // drill-down hooks
) {
    public record DimensionScore(int score, List<Metric> metrics) {}
    public record Metric(String name, long count, double percentage) {}
    public record QualityIssue(String issueKey, String severity, long count) {}
}
```

`issueKey` values (contract with frontend): `MISSING_CONTACT`, `MISSING_EMAIL`,
`MISSING_PHONE`, `NO_WORK_EXPERIENCE`, `NO_SKILLS`, `MISSING_SUMMARY`, `OUTDATED`,
`UNKNOWN_FRESHNESS`, `LOW_PARSE_CONFIDENCE`, `DUPLICATES`.

## C.2 New aggregations in `CVRepository`

Style: existing `@Aggregation` methods (`$match tenantId` first, always).

**a) Field presence — one single-pass `$group`**, returns one document of counts
(powers completeness AND the structural parse anomalies):

```java
@Aggregation(pipeline = {
  "{ '$match': { 'tenantId': ?0 } }",
  "{ '$group': { '_id': null, 'total': { '$sum': 1 }, " +
     "'hasEmail':  { '$sum': { '$cond': [ { '$gt': [ '$personalInformation.contact.email', '' ] }, 1, 0 ] } }, " +
     "'hasPhone':  { '$sum': { '$cond': [ { '$gt': [ '$personalInformation.contact.phone', '' ] }, 1, 0 ] } }, " +
     "'hasName': …, 'hasRole': …, " +
     "'hasWorkExp': { '$sum': { '$cond': [ { '$gt': [ { '$size': { '$ifNull': [ '$workExperience', [] ] } }, 0 ] }, 1, 0 ] } }, " +
     "'hasSkills': …, 'hasEducation': …, 'hasCareerStartYear': …, " +
     "'hasLanguages': …, 'hasCertifications': …, 'hasSalary': …, 'hasLinkedin': …, 'hasSummary': … } }"
})
FieldPresenceCounts getFieldPresenceByTenantId(ObjectId tenantId);
```

**b) Freshness buckets** — `$group` on a `$switch` over
`$dateDiff($$NOW, contentDate)` (Atlas — already required by vector search):
`UP_TO_DATE` (< 6 months), `REVIEW_SUGGESTED` (6–18), `OUTDATED` (> 18),
`UNKNOWN` (`contentDate` null).

**c) Parse confidence** — one `$group`: count clustering missing, count
`clusterConfidenceScore < 0.5`, average confidence.

**Duplicates:** no new query — reuse `findEmailDuplicates` / `findPhoneDuplicates`
(`CVRepository.java:75-97`); derive `groupCount` and `cvsAffected = Σ(count − 1)` in the
service.

## C.3 `LibraryQualityService`

Clone the `DashboardService` shape exactly: `dashboardExecutor` injection, one
`CompletableFuture.supplyAsync` per aggregation, `orTimeout(15s).exceptionally(fallback)`,
`allOf().join()`, assemble record. Tenant resolution identical to
`DashboardService.getDashboardData` (lines 47–53).

Scoring — pure, unit-testable static methods; weights as constants in this service:

- **Completeness** = weighted avg of field-presence percentages.
  Critical ×3: email, phone, name, role.
  Important ×2: workExperience, keySkills, careerStartYear, education.
  Enrichment ×1: languages, certifications, salary, linkedin, summary.
- **Freshness** = `%upToDate + 0.5 × %reviewSuggested`
  — **UNKNOWN excluded from denominator** (surfaced as its own issue, not punished).
- **Uniqueness** = `100 × (1 − cvsAffected / totalCVs)`.
- **Parse confidence** = % of CVs with clustering present AND confidence ≥ 0.5.
- **Overall** = `0.4·completeness + 0.2·freshness + 0.2·uniqueness + 0.2·confidence`, rounded.
- `totalCVs == 0` → `overallScore = null`, empty metrics (frontend renders empty state).

## C.4 Endpoints — `LibraryQualityController`

`@RequestMapping("/library-quality")`, same `@CrossOrigin("${weblink.allowedOrigins}")`
as `DashboardController`.

- **`GET /library-quality`** → `LibraryQualityReport`.
  Gate: `VIEW_DASHBOARD` (aggregate stats, no CV content).
- **`GET /library-quality/issues?issueKey=…&pageNumber=0&pageSize=20`** → paged slim CV
  list. Gate: `VIEW_CV` (returns candidate data).
  Item shape: `{ id, name, role, email, phone, lastUpdatedAt, contentDate }` only.
  **Projection MUST exclude `attachment`, `rawText`, `embedding`** (3072 floats) —
  the one easy-to-miss perf/correctness trap in the feature.

Issue→Criteria mapping (in `CVRepositoryImpl` or via `CVQueryBuilder`):

| issueKey | Criteria |
|---|---|
| MISSING_CONTACT | email empty AND phone empty |
| MISSING_EMAIL / MISSING_PHONE | respective field empty |
| NO_WORK_EXPERIENCE / NO_SKILLS | array null or empty |
| MISSING_SUMMARY | `candidateProfileSummary` empty (or < N chars) |
| OUTDATED | `contentDate < now − 18 months` |
| UNKNOWN_FRESHNESS | `contentDate` null |
| LOW_PARSE_CONFIDENCE | clustering null OR `clusterConfidenceScore < 0.5` |
| DUPLICATES | *not served here* — frontend routes to the existing duplicates UI |

## C.5 Backend tests

- `LibraryQualityServiceTest`: scoring math (weighting, empty library, div-by-zero
  guards, all-perfect tenant = 100, unknown-freshness exclusion).
- Issue-key criteria mapping unit test.
- Part A tests: `S3StorageService.uploadCvDocument` (mocked `S3Client`: key format,
  extension resolution, metadata mapping); `processFile` orphan-cleanup path
  (mocked S3 + repo failure → `deleteObject` called).
- Part B test: `contentDate` derivation (date-parsing tolerance, max-of-sources,
  UNKNOWN fallback).

---

# Part D — Frontend (`qorva-ai-app`, Option B: dedicated section)

## D.1 Wiring (standard 3-file pattern)

1. `src/constants.js`: `export const COMP_ID_LIBRARY_QUALITY = 'LIBRARY_QUALITY';`
2. `src/components/menu/AppMenuList/AppMenuList.jsx` — `menuItems`, right after CVLIB:
   `{ id: COMP_ID_LIBRARY_QUALITY, Icon: FactCheckOutlinedIcon, label: t('header.libraryQuality', 'Library Quality'), display: true }`
3. `src/components/contents/AppContent.jsx`:
   `case COMP_ID_LIBRARY_QUALITY: return <AppLibraryQuality />;`

Permission note: frontend does soft gating only (backend enforces `VIEW_DASHBOARD` /
`VIEW_CV`); no new frontend gating pattern needed for v1.

## D.2 Service — `src/services/libraryQualityService.js`

```js
import apiClient from '../../axiosConfig.js';
export const getLibraryQuality = () => apiClient.get('/library-quality');
export const getQualityIssues = (issueKey, pageNumber = 0, pageSize = 20) =>
    apiClient.get('/library-quality/issues', { params: { issueKey, pageNumber, pageSize } });
```

## D.3 Components — `src/components/contents/library-quality/`

**`AppLibraryQuality.jsx`** (container) — fetch on mount with the dashboard's defensive
pattern (`res.data.data ?? res.data`, defaulted fields). Layout top→bottom:

1. **Headline row**: big overall-score card colored via the `scoreColor()` convention
   (≥70 green / ≥40 amber / else red — copy the helper from `QorvaDashboard.jsx`,
   it's in-file there), plus four dimension cards (clone `KPICard`).
2. **Completeness panel**: field-presence meters (clone the `InsightCard` /
   `LinearProgress` pattern), grouped critical / important / enrichment.
3. **Freshness panel**: donut via `@mui/x-charts` `PieChart` (same as
   `ChartSection.jsx` `MiniPie`) — buckets labeled
   "Up to date / Review suggested / Outdated / Unknown"
   (deliberately not "stale" — avoid day-one shame).
4. **Issues panel**: one row per `issues[]` entry — severity chip (`QorvaChip`),
   count, "View" button.

**`QualityIssueList.jsx`** — expands inline (or MUI `Drawer`) under a clicked issue:
paged table from `getQualityIssues`, columns name / role / email / phone / content date,
pagination like `AppCVEntries.jsx`.

- `issueKey === 'DUPLICATES'`: "View" dispatches a `window` CustomEvent (established
  decoupling pattern, cf. `demoMode.js` upgrade dialog) handled in `AppHome.jsx` →
  switch `content` to `COMP_ID_CVLIB`; the existing duplicates badge/mode in
  `AppCVContent.jsx` + `AppCVDuplicates.jsx` takes over.
  Fallback if event plumbing is scope creep: "Manage in CV Library" hint text.

After any bulk remediation action, **refetch the report** so the recruiter watches the
score move (61 → 74) — the feedback loop is the retention hook.

## D.4 i18n

New `libraryQuality.*` namespace + `header.libraryQuality` in **all 7** locale files
(`src/locales/{en,de,es,fr,it,nl,pt}/translation.json`): section title, dimension names,
bucket labels, one label per `issueKey`, empty state, drill-down column headers.
~25 keys × 7 files — write English first, translate in one pass.

---

# Part E — Remediation roadmap (value layer)

Every issue row gets a button. Ranked by build cost:

## Wave v1.5 (days) — reuse existing pipeline/CRUD

| Action | Dimension | Mechanics |
|---|---|---|
| **Re-analyze** (single + bulk) | Completeness, Parse confidence | Re-run LLM extraction from `rawText` (Part B); meter via `SCREENING_ACTIONS`. Retroactive benefit every time the prompt/model improves. |
| **Gap-focused quick edit** | Completeness | Micro-form showing ONLY missing fields (email, phone, salary…) in the drill-down list — not the full CV editor. |
| **Keep-newest duplicate resolution** | Uniqueness | Per-group "Keep most recent" + bulk "Resolve all"; **carry over tags** from deleted CVs (recruiter knowledge must survive cleanup). |
| **Bulk archive/tag outdated** | Freshness | One click turns a red metric into a clean library. |
| **"Confirmed current"** | Freshness | Recruiter attests data is accurate → sets the verified signal (`contentDateSource = VERIFIED`, `contentDate = now`). |

## Wave v2 (week) — prevention

- **Upload-time duplicate prompt**: on upload, email/phone lookup against existing CVs →
  "Jane Doe already exists (added Jan 2026). Replace her CV?" Accepting turns a duplicate
  into a version update — fixes uniqueness AND freshness at the source.
- **Upload-result gap warnings**: per-file one-liner after upload
  ("✓ parsed — missing phone") — fixing at upload beats any later cleanup campaign.

## Wave v3 (bigger bet) — candidate self-service

- **Candidate self-update magic link**: email the candidate a tokenized public form
  ("Still open to work? Available from? Notice period? Salary? Upload newer CV?") mapping
  directly onto `Availability` (`openToWork`, `status`, `availableFrom`,
  `noticePeriodDays`) + `salaryExpectation`. One recruiter click (or bulk "request
  updates from all outdated") refreshes data, sets VERIFIED, keeps candidates warm.
  Needs: public endpoint + token, email flow, small consent/GDPR review.
  This is what turns the quality page from a chore list into a growth engine.

Cross-cutting: bulk Re-analyze consumes LLM tokens → flows through
`UsageMonitoringService` → natural upsell lever for higher plans.

---

# Part F — Rollout, verification, open decisions

## F.1 Build order

1. **Part A** (S3 attachment) + **Part B** (rawText/contentDate) — one PR: they share the
   entity/DTO/validator migration and the upload-pipeline refactor.
   Re-seed sample data through the new pipeline afterwards.
2. **Part C** report endpoint (`GET /library-quality`) + service + aggregations + tests.
3. **Part D** frontend overview page (demoable milestone with steps 1–2).
4. **Part C/D** issues endpoint + drill-down UI.
5. **Part E v1.5** remediation actions.
6. i18n completion (6 remaining locales) + demo-seed tuning for an interesting score.

## F.2 Verification checklist

- `./mvnw compile` + existing tests (`SubscriptionWelcomeNotificationServiceTest`) + new tests (C.5).
- Mongock runs `V20260724_01` cleanly on local Mongo; insert-with-attachment passes validator.
- Manual smoke (local profile, real bucket):
  - upload PDF → `attachment.s3Key` set, object exists in S3, `contentDate` populated;
  - upload `.docx` → correct extension/content type;
  - delete CV → S3 object gone;
  - demo purge → `tenants/{tenantId}/cvs/` prefix empty;
  - S3 outage simulation (bad bucket name locally) → CV still persists without attachment (policy A).
- `GET /library-quality` on seeded account → sensible scores; empty tenant → `overallScore: null`.
- Issues endpoint pages correctly and payload contains no `rawText`/`embedding`/`attachment`.

## F.3 Open decisions (defaults assumed unless overridden)

| # | Decision | Default assumed |
|---|---|---|
| 1 | S3 failure policy (A.6) | **A — best-effort**, CV persists without attachment |
| 2 | Field name | keep **`attachment`** (rename is free until implementation starts) |
| 3 | Freshness thresholds | 6 / 18 months, constants in `LibraryQualityService` |
| 4 | Overall-score weights | 40/20/20/20, constants in `LibraryQualityService` |
| 5 | Low-confidence cutoff | `clusterConfidenceScore < 0.5` |
| 6 | Per-CV stored quality score | **No** (compute on read); revisit if drill-downs get slow |

## F.4 Estimated footprint

- **Backend**: ~10 new files (`AttachmentInfo`, `LibraryQualityReport`,
  `LibraryQualityService`, `LibraryQualityController`, migration Java + JSON,
  tests ×3–4), ~9 edited (`CV`, `CVDTO`, `S3StorageService`, `CVService`,
  `DemoDataPurgeService`, `CVRepository`, `CVRepositoryImpl`/`CVQueryBuilder`,
  `QorvaErrorCodes`, message bundles) + 2 doc scripts (`ddl.js`, `updates.js`).
- **Frontend**: 3 new files (`libraryQualityService.js`, `AppLibraryQuality.jsx`,
  `QualityIssueList.jsx`), 3 edited (`constants.js`, `AppMenuList.jsx`,
  `AppContent.jsx`), 7 locale files.
- **Ops**: IAM additions (`s3:DeleteObject`, `s3:ListBucket`).
