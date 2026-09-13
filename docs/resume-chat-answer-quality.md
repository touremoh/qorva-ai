# AI Resume Chat — answer quality: fit-score consistency & formatting

_Written 2026-09-13 on `feature/resume-chat-context-window` (follows
`resume-chat-context-window.md`)._

> **IMPLEMENTED 2026-09-13** — steps 1–6, uncommitted on the feature branches. Notes:
> `ScreeningContextProvider.load(Chat)` resolves the report per turn and `ChatAgent` writes the
> found id onto the chat (saved by `ChatService` in the reply transaction); `ScreeningContext`
> gained `matchingReportId` / `finalScore` / `reportStale`. The header chip resolves the report
> via `POST /matching-reports/search` (no new endpoint) and the app refetches `GET /chats/{id}`
> after every reply. Tests: `ResumeChatPromptBuilderTest`, `ScreeningContextProviderImplTest`,
> `ChatAgentTest`, serializer test flipped. Acceptance checks 1–5 still to run by hand.

## 0. The two observations

1. Asked "how well does Mohamed fit the senior backend role?", the chat answered
   **"Estimated fit: 75–80%"**. A screening report generated afterwards for the same
   CV + job says **64%**. Two numbers for one question.
2. Answers come back as Markdown (`###`, `**bold**`, `> quotes`, `| tables |`) but the
   bubble renders them as raw text, so a 75-minute interview plan is a wall of asterisks.

## 1. Fit-score inconsistency

### 1.1 Why the numbers differ — and why they always will

The two numbers are produced by two different pipelines with different inputs:

| | Screening report | Resume chat |
|---|---|---|
| Model | `gpt-5.6-terra` (`qorva.ai.report.model`) | `gpt-5.6-sol` (`qorva.ai.resume-chat.model`) |
| Prompt | `Matching_report_generation_prompt.md` — weighted formula over `scoringRules` (`skills` / `experience` / `location` / `industry` weights, `MANDATORY` penalties, location strictness…) | `ResumeChatPromptBuilder.RULES` — "answer from the context"; no scoring instructions at all |
| Scoring rules | Passed explicitly (`ReportGenerationAgent.java:37,56`) | **Stripped from the job JSON** by `ChatContextSerializer.JobPostMixin` (`scoringRules` is in its ignore list) |
| Output | Structured `decisionSummary.finalScore` + per-dimension scores, persisted | Free text; the number is improvised on the spot |

So the chat's 75–80 % is not "wrong", it is a gut estimate by a different model with no
scoring rules — and the report's 64 % is the tenant's *official* score. Making the two
agree numerically is not achievable (different models, temperature 1, no formula in chat).
The fix is to stop having two sources: **the report score is the only fit score the chat
is allowed to state.**

### 1.2 Why the chat didn't know about the report

Even after the report existed, the chat could not see it:

- `Chat.context.matchingReportId` is set once at creation (`ChatService.createChat`) from
  whatever the dialog found at that moment. This chat was created when no report existed
  (dialog showed "No matching resume found"), so the id is `null` **forever**.
- `ChatAgent.answer` → `ScreeningContextProvider.load(cvId, jobPostId, matchingReportId)`
  only loads a report when the id is non-null. A report generated five minutes later
  never enters the prompt.

Side finding — the dialog's score chip is dead: `AppAIResumeChat.jsx:1050-1053` reads
`resumeMatch.aiAnalysisReportDetails.overallSummary.score`, but the DTO shape is
`matchingReportDetails.decisionSummary.finalScore`. The chip never renders.

### 1.3 Target behaviour

| Situation | The chat should… |
|---|---|
| A report exists for (cv, job) | Quote **its** `finalScore` as *the* fit score, name it as the screening result, and explain it using the report's `strengths` / `weaknesses` / `redFlags` / `missingSkills`. Never produce a second number. |
| No report exists | Give a **qualitative** assessment (strong / partial / weak fit, with reasons) and say explicitly that no screening report has been run, so no score is available — and suggest running one. **No percentage.** |
| Recruiter insists on a number without a report | Refuse politely: "the scored evaluation is produced by the screening report; I can run through strengths and gaps instead." |
| Report exists but is older than the CV (`report.lastUpdatedAt < cv.lastUpdatedAt`) | Quote the score but flag that the CV changed since. |

### 1.4 Solution

**A. Resolve the report per turn, not per chat** (`ScreeningContextProviderImpl`)

```
matchingReportId != null  → load by id (as today)
else                      → MatchingReportRepository
                              .findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(tenant, jobPostId, cvId)
                            (the same query POST /matching-reports/search uses)
found                     → persist the id back into chat.context.matchingReportId
                            (one save, so the lookup happens once per chat)
```
`load(...)` needs the tenant id and the `Chat` (or a small record) instead of three strings.

**B. Put the scoring rules back in the job context** (`ChatContextSerializer.JobPostMixin`)
— remove `scoringRules` from the ignore list. It is a few hundred tokens and it lets the
model *explain* the official score ("skills weigh 50 %, Kubernetes is `MANDATORY`") instead
of inventing one. Keep `matchingReportsNeeded`, `atsRef`, audit fields excluded.

**C. Make the score unmissable in the context block** (`ResumeChatPromptBuilder.contextBlock`)
Prepend a one-line header before the report JSON so the model cannot overlook it:

```
RESUME MATCH ANALYSIS (official screening score: 64 % — quote this figure, do not compute your own):
{ …report json… }
```
When there is no report, emit instead:

```
RESUME MATCH ANALYSIS: none generated yet. Do not state a fit percentage.
```

**D. Scoring rules in the system prompt** (`ResumeChatPromptBuilder.RULES`) — add:

> Fit scores: the only fit score you may state is the official screening score from the
> RESUME MATCH ANALYSIS, and you must attribute it ("the screening report scores this
> match at 64 %"). If there is no analysis, assess the fit qualitatively (strong / partial /
> weak, with evidence from the CV and the job's scoring rules) and say that no scored
> report has been generated yet. Never estimate or invent a percentage.

**E. Frontend affordance** (`AppAIResumeChat.jsx`)
- Fix the score chip path (`matchingReportDetails.decisionSummary.finalScore`).
- In the chat header, show the linked report's score when `selectedChat.context.matchingReportId`
  is set; when it is not, show a small "No screening report — Run screening" link that
  deep-links to the reports screen for this job. After A, the next message will pick the
  report up automatically; the UI only needs to refetch the chat (`GET /chats/{id}`) after
  a reply to refresh the header.

**F. Don't try to align the models.** Keeping chat on `sol` and reports on `terra` is fine
once the chat defers to the report. If cost matters more than answer quality, the chat can
move to `terra` via `QORVA_RESUME_CHAT_MODEL` — unrelated to the consistency problem.

## 2. Formatting

### 2.1 Why it looks bad

- `AppAIResumeChat.jsx:757` renders `{m.content}` in a `Typography` with
  `whiteSpace: 'pre-wrap'` — Markdown syntax is shown literally.
- The assistant bubble is capped at `maxWidth: '72%'`; a structured plan with headings
  and a scorecard table needs the width.
- The prompt says nothing about format or length, so `gpt-5.6-sol` defaults to long,
  heavily structured Markdown (headings, horizontal rules, blockquotes, tables) even for a
  one-line question.

### 2.2 Solution — render Markdown, and ask for less of it

**A. Render Markdown in assistant bubbles only** (user bubbles stay plain text).

- Add `react-markdown` + `remark-gfm` (tables, task lists, strikethrough). `react-markdown`
  does not render raw HTML by default, so no XSS surface; `dompurify` (already a dependency)
  is not needed here.
- New component `ChatMarkdown.jsx` (`src/components/contents/chats/`) mapping Markdown
  nodes to MUI-styled elements so it matches the bubble typography:
  - `h1–h3` → `Typography` 0.9 rem / 600, small top margin (all levels collapse to the same
    size — headings in a chat bubble are section labels, not document titles)
  - `p` → 0.85 rem, `lineHeight 1.55`, margin 0 0 0.5em
  - `ul/ol/li` → tight lists (`pl: 2.5`, `my: 0.25`)
  - `blockquote` → left border in the brand green, italic, used by the model for
    "questions to ask"
  - `table` → wrapped in `Box overflow-x: auto`, compact cell padding, header row bold
  - `code` → monospace chip; `pre` → scrollable block
  - `hr` → thin divider with 1 rem vertical margin
  - `a` → open in new tab, `rel="noopener"`
- Assistant bubble `maxWidth` → `'88%'` (keep 72 % for the user).
- Add a **copy** icon button on assistant bubbles (copies the raw Markdown — useful for
  pasting an interview plan into a doc). The copy-reference pattern already exists in the
  file (`navigator.clipboard.writeText`, `copied` i18n key).

**B. Tell the model how to format** (`ResumeChatPromptBuilder.RULES`, appended):

> Formatting: Markdown is rendered. Default to a short answer — a few sentences or up to
> ~8 bullets — and only produce a long structured document (headings, tables, scorecards)
> when the recruiter explicitly asks for a plan, checklist, report or comparison. Use `###`
> for section headings, bold for the key fact of a bullet, blockquotes for verbatim
> questions to ask the candidate, and tables only for comparisons with ≤ 4 columns. No
> horizontal rules, no nested headings deeper than `###`, no emojis.

This keeps the interview-plan case (which *should* be long) while stopping the
"75-minute plan" reflex for simple questions.

**C. Optional — streaming.** Long structured answers are where perceived latency hurts most.
Not needed for formatting, but if it is ever done (`.stream()` + SSE), the Markdown
component must handle partial input (react-markdown does).

## 3. Implementation plan

| # | Step | Repo | Files | Notes |
|---|---|---|---|---|
| 1 | Report resolved per turn + id persisted back (1.4 A) | ai | `ScreeningContextProvider` (signature: `load(Chat)`), `ScreeningContextProviderImpl`, `ChatAgent`, `ChatService` (pass tenant), `MatchingReportRepository` (query exists) | unit test: report found later is loaded and the chat's context id is written |
| 2 | Scoring rules back in job context (1.4 B) | ai | `ChatContextSerializer.JobPostMixin` | update `ChatContextSerializerTest` (currently asserts `scoringRules` is absent — flip it) |
| 3 | Score header + no-report sentinel in context block (1.4 C) | ai | `ResumeChatPromptBuilder.contextBlock`, `ScreeningContext` (add `Double finalScore`, `boolean reportStale`) | test: header present with score / sentinel when null |
| 4 | Scoring + formatting rules in the system prompt (1.4 D, 2.2 B) | ai | `ResumeChatPromptBuilder.RULES` | spot-check on 3 real chats: no invented %, short answers to short questions |
| 5 | Markdown rendering + wider bubble + copy button (2.2 A) | app | `package.json` (+`react-markdown`, `remark-gfm`), new `ChatMarkdown.jsx`, `AppAIResumeChat.jsx` | user bubbles unchanged |
| 6 | Score chip path fix + header report state + "Run screening" link (1.4 E) | app | `AppAIResumeChat.jsx`, i18n keys `noReportYet`, `runScreening`, `screeningScore` (7 locales) | refetch `GET /chats/{id}` after each reply to refresh the header once step 1 has linked a report |

Steps 1–4 are the consistency fix and are backend-only; step 5 alone fixes readability.
Both halves are independent and can ship separately.

### Acceptance checks

1. Chat created with no report → ask for a fit score → answer is qualitative, states no
   report exists, no `%` anywhere in the reply.
2. Generate the report → next question in the same chat → reply quotes exactly the report's
   `finalScore`, attributed to the screening report; `chat.context.matchingReportId` is now set.
3. "Help me prepare a technical interview" → headings, bullets, blockquotes and the
   scorecard table render as rich text; copy button yields the raw Markdown.
4. "Does he know Kafka?" → a few sentences, no headings.
5. Score chip in the create-chat dialog shows the report score when one exists.

## 4. Out of scope / later

- Letting the chat **trigger** report generation ("run the screening for me") — needs the
  screening quota checks (`hasNotExceededScreeningLimit`) and the async job plumbing; a
  link to the reports screen covers the need for now.
- Per-dimension score explanations as UI cards inside the chat (skills / experience /
  location / industry) — nice, but the report screen already shows them.
