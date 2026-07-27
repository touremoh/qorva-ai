# Library Quality Remediation — Scale-Ready Implementation Plan

Status: **implemented (steps 1–5 + A1 + A2, 2026-07-27)** — see caveats in the final commit summary; A3 was a documented no-op decision
Scope: backend (`qorva-ai`) + frontend (`qorva-ai-app`)
Builds on: `docs/library-data-quality-implementation-guide.md` (Parts A–D, implemented)

Design constraints this plan is built around:

- **Tens of thousands of CVs per tenant.** 1% of any issue = hundreds of affected rows.
  Per-row and checkbox-selection UX only works for small counts; the bulk primitive is
  a **server-side operation on the issue criteria**, never a list of IDs (except where
  deliberateness is the point — see confirm-current).
- **Thousands of tenants, worldwide, concurrently.** No quiet batch window; every hot
  query must be indexed; expensive work must be queued, fair, and cost-governed.
- **Prevention beats cure.** At this scale, cleanup campaigns are always painful;
  ingest-time gates are the highest-leverage investment.

The plan is five steps. Step 1 is the foundation everything else depends on; steps 2–5
are independently shippable after it.

| Step | Theme | Ships |
|---|---|---|
| 1 | Foundations | `qualityFlags` per CV, indexes, report caching, duplicates paging |
| 2 | Prevention at ingest | upload duplicate/replace prompt, per-file parse warnings |
| 3 | Cheap synchronous actions | archive, confirm-current, quick-edit, issue dismiss |
| 4 | Job infrastructure | async criteria-bulk Re-analyze with quotas and fairness |
| 5 | Candidate self-update | tokenized public form + update-request campaigns |

---

# Step 1 — Foundations: flags, indexes, caching, duplicates paging

Goal: every quality read (report, drill-down, bulk-action criteria) becomes an indexed
query; no tenant-wide collection scans on hot paths.

## 1.1 `qualityFlags` on the CV document

A denormalized array of write-time-computable defects, maintained in the service layer:

```java
// CV.java / CVDTO.java (READ_ONLY in the DTO)
private List<String> qualityFlags;
```

Flag catalog (enum `QualityFlagEnum`) — negative flags for **all 13 completeness
fields** (so completeness percentages can be derived as `total − flagCount`), plus
structural/confidence anomalies:

```
MISSING_EMAIL, MISSING_PHONE, MISSING_CONTACT,           // contact (MISSING_CONTACT = both)
MISSING_NAME, MISSING_ROLE,
NO_WORK_EXPERIENCE, NO_SKILLS,
MISSING_CAREER_START_YEAR, MISSING_EDUCATION,
MISSING_LANGUAGES, MISSING_CERTIFICATIONS,
MISSING_SALARY, MISSING_LINKEDIN, MISSING_SUMMARY,
NO_AI_ANALYSIS, LOW_AI_CONFIDENCE                        // clustering absent / score < 0.5
```

Deliberately **not** flags (they are not write-time facts):

- **Freshness** decays with time — a stored bucket silently goes stale. It stays
  derived from `contentDate` via indexed range counts (1.3).
- **Duplicates** are a cross-document property — handled by the duplicates pipeline (1.5)
  and prevented at ingest (step 2).
- **Archived** (step 3) is a separate boolean, not a flag.

### Computation — one resolver, one call site per write path

`CVQualityFlagResolver.resolve(CVDTO dto)` — pure, deterministic, unit-tested; the
single source of truth. Wired into:

- `CVService.preProcessCreateOne` (after `CVContentDateResolver.resolve`) — covers
  upload, demo seeding, API create.
- `CVService.preProcessUpdateOne` (after the merge) — covers PUT/PATCH and every
  future remediation write (quick-edit, re-analyze), so flags can never drift stale.

### Migration `V2026072701` (schema + indexes only — no backfill)

Decision 2026-07-27: **no backfill step.** The only tenant is the developer's own
account, which will be wiped and re-seeded with fresh uploads — every CV then gets its
flags from `preProcessCreateOne`. (If real multi-tenant data ever predates this
feature, the backfill is a one-liner pipeline update reusing the resolver's `$cond`
expressions — documented here so it can be resurrected.)

The change unit itself remains — it is how indexes and validator changes reach
local/tst/prd deterministically via Mongock:

1. `collMod` validator: declare `qualityFlags` (array of string, enum of the catalog)
   and `archived` (bool, for step 3 — one migration, not two). Optional strictly
   speaking (the schema allows undeclared fields) but keeps the validator truthful,
   as done for `rawText`/`contentDate`.
2. Indexes (the scale-critical part — required regardless of data):
   - `{ tenantId: 1, qualityFlags: 1 }` (multikey) — report counts, drill-downs, bulk criteria
   - `{ tenantId: 1, contentDate: 1 }` — freshness buckets, OUTDATED criteria
   - `{ tenantId: 1, "personalInformation.contact.email": 1 }` and same for phone —
     duplicates aggregation + step-2 ingest lookups
3. Rollback: drop the new indexes, restore prior validator.

## 1.2 Report reads from flags

`CVRepository` replaces the field-presence scan with one indexed aggregation:

```
{ $match: { tenantId, archived: { $ne: true } } },
{ $unwind: '$qualityFlags' },
{ $group: { _id: '$qualityFlags', count: { $sum: 1 } } }
```

`LibraryQualityService` derives everything it used to get from `FieldPresenceCounts`
as `presence(field) = totalCVs − flagCount(MISSING_field)`. Scoring math, weights, and
the response DTO shape are **unchanged** — this is an internal rewiring, the frontend
does not change in step 1.

## 1.3 Freshness via indexed range counts

Replace the `$switch` scan with four `countDocuments` on `{tenantId, contentDate}`:

- `UNKNOWN`: `contentDate: null` (matches missing — index-covered)
- `UP_TO_DATE`: `contentDate ≥ now−6mo`
- `REVIEW_SUGGESTED`: `now−18mo ≤ contentDate < now−6mo`
- `OUTDATED`: `contentDate < now−18mo`

Thresholds stay the existing named constants. Drill-down criteria for
OUTDATED/UNKNOWN_FRESHNESS already use `contentDate` — now index-backed.

## 1.4 Report caching

- Spring Cache + Caffeine (`spring-boot-starter-cache` + `caffeine` deps),
  `@Cacheable("libraryQuality", key = tenantId)`, TTL **5 minutes**, max size ~10k
  entries (tenant count ceiling; entries are a few KB).
- `@CacheEvict` from every mutation path that changes quality: CV create/update/delete,
  all step-3/4 bulk actions, duplicate resolution. Frontend already refetches after
  actions — eviction makes the refetch honest.
- Quality changes slowly; 5-minute staleness on a passively-viewed report is invisible,
  and the evict-on-action keeps the feedback loop (score visibly moves) intact.

## 1.5 Duplicates: paging and counting in MongoDB

Current `CVService.findDuplicates` loads all groups into Java memory and `subList`s —
replace with:

- **Page query**: one aggregation using `$unionWith` (same collection, second pipeline
  grouping by phone) → each branch: `$match` (indexed field exists, tenant) → `$group`
  (with `cvs: { $topN: … }` capped at ~10 per group to bound document size) →
  `$match count > 1` → tag `matchType` → then unified `$sort { count: -1 } → $skip →
  $limit`.
- **Count query**: same shape ending in `$count` with **no `$push`** — this cheap
  variant is also what the report uses for the DUPLICATES issue count and the
  uniqueness score (`Σ(count−1)` via `$group { excess: { $sum: { $subtract:
  ['$count', 1] } } }`).

`GET /cvs/duplicates` keeps its response contract — frontend unchanged.

## 1.6 Tests / verification

- `CVQualityFlagResolverTest` — every flag, null-vs-empty, boundary confidence 0.5.
- `LibraryQualityServiceTest` — rewritten against flag counts; scoring assertions
  unchanged (scores must be identical to pre-refactor for the same data).
- Post-wipe smoke: clean DB → Mongock runs → upload a fresh batch → report numbers
  match what the pre-refactor aggregations would produce for the same CVs.
- `explain()` on report queries and drill-downs: every one must show IXSCAN.

---

# Step 2 — Prevention at ingest

Goal: stop duplicates and gaps from accumulating; O(1) per uploaded file.

## 2.1 Per-file upload results (replaces silent-failure response)

`POST /cvs/upload` today returns only the successfully created `CVDTO`s; failures are
logged and invisible, and duplicates are undetected. New response:

```java
public record UploadResult(
    String fileName,
    String status,          // CREATED | DUPLICATE_DETECTED | FAILED
    CVDTO cv,               // when CREATED or DUPLICATE_DETECTED (the new copy)
    DuplicateMatch match,   // when DUPLICATE_DETECTED: { existingCvId, name, matchType, existingCreatedAt }
    List<String> warnings,  // subset of qualityFlags worth surfacing: MISSING_PHONE, MISSING_EMAIL, NO_WORK_EXPERIENCE, LOW_AI_CONFIDENCE
    String errorCode        // when FAILED (existing QorvaErrorCodes)
) {}
```

- **Duplicate detection**: after extraction, before returning — indexed point lookups
  on `{tenantId, email}` / `{tenantId, phone}`. The new CV **is persisted** (no pending
  state to hold server-side); the response flags the collision for immediate
  resolution. Detection cost: ≤2 indexed lookups per file, flat at any library size.
- **Parse warnings**: free — read straight off the just-computed `qualityFlags`.
- Frontend contract change is breaking → coordinate the upload dialog update in the
  same release.

## 2.2 Replace endpoint

`POST /cvs/{newCvId}/replace/{oldCvId}` (gate `DELETE_CV`, both must belong to tenant):

1. Union `tags` from old into new (recruiter knowledge survives), preserve old
   `dataVerified`-style state only if newer — in practice: keep new CV's contentDate
   (it's the newer document).
2. Delete old CV via the existing `deleteOneById` path (cascades reports, chats, S3
   object — already implemented).
3. Evict report cache.

## 2.3 Upload dialog UX

After upload completes, replace the current "done" state with a **results list**:

- ✓ `file.pdf` — created
- ✓ `file2.pdf` — created, *missing phone*
- ⚠ `file3.pdf` — matches existing **Jane Doe** (added Jan 2026):
  [Replace old version] [Keep both]
- ✗ `file4.pdf` — could not be read

"Replace all N" bulk button when several duplicates are detected. All strings ×7 locales.

---

# Step 3 — Cheap synchronous actions + issue lifecycle

Goal: every issue row gets a button; all operations are single indexed
`updateMany`s — safe synchronously even at 50k CVs.

## 3.1 Archive

**Model**: `archived: Boolean` on CV (validator + entity + DTO READ_ONLY; field added
in the step-1 migration). Index `{tenantId, archived}` partial (`archived: true`) —
the set is small relative to the library.

**Semantics** (the important design decision):

- Excluded from **quality report** aggregations (`archived: { $ne: true }` in every
  `$match` — added in step 1 so it's one change).
- Excluded from **vector search** (`similaritySearch` filter) and **text search** —
  an archived candidate must not appear in job matching; that is the point of archiving.
- **Visible** in the Resume Library behind an "Archived" filter toggle (default off),
  with per-CV unarchive. Never silently deleted.

**Endpoint**: `POST /library-quality/actions` (gate `MODIFY_CV`):

```json
{ "action": "ARCHIVE", "issueKey": "OUTDATED" }         // criteria-based, whole issue
{ "action": "ARCHIVE", "cvIds": ["…"] }                 // or explicit small selection
{ "action": "UNARCHIVE", "cvIds": ["…"] }
```

Criteria mode resolves `issueKey → indexed filter` (same mapping as drill-downs),
executes one `updateMany`, returns `modifiedCount`, evicts cache. Response drives the
"score moved" refetch.

## 3.2 Confirm-current (deliberately friction-ful)

`{ "action": "CONFIRM_CURRENT", "cvIds": [...] }` — **explicit IDs only, no criteria
mode, server-enforced cap of 50 per call.** Sets `contentDate = now`,
`contentDateSource = VERIFIED` (server-side — these DTO fields are READ_ONLY by
design). Mass-verifying unchecked data would corrupt the freshness signal; the cap
makes verification proportional to actual human attention. The UI pairs it with the
honest alternative ("Request updates from candidates" — step 5) for large sets.

## 3.3 Quick-edit missing fields

No new backend (PATCH `/cvs/{id}` exists; step 1 recomputes flags on update).
Frontend: in `QualityIssueList` rows for MISSING_PHONE / MISSING_EMAIL /
MISSING_CONTACT, an inline editable field + save; row disappears on refetch. Only
rendered when the issue count is small (≤ ~20 visible rows at a time — it's paginated
anyway; the point is inline editing stays useful per page even when the total is 500).

## 3.4 Issue lifecycle: dismiss / reopen

At scale some findings are consciously accepted risk; without a dismissed state the
report becomes permanent red noise.

- **Model**: small collection `quality_issue_states`
  `{ tenantId, issueKey, status: DISMISSED, dismissedBy, dismissedAt }` (unique index
  `{tenantId, issueKey}`). Not on the tenant document — keeps tenant lean and writes
  contention-free.
- **Endpoints**: `POST /library-quality/issues/{issueKey}/dismiss` and `/reopen`
  (gate `MODIFY_CV`).
- **Report**: issues still computed; response gains `dismissed: boolean` per issue.
  Dismissed issues are **excluded from the overall score's issue presentation but NOT
  from the dimension scores** (scores stay honest; dismissal is a display/triage state).
- **Frontend**: dismissed issues collapse into a "Dismissed (N)" section with reopen.

## 3.5 Volume-aware issues panel

Per-issue action row driven by `issueKey` + count:

| Issue | Actions offered |
|---|---|
| OUTDATED | [Archive all N] · [Confirm selected (≤50)] · [Request updates] (step 5) |
| MISSING_PHONE / EMAIL / CONTACT | inline edit · [Re-analyze all N] (step 4) |
| NO_WORK_EXPERIENCE / NO_SKILLS / MISSING_SUMMARY / LOW_PARSE_CONFIDENCE | [Re-analyze all N] (step 4) |
| UNKNOWN_FRESHNESS | [Confirm selected] · [Request updates] (step 5) |
| DUPLICATES | existing embedded manager (delete per copy) |
| any | Dismiss / Reopen |

Every action: confirmation dialog showing the affected count → execute → evict/refetch
→ score visibly moves.

---

# Step 4 — Job infrastructure + async bulk Re-analyze

Goal: run N×LLM-call operations for any N, fairly across thousands of tenants, with
cost governance. This infrastructure is also the substrate for step 5's campaigns.

## 4.1 Job model

Collection `background_jobs` (+ validator migration, entity, repository):

```
{
  _id, tenantId, type: 'REANALYZE' | 'CANDIDATE_UPDATE_CAMPAIGN',
  criteria: { issueKey } | { cvIds },       // resolved to a filter at execution time
  status: PENDING | RUNNING | COMPLETED | COMPLETED_WITH_ERRORS | FAILED | CANCELLED,
  progress: { total, processed, succeeded, failed, skipped },
  errorSamples: [ { cvId, errorCode } ],    // capped at ~20
  leaseOwner, leaseExpiresAt,               // multi-instance claim/heartbeat
  createdBy, createdAt, startedAt, finishedAt
}
```

Indexes: `{status, leaseExpiresAt}` (worker claim), `{tenantId, createdAt}` (listing).

## 4.2 Execution engine (in-process poller now, queue-swappable later)

- `@Scheduled` poller (every ~5s) claims one PENDING job via atomic
  `findAndModify(status: PENDING → RUNNING, leaseOwner, leaseExpiresAt = now+2m)`.
  Safe across multiple App Runner instances; a heartbeat extends the lease each batch;
  stale leases (crashed instance) are reclaimed by the same query.
- Processing: stream matching CVs by indexed criteria in batches; per item, isolated
  try/catch → increment succeeded/failed; job progress updated per batch (not per item).
- **Fairness/limits**:
  - Per-tenant: **one active job per type** (enforced at submit).
  - Per-job LLM concurrency: small fixed parallelism (e.g. 3–5 virtual threads).
  - Global: LLM calls acquire the **existing OpenAI semaphore** (already in place from
    the App Runner HTTP-client hardening work) — job traffic and interactive
    upload/screening traffic share one provider budget, so background work can never
    starve interactive work if sized so interactive keeps headroom.
- Cancellation: status flip checked between batches.
- The engine is behind a `JobRunner` interface — moving claim/dispatch to SQS later is
  an implementation swap, not a redesign.

## 4.3 The REANALYZE operation

Per CV:

1. Skip (counted as `skipped`) if `rawText` is absent (pre-feature uploads) — the UI
   explains "N older resumes have no stored text; re-upload to refresh them".
2. `OpenAIService.streamCVExtraction(rawText)` → map via `OpenAIResultMapper` → merge
   into the existing document **preserving**: id, tenantId, applicantNumber, tags,
   attachment, archived, and VERIFIED contentDate (resolver already guarantees the
   last). Persist via the normal update path → flags + contentDate recomputed.
3. Meter one `SCREENING_ACTIONS` per successful item; stop the job cleanly with
   `FAILED (quota_exceeded)` if the tenant hits their plan limit mid-run.

## 4.4 Cost pre-flight

`POST /library-quality/jobs` with `dryRun: true` → `{ affectedCount, skippedNoRawText,
estimatedActions, remainingQuota }` (all indexed counts). The UI shows "This will use
~480 of your 2,000 remaining screening actions" → user confirms → same call with
`dryRun: false` → job created. Submissions that would exceed quota are rejected with a
clear error (and are the natural plan-upgrade prompt).

## 4.5 API + frontend

- `POST /library-quality/jobs` (gate `MODIFY_CV`), `GET /library-quality/jobs`
  (active + last 10, gate `VIEW_DASHBOARD`), `GET /jobs/{id}`, `POST /jobs/{id}/cancel`.
- Quality page: a **job progress card** appears above the issues panel while a job is
  active (poll every 4s: progress bar, processed/total, cancel). On completion:
  success summary + automatic report refetch. Issue rows with an active job show the
  in-flight state instead of their action buttons.
- Optional (flagged): completion email through the existing pending-email notification
  infrastructure for long jobs.

---

# Step 5 — Candidate self-update links

Goal: the freshness fix that scales — candidates update their own data. Built as a
**campaign** on step-4 infrastructure, because at 10k CVs this is a bulk-email problem,
not a send-loop.

## 5.1 Model

Collection `candidate_update_requests`:

```
{ _id, tenantId, cvId, tokenHash,          // 256-bit random token, SHA-256 stored, plaintext only in the email link
  status: SENT | OPENED | COMPLETED | EXPIRED,
  sentAt, openedAt, completedAt, expiresAt  // default 30 days
}
```

Plus `suppressed_emails` `{ tenantId?, email, reason: UNSUBSCRIBED | BOUNCED }` —
checked before every send; the public form's unsubscribe link writes to it.

## 5.2 Public endpoints (unauthenticated — hardened)

- `GET /public/candidate-update/{token}` → minimal prefill only (first name, current
  availability, salary expectation — never the full CV), marks OPENED.
- `POST /public/candidate-update/{token}` → accepts `Availability` fields,
  `salaryExpectation`, and an optional replacement CV file (multipart, same
  type/size validation as upload).
- Hardening: token single-use for POST; expiry enforced; per-IP and per-token rate
  limits; constant-time hash lookup; no tenant/candidate enumeration in error
  responses; CORS restricted to the app origin.
- On completion: apply field updates; if a file was included, run the normal
  extraction pipeline as a **replace** of that CV (step 2.2 mechanics); set
  `contentDateSource = VERIFIED`, `contentDate = now`; evict report cache;
  mark COMPLETED.

## 5.3 Campaign job (`CANDIDATE_UPDATE_CAMPAIGN`)

- Submit with criteria (e.g. `issueKey: OUTDATED`); dry-run returns
  `{ eligible, skippedNoEmail, skippedSuppressed, skippedActiveRequest }`.
- Execution: batched sends through the existing email service, provider rate limit as
  a config (e.g. ≤ N/sec) enforced by the job's batch pacing; one request doc + one
  email per eligible CV; hard bounces recorded to `suppressed_emails` (minimal v1:
  provider webhook out of scope, mark FAILED on synchronous send errors).
- Email template: tenant branding (logo already in S3), candidate language = tenant
  language (v1), link + expiry + unsubscribe. Templates ×7 locales via the existing
  message-template infrastructure.
- GDPR/compliance: processing-notice text on the public form, unsubscribe in every
  email, suppression honored tenant-wide, requests auto-EXPIRED by a daily sweep.

## 5.4 Frontend

- Quality page: "Request updates from candidates" on OUTDATED / UNKNOWN_FRESHNESS →
  dry-run summary dialog → confirm → campaign job card (reuses step-4 UI).
- Drill-down rows show request status chips (Sent / Opened / Completed).
- New **public page** in the app: `/candidate-update/:token` — standalone route
  (no auth shell), mobile-first, i18n'd, availability + salary form + optional CV
  upload + consent text + success state.

---

# Addendum — companion changes (requested 2026-07-27)

## A1 — Issue-count badge on the "Library Quality" menu item

**Goal**: the sidebar shows how many open issues exist without visiting the page.

- **Backend**: `GET /library-quality/summary` → `{ openIssueCount }` (count of
  non-dismissed issue types). Served **from the step-1 report cache** (internally calls
  the cached `getReport`), so sidebar traffic from every logged-in user worldwide adds
  ~zero DB load. Gate `VIEW_DASHBOARD`.
- **Frontend** (`AppMenuList.jsx`): fetch on mount, re-fetch every 5 minutes and on a
  new `qorva:quality-changed` window event dispatched by every quality mutation
  (actions, jobs completing, duplicate deletes) — same decoupling pattern as the demo
  upgrade dialog. Display: a small count chip on the "Library Quality" child item
  (numeric badge, not text — avoids i18n plural forms and menu-width issues), with a
  translated tooltip ("3 issues to fix" ×7 locales). Collapsed rail: badge dot on the
  icon. Badge hidden at zero.
- **Sequencing**: v0 can ship right after step 1 (counts all issues); becomes fully
  accurate after step 3 (dismissed issues excluded). Do not ship before step 1 — the
  summary endpoint must sit on the cache, not on the five-scan report.

## A2 — AI pre-fill of scoring rules on job-post creation

**Goal**: on "Next" into the matching-criteria step, OpenAI drafts the full
`ScoringRules` (weighted skills, experience thresholds, location/industry strictness,
weight distribution, availability filters) from the job description, so the recruiter
only reviews and adjusts. **Create mode only — never on update** (an update means a
human already invested in these rules; overwriting them is destructive).

- **Backend**:
  - New prompt `Scoring_rules_prefill_prompt.md` + `Scoring_rules_output_format.json`
    mirroring the `ScoringRules` structure (importance enums, weights summing to 1.0,
    strictness enums, availability statuses from the existing catalog).
  - `OpenAIService.suggestScoringRules(title, description)` via
    `BeanOutputConverter<ScoringRules>`; sanitize enums the way `OpenAIResultMapper`
    does for availability (invalid values → null, weights renormalized to 1.0).
  - `POST /job-posts/scoring-rules/suggest` `{ title, description }` — takes raw text
    because at this wizard step the job post is not yet persisted. Gate: the existing
    job-post create authority. Metered as one `SCREENING_ACTIONS`; the call goes
    through the shared OpenAI semaphore like all LLM traffic.
- **Frontend** (`JobsContent.jsx` wizard):
  - On "Next" in **create** mode: advance to the scoring step immediately, show an
    "AI is drafting your scoring rules…" overlay on the form, populate on response
    with an "AI-suggested — review before saving" chip; every field stays editable.
  - Guards against waste at scale: fire only if the scoring form is still untouched
    AND the description changed since the last suggestion (hash compare) — Back/Next
    bouncing must not re-bill; one in-flight request max; on failure, fall back
    silently to the empty form (pre-fill is an accelerator, never a blocker).
  - Update mode: never calls, no UI difference.
- **i18n**: overlay + chip strings ×7 locales. Suggested skill labels naturally follow
  the job description's language.

## A3 — Recruitment type in the matching report prompt: **recommend leaving as-is**

Decision recorded here so it isn't relitigated: **do not inject tenant
`recruitmentType` into `Matching_report_generation_prompt.md`.**

Rationale:

1. **The prompt already has stronger, per-job signal.** The report receives the full
   job description *and* the job's own `ScoringRules` — including
   `industryPreferences` with strictness and `seniorityLevel`. A tenant-level segment
   is strictly coarser than both.
2. **It describes the tenant, not the job — and can actively mislead.** A `TECH_IT`
   tenant hiring a finance controller, or any `GENERALIST` agency, would get wrong
   domain context injected into every evaluation, biasing scores on exactly the
   cross-domain hires where accuracy matters most.
3. **Prompt churn has a hidden cost**: reports become subtly non-comparable across the
   change (same CV, same job, different narrative), for no measurable accuracy gain.

If domain calibration ever proves lacking (e.g. complaints that certifications are
under-weighted in healthcare tenants), the right lever is **per-job**, not per-tenant:
enrich `industryPreferences` / add an optional domain field on the job post — which A2
will already be pre-filling from the job description.

# Cross-cutting

- **Permissions**: reads `VIEW_DASHBOARD`; mutations `MODIFY_CV`; replace/delete
  `DELETE_CV`; public endpoints anonymous by design. Demo users hit the existing
  403 → upgrade-dialog flow on every action — the issues panel doubles as a
  conversion surface, no extra work.
- **Cache coherence rule**: every mutation path evicts the tenant's report cache;
  every frontend action refetches after completion. One rule, applied everywhere.
- **i18n**: all new UI strings, email templates, and the public form ×7 locales;
  audit script from the login/register work reused per step.
- **Observability**: structured logs per job (jobId, tenantId, counts, duration);
  WARN on lease reclaims; usage metering already covers LLM spend per tenant.
- **Demo fixtures**: unchanged — v2 imperfections now also exercise the remediation
  actions in demos (archive an outdated sample, re-analyze the bad parse, etc.).

# Sequencing, estimates, risks

Ship order = step order; each step is one PR/release:

| Step | Est. size | Risk to watch |
|---|---|---|
| 1 | ~8 backend files new/changed + migration (schema/indexes only); no frontend | Flag-resolver parity — new flag-based scores must equal the old aggregation's scores for identical data |
| A1 | summary endpoint + menu badge (small) | Must sit on the step-1 cache; never ship against the uncached report |
| 2 | upload response contract + dialog rework | Breaking API change — backend & frontend must release together |
| 3 | ~6 backend + issues-panel rework | Archive semantics leaking into search/matching — test vector search exclusion explicitly |
| A2 | prompt + suggest endpoint + wizard overlay (medium, independent — can ship any time after A1) | Re-bill guard (hash compare) must work; enum sanitization on LLM output |
| 4 | job engine + 1 operation + progress UI | Lease/claim races on multi-instance App Runner — integration-test the findAndModify claim; keep the OpenAI semaphore authoritative |
| 5 | public flow + campaign + email | Security review of the public endpoints before exposure; provider send limits |
| A3 | none — decision: leave matching report prompt unchanged (see addendum) | — |

Definition of done per step: tests green, `explain()` shows IXSCAN on new queries,
i18n complete, quality score visibly updates after every action.
