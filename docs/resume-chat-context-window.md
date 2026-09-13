# AI Resume Chat — conversation history growth: analysis & implementation guide

_Written 2026-09-13 against qorva-ai @4240fd1 and qorva-ai-app @37f14f4 (both `develop`).
Branch: `feature/resume-chat-context-window` in both repos._

> **IMPLEMENTED 2026-09-13** — steps 0–6 below, uncommitted on the feature branches.
> Deviations from the plan: the strict JSON-schema response format was dropped (plain text,
> `OpenAIChatResponse` deleted); the stored SYSTEM seed message is no longer inserted at all
> (`createChat`) instead of merely filtered; `postUserMessage` reuses the newest USER row when
> the same text is re-posted, so a UI retry after a failed model call does not duplicate the
> question; the second transactional step uses `TransactionTemplate` rather than a split bean.
> Chat tests: 14 (`ConversationWindowBuilderTest`, `ChatContextSerializerTest`,
> `ChatServiceTest`, `ChatSummaryServiceTest`); full suite 286 green. §7 (tst verification)
> still to do by hand. **2026-09-13 follow-up:** `gpt-5-chat-latest` turned out to be retired
> (OpenAI 404 `model_not_found`); default switched to `gpt-5.6-sol`, the replacement OpenAI lists.

## 1. Symptom

Long-running resume chats degrade and eventually every turn fails. From the recruiter's
point of view: answers get slower, then new replies stop appearing, then sending a
message errors out (silently — the UI only `console.error`s).

## 2. How a turn works today

```
POST /chats/{id}/messages                       ChatController.java:66
  └─ ChatService.postUserMessage()               ChatService.java:118   @Transactional (Mongo tx!)
       ├─ save USER message
       ├─ buildConversationWindow()               ChatService.java:212   ALL messages, last 100 kept
       ├─ ChatAgent.answer(chat, history)         ChatAgent.java:28
       │    ├─ ScreeningContextProvider.load()    ScreeningContextProviderImpl.java:23
       │    │     CV DTO + JobPost DTO + MatchingReport DTO → JSON strings
       │    ├─ PromptComposer.compose()           PromptComposer.java:12
       │    │     one big string: "SYSTEM:…CONTEXT:…HISTORY:\nUSER: …\nASSISTANT: …"
       │    └─ OpenAIService.chatCompletions()    OpenAIService.java:50
       │          gpt-5-chat-latest, strict JSON schema {content}, .call().content()
       └─ save ASSISTANT message, save chat
```

Nothing is ever dropped from the prompt except by the `MAX_MSG = 100` cut, and the cut is
by message count, not by size. Every turn re-sends the full CV + job + report JSON **and**
the whole transcript.

## 3. Root causes (ranked by impact)

### 3.1 Unbounded prompt — the actual "history too long" problem

`ChatService.buildConversationWindow()` (`ChatService.java:212-222`) streams every message
of the chat and keeps the last 100. With `gpt-5-chat-latest` answers routinely running
500–1 500 tokens, 100 messages is 40–80 k tokens of history on top of a 5–15 k token
context block. Consequences, in the order they appear as a chat grows:

1. **Latency climbs** — prompt tokens are re-processed each turn; time-to-first-token grows
   roughly linearly with prompt size, and the strict JSON-schema response format adds
   overhead of its own.
2. **Two 60-second walls are hit**, and they compound:
   - `OpenAiHttpClientConfig.java:41` — `setResponseTimeout(60s)` / socket timeout 60 s.
     When it fires, Spring AI's retry (`application.yml:31-38`, `max-attempts: 3`) re-sends
     the same oversized prompt up to 3× → up to ~3 min of blocked request.
   - `postUserMessage` is `@Transactional` and a `MongoTransactionManager` is active
     (`MongoConfig.java:70`). MongoDB aborts any transaction older than
     `transactionLifetimeLimitSeconds` (default **60 s**). The LLM call runs *inside* that
     transaction, so once a turn takes > 60 s the `chatMessagesRepository.save(assistant)`
     after it fails with a transaction-aborted error even when OpenAI eventually answered —
     and the USER message is rolled back with it.
3. **Hard ceiling** — `gpt-5-chat-latest` has a 128 k context window. A chat with long
   answers can reach it; OpenAI then returns `400 context_length_exceeded`, which is *not*
   retried (`on-client-errors: false`) and surfaces as an unhandled error. From that point
   the chat is permanently broken: every turn re-sends the same too-large prompt.
4. **Cost** — each turn costs the full transcript. A 60-turn chat has paid for ~30× the
   tokens a windowed design would.

### 3.2 Frontend pagination drift — why "new replies stop appearing" at 50 messages

`AppAIResumeChat.jsx:155` calls `getMessages(chatId, { pageNumber, pageSize })` but the
controller reads `page` / `size` (`ChatController.java:76-78`). Both params are ignored, so
the backend always returns **page 0, size 50, sorted by `createdAt` ASC** — the *oldest* 50
messages. After `sendMessage` succeeds the component re-fetches "page 0"
(`AppAIResumeChat.jsx:283`) expecting to see the new reply; once a chat passes 50 non-system
messages the reply is persisted but never displayed. "Load more" fetches the same page again.

This is independent of 3.1 and is the first thing a user notices.

### 3.3 Prompt structure defeats provider caching and role semantics

`PromptComposer` flattens system instructions, context and history into a single **user**
message (`OpenAIService.java:67` `.user(u -> u.text(userMessage))`). There is no system
message; roles are pseudo-headers (`USER:`/`ASSISTANT:`) inside text. Effects:

- OpenAI's automatic prompt caching only applies to an identical prefix ≥ 1 024 tokens. The
  static part (instructions + CV/job/report JSON) *would* be identical every turn, but it is
  followed in the same message by ever-changing history, so caching still works on the prefix
  — however the prefix is not stable across chats for the same CV/job, and the JSON is
  serialised with all nulls/ids/timestamps (see 3.5).
- The model receives no real role structure, which hurts instruction-following on long
  threads (it will sometimes "continue the transcript" instead of answering).
- The stored `SYSTEM` seed message (`ChatService.java:80-88`, `buildSystemPreamble`) is read
  back as part of `streamForContext` and re-emitted as a `SYSTEM:` line *inside* the history
  section — a second, contradictory system preamble every turn.

### 3.4 Context labels are swapped (correctness bug)

`ScreeningContextProviderImpl.java:47` returns
`new ScreeningContext(jobPostText, cvText, matchingReportText)` while the record is
`ScreeningContext(cvText, jobText, matchingReportText)` (`ScreeningContext.java:3`). The
prompt therefore says `CV:` followed by the job post JSON and `JOB DESCRIPTION:` followed by
the CV JSON. The model usually infers the right thing from field names, but it is a bug, and
it will matter more once context gets trimmed to essentials.

### 3.5 Context block is fatter than it needs to be

`QorvaUtils.toJSON()` (`QorvaUtils.java:34`) serialises the full DTOs with a default
`ObjectMapper`: nulls, `id`/`tenantId`/`createdBy`/`lastUpdatedBy`, timestamps,
`searchIndex`, `candidateClustering`, `attachment`, `atsRefs`, `qualityFlags`,
`scoringRules`, `embedding`-free (good — `embedding` and `rawText` are `WRITE_ONLY`,
`CVDTO.java:50,77`, `JobPostDTO.java:44`). Easily 20–40 % of the context block is noise.

### 3.6 No observability — token usage is faked

`OpenAIResultMapper.java:53-56` hardcodes `promptTokens = 0`, `completionTokens = 0`.
`ChatMessage.tokens` therefore always reads 0, so nobody can see prompt growth from the DB
(the architecture map already noted chat is the *only* feature persisting tokens — and they
are wrong). `OpenAIService.chatCompletions` uses `.call().content()` and discards the
`ChatResponse` metadata that carries real usage.

### 3.7 Failures are invisible to the user

Backend: `ChatAgent.java:37-38` has two `TODO`s (retry, mark failed). Frontend: every
`catch` in `AppAIResumeChat.jsx` is `console.error` only; the optimistic user bubble stays,
the typing indicator stops, nothing else happens.

## 4. Target design

Keep the existing collections and endpoints. Change *what goes into the prompt* and *how
the turn is executed*. Three layers, cheapest first:

```
┌──────────────────────────────────────────────────────────────────────┐
│ system    : persona + rules (+ answer language)                      │  stable per chat
│ system    : CONTEXT — trimmed CV / JOB / REPORT JSON                 │  stable per chat  ← cacheable prefix
├──────────────────────────────────────────────────────────────────────┤
│ system    : CONVERSATION SUMMARY SO FAR (rolling, LLM-written)       │  changes rarely
├──────────────────────────────────────────────────────────────────────┤
│ user/assistant … last N turns within a token budget                  │  sliding window
│ user      : the new question                                          │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.1 Token-budgeted sliding window (replaces `MAX_MSG = 100`)

- Budget in **tokens, not messages**. Estimate with `chars / 4` (good enough for windowing;
  optionally add `com.knuddels:jtokkit` with `o200k_base` for exactness — not required).
- Config (new `qorva.ai.resume-chat.*` block in `application.yml`, env-overridable like the
  other AI settings):

  | key | default | meaning |
  |---|---|---|
  | `model` | `gpt-5.6-sol` | move the hardcoded model here (`gpt-5-chat-latest` was shut down 2026-07-23) |
  | `history-token-budget` | `8000` | max tokens of verbatim recent turns sent to the model |
  | `keep-recent-messages` | `6` | never summarise the last N messages (3 user/assistant pairs) |
  | `summary-trigger-tokens` | `6000` | when un-summarised history exceeds this, compact |
  | `summary-model` | `gpt-4.1-mini` | cheap model for compaction |
  | `summary-max-tokens` | `1200` | cap on summary length |
  | `context-token-warn` | `12000` | log a warning when the CV+job+report block exceeds this |

- Window algorithm (pure function, unit-testable):
  1. Load messages **after** `chat.summary.upToMessageCreatedAt` (or all if no summary),
     `role != SYSTEM`, ASC.
  2. Walk from the newest backwards, accumulating estimated tokens until
     `history-token-budget` is exhausted; always include at least `keep-recent-messages`.
  3. Everything older than the cut is *not* sent — it is represented only by the summary.

### 4.2 Rolling summary (compaction)

- New sub-document on `chats`:

  ```json
  "summary": {
    "text": "…",                       // LLM-written, bullet style
    "upToMessageId": "…",              // last message folded into the summary
    "upToMessageCreatedAt": ISODate,   // window query cut-off
    "messageCount": 42,                // messages covered so far
    "tokens": 900,                     // estimated size of text
    "model": "gpt-4.1-mini",
    "updatedAt": ISODate
  }
  ```
- Trigger: after a successful turn, if estimated tokens of messages *after* the summary
  cut-off > `summary-trigger-tokens` **and** there are more than `keep-recent-messages`
  of them.
- Input to the compaction call: previous `summary.text` (if any) + the messages older than
  the last `keep-recent-messages`. Output: a new merged summary. Prompt outline:

  > You maintain a running summary of a recruiter's conversation with an assistant about one
  > candidate for one job. Merge the PREVIOUS SUMMARY and the NEW MESSAGES into an updated
  > summary. Keep: questions asked and the conclusions reached, facts the recruiter stated
  > about the candidate/process, decisions, open points, the recruiter's language and tone
  > preferences. Drop pleasantries and repeated context. ≤ 300 words, bullet points, same
  > language as the conversation.

- Run it **off the request path**: after the assistant message is saved, hand the
  compaction to a virtual thread / `@Async` executor (same pattern as other background LLM
  work in the repo). The current turn does not wait; the next turn benefits. Guard with a
  per-chat idempotency check (`summary.upToMessageId` ≥ candidate cut → skip) so two
  quick turns do not both compact.
- Failure of compaction is non-fatal: log, keep the old summary, retry on a later turn. The
  window in 4.1 still bounds the prompt even if the summary is stale, because it walks
  backwards from the newest message — the worst case is that some middle history is simply
  absent, not that the request blows up.

### 4.3 Prompt restructuring (`PromptComposer` → real messages)

- Build `List<org.springframework.ai.chat.messages.Message>`:
  `SystemMessage(rules)`, `SystemMessage("CONTEXT\n" + trimmedJson)`,
  optional `SystemMessage("CONVERSATION SUMMARY SO FAR\n" + summary)`, then
  `UserMessage`/`AssistantMessage` for the window. Send with
  `chatClient.prompt().messages(list).options(...).call().chatResponse()`.
- Stop replaying the stored `SYSTEM` seed message inside history (exclude `SYSTEM` in
  `streamForContext`, or drop the seed insert entirely — it is already filtered out of
  `getMessages`).
- Put the answer-language instruction in the rules (`chat.metadata.language` exists but is
  unused by the prompt today).
- Keeping instructions + context as an identical leading prefix across turns lets OpenAI's
  automatic prompt caching hit on the ≥ 1 024-token prefix — free latency/cost reduction
  once the JSON serialisation is deterministic (see 4.4).
- Optional: drop the strict JSON-schema `{content}` response format for chat — it exists only
  to feed `OpenAIChatResponse`, which is a one-field wrapper. Plain text has fewer failure
  modes (no schema refusals) and lower latency. If kept, no change needed.

### 4.4 Slim, deterministic context block

- Replace `QorvaUtils.toJSON(dto)` for chat with a dedicated `ChatContextSerializer`:
  `ObjectMapper` with `Include.NON_EMPTY`, `ORDER_MAP_ENTRIES_BY_KEYS`, and a Jackson
  `@JsonView`/mixin (or explicit projection records) that omits `id`, `tenantId`,
  `createdBy`, `lastUpdatedBy`, `createdAt`, `lastUpdatedAt`, `searchIndex`,
  `candidateClustering`, `attachment`, `atsRefs`, `qualityFlags`, `archived`,
  `matchingReportsNeeded`, `scoringRules` (job), `status`.
- Fix the swapped constructor arguments in `ScreeningContextProviderImpl.java:47`
  (`new ScreeningContext(cvText, jobPostText, matchingReportText)`).
- Log estimated context tokens once per turn; warn above `context-token-warn`.

### 4.5 Turn execution — take the LLM call out of the Mongo transaction

Restructure `postUserMessage` into three steps:

1. `@Transactional` **saveUserMessage** — validate chat, persist USER message, return it.
2. **No transaction** — build window, call `ChatAgent.answer()`.
3. `@Transactional` **saveAssistantReply** — persist ASSISTANT message, touch chat
   (`lastUpdatedAt`, `lastUpdatedBy` — note it currently writes the *email* into a field that
   elsewhere holds a user id, `ChatService.java:139`).

On LLM failure the USER message stays (the recruiter should not lose what they typed);
return `AI_REQUEST_FAILED` and let the UI offer a retry. Optionally persist a placeholder
ASSISTANT message with `metadata.failed = true` so the failure is visible in history and
excluded from future windows.

Safety net for `400 context_length_exceeded` (should be unreachable once 4.1 is in): catch
the OpenAI 400 with that `code`, halve `history-token-budget` for this call, retry once,
then fail.

### 4.6 Real token accounting

- `OpenAIService.chatCompletions` → `.call().chatResponse()`, read
  `getMetadata().getUsage()` (`getPromptTokens()`, `getCompletionTokens()`) and the
  model from `getMetadata().getModel()`; drop the hardcoded zeros in `OpenAIResultMapper`.
- Store on `ChatMessage.tokens` as today. This turns `chat_messages` into the growth
  monitor: `promptTokens` per turn should now plateau instead of climbing.

### 4.7 Frontend (`qorva-ai-app`, same branch name)

1. Fix the param names: `getMessages(chatId, { page, size })`.
2. Show the **latest** messages on open: change the controller sort to `createdAt DESC`
   and reverse in the UI (older pages are *prepended* on "load more"); or keep ASC and
   request the last page (`totalPages - 1`) — DESC is simpler.
3. After `sendMessage`, **append the returned `ChatMessageDTO`** (the POST already returns
   the assistant message) instead of re-fetching page 0.
4. Surface errors: replace the optimistic bubble with an error state + "Retry" on failure
   (`AI_REQUEST_FAILED` → translated message; other codes → generic).
5. Nothing else changes in the contract — no new endpoints.

## 5. Implementation plan (ordered, each step shippable on its own)

| # | Step | Repo | Files | Risk |
|---|---|---|---|---|
| 0 | Fix pagination drift + append reply + error bubble (4.7) | app | `AppAIResumeChat.jsx`, `chatService.js`; `ChatController.getMessages` sort | none — pure bug fix, do first |
| 1 | Fix swapped context args (4.4) + real usage (4.6) | ai | `ScreeningContextProviderImpl`, `OpenAIService`, `OpenAIResultMapper` | none |
| 2 | Move LLM call out of the transaction (4.5) | ai | `ChatService` | low — split into 3 methods; note Spring self-invocation: call the tx methods through a separate bean or `TransactionTemplate` |
| 3 | Config block + token-budgeted window (4.1) | ai | `application.yml`, new `ResumeChatProperties`, new `ConversationWindowBuilder` (pure, tested) | low — immediately bounds the prompt |
| 4 | Real-message prompt + slim context (4.3, 4.4) | ai | `PromptComposer` → `ResumeChatPromptBuilder`, new `ChatContextSerializer`, `OpenAIService.chatCompletions(List<Message>)` | medium — answer quality must be spot-checked on a few real chats |
| 5 | Rolling summary (4.2) | ai | `Chat.summary`, new `ChatSummaryService` + `ChatSummarizerAgent`, Mongock changeunit, DDL json | medium — background LLM call, needs the idempotency guard |
| 6 | Observability | ai | per-turn structured log line: `chatId, contextTokens, summaryTokens, historyTokens, sentMessages, promptTokens(actual), completionTokens, latencyMs` | none |

Steps 0–3 alone remove the failure. Steps 4–5 are what keep long chats *good* instead of
merely not failing (without the summary, the model forgets everything past the window).

### Mongock / schema

`chats` has a strict validator (`db/migrations/20260514_06__create_chats_collection.json`)
without `additionalProperties: false`, so an undeclared `summary` field would technically be
accepted — but per repo convention every changed collection gets a changeunit. Add
`V2026MMDDnnUpdateChatsCollectionSummary` + `2026MMDD_nn__update_chats_collection.json`
declaring the `summary` object (all fields optional, `upToMessageCreatedAt` / `updatedAt` as
`date`). No data backfill: existing chats simply start with no summary and compact on their
next turn. Rollback restores `20260514_06__create_chats_collection.json`.

`chat_messages` is unchanged unless the optional `metadata.failed` flag is added — if it
is, declare it the same way.

### Tests to add (`src/test/java/ai/qorva/core/service/` — there are none for chat today)

- `ConversationWindowBuilderTest`: budget respected, `keep-recent-messages` honoured even
  when over budget, summary cut-off respected, SYSTEM excluded, empty history.
- `ChatContextSerializerTest`: noise fields absent, output deterministic, nulls dropped.
- `ChatServiceTest` (mock agent): user message survives an agent exception; assistant saved
  and chat touched on success; summary trigger fires only past the threshold and is
  idempotent.
- `ChatSummaryServiceTest`: merges previous summary + slice, advances `upToMessageId`,
  swallows LLM failure.

## 6. Non-goals / later

- **Streaming** the reply (`.stream()` + SSE) — biggest UX win for perceived latency but
  a contract change on both sides; not needed to fix the failure.
- Retrieval over the transcript (embedding old turns and pulling relevant ones back in)
  — overkill for a single-CV/single-job chat; the summary covers it.
- Raising `OpenAiHttpClientConfig` timeouts — the client is shared by extraction and
  report generation; with a bounded prompt the chat turn fits comfortably in 60 s. If
  needed later, give chat its own `RestClient` rather than touching the shared one.
- Migrating to Spring AI `ChatMemory`/`MessageWindowChatMemory` — it windows by message
  count only and would duplicate the existing `chat_messages` persistence; a custom window
  over our own collection is less code.

## 7. Verification checklist

1. Seed a chat with 120 messages (mix of 200- and 1 500-token answers) on tst; send a turn;
   confirm the request completes < 15 s and `promptTokens` on the stored ASSISTANT message
   is ≤ context + `history-token-budget` + summary.
2. Send 10 more turns; confirm `chat.summary.upToMessageId` advances and `promptTokens`
   stays flat.
3. Kill OpenAI connectivity for one turn: USER message persists, UI shows retry, next turn
   works.
4. Open a chat with > 50 messages in the app: latest messages visible, "load more" shows
   older ones, reply appears after send without refetch.
5. Compare answers on 3 real chats before/after step 4 for regressions in quality/language.
