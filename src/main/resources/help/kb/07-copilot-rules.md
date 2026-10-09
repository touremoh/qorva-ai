---
routes: [copilot.rules]
requires: [agent.rules]
---
# Copilot rules

A rule starts a Copilot task by itself when something happens, as you. Emails and other actions still wait for your approval. Rules only act on what happens from the moment they are created; nothing that already exists is processed.

## How to create a rule
1. Open **Copilot → Rules** and click **New rule**.
2. **Name**: give it a name (up to 100 characters).
3. **When**: choose a trigger and its options (see below).
4. **What Copilot does**: describe the task in plain words (up to 2,000 characters). Use **Insert:** to add placeholders that are filled in when the rule fires: **Candidates**, **Job**, **Count**, **Integration**. Example: "Draft an interview invitation for {{candidates}} for {{job}}."
5. **Tasks per day (max)**: 1 to 100 (default 20). Beyond it, the rule waits until tomorrow.
6. Optional: tick **Run matching without asking me** and set **Max actions per matching** (1 to 500). A matching that would cost more still waits for your approval.
   You can also tick **Send update requests without asking me** and set **Max candidates per request** (1 to 25): Copilot then asks candidates to update their profile without waiting for you; larger batches still wait.
7. Click **Create rule**.

## Triggers and their options
- **A candidate is added** — Candidates: **Uploaded or imported**, **Uploaded only**, or **Imported from the ATS only**.
- **A candidate is scored on a job** — Job (a specific job or any open job); **Minimum score** 0–100 (leave empty for any score); **Only when recommended for interview**.
- **On a schedule** — Repeat **Every day** or **Every week**; Day (for weekly); Time. The time zone is your browser's.
- **An ATS import finishes** — Integration (a specific one or any integration).
- **A job needs matching** — Job (or any open job); **Because**: New job, Job changed, New candidates would rank, Candidate profiles changed (at least one).
- **A candidate's status changes** — Job (or any open job); **Statuses that start the rule** (at least one of New, Contacted, Shortlisted, Interviewing, Offered, Hired, Rejected, Withdrawn).
- **A candidate is scored on a job** can also be narrowed to a **Recommendation** (Strong Interview, Interview, Maybe, Reject) and a **Maximum score** — for example only rejected candidates, or scores between 40 and 60.
- **A candidate stays in a status** — **Statuses** to watch (at least one) and **Days without change** (1 to 90); Job (or any job). It starts once per waiting period: when the candidate moves again, the count starts over. A candidate never moved out of New counts from when their report was created.
- **A CV becomes outdated** — **Age** of the CV's content: 6, 12, 18 or 24 months (18 is what Data Health calls Outdated); Candidates: uploaded, imported or both. CVs that were already outdated when you create the rule are not included: use **Data Health → Request updates** for those.
- **A job is closed** — Job (or any job). The task lists the candidates still waiting on that job (New, Contacted or Shortlisted).
- **A duplicate is found** — a new candidate has the same email or phone as an existing one; Candidates: uploaded, imported or both. The task mentions both profiles.
- **A candidate updates their profile** — a candidate completed a profile-update request (from Data Health or from Copilot).

## What waits for approval
The rule runs as you. Sending emails, profile-update requests, ATS imports and matching (unless pre-approved above) wait for your approval for up to 72 hours. You get an email when something waits, and the Copilot menu shows a badge. If nobody approves in time, the task expires. Tagging, notes, drafts and status moves do not need approval.

## Managing rules
Each rule shows its trigger, whether it is **Active** or paused, tasks today against its daily limit, tasks skipped today because of the limit, the last task and (for schedules) the next run.
- **Pause** / **Resume** a rule.
- **Edit** to change any setting, then **Save**.
- **Delete** stops it. Tasks it already started stay in **Activity**.
- **See its tasks** opens Activity filtered to that rule.
**My rules** shows yours; **Team** shows everyone's rules with their owner and is available to users who can manage users. Your company has a maximum number of rules.

## Why a rule is paused
- **Paused** — someone paused it.
- **Paused: owner can't use Copilot** — the owner lost the Copilot permission or was removed.
- **Paused: no Copilot tasks left** — the plan's task allowance is used up for the period.
- **Paused: subscription inactive**.
- **Paused: job deleted** — the job it watches was removed.
- **Paused: integration removed** — the ATS integration it watches was disconnected.
Fix the cause, then click **Resume**.

## Creating a rule from the chat
Ask Copilot, for example: "Whenever a candidate scores 80+ on a job, draft an invitation." Copilot proposes the rule (**Create this rule?** with Name, When, Task and Limit). Approve it to create it. You can then pause or edit it in **Copilot → Rules**.

## Rules count against your plan
Each task a rule starts counts once on the Copilot tasks meter. Anything it does along the way, such as a matching run, also counts on its own meter.

## Example rules
1. **Invite strong matches** — When: A candidate is scored on a job; Minimum score 80; Only when recommended for interview. Task: "Draft an interview invitation for {{candidates}} for {{job}}."
2. **Tag new uploads** — When: A candidate is added; Uploaded or imported. Task: "Tag {{candidates}} with their main skill area and seniority."
3. **Keep matching fresh** — When: A job needs matching; any open job; all reasons; Run matching without asking me, max 50 actions. Task: "Run matching for {{job}}, top 10."
4. **Monday shortlist digest** — When: On a schedule; Every week, Monday, 09:00. Task: "List the 5 best-matched candidates for each open job and tag them weekly-shortlist."
6. **Reject clearly unsuitable candidates** — When: A candidate is scored on a job; Recommendation: Reject. Task: "Move {{candidates}} to Rejected."
7. **Nudge stalled candidates** — When: A candidate stays in a status; Statuses: Contacted; 7 days. Task: "Draft a follow-up email for {{candidates}} about {{job}}."
8. **Keep profiles fresh** — When: A CV becomes outdated; 18 months; Send update requests without asking me. Task: "Ask {{candidates}} to update their profile."
9. **Interview plan on shortlist** — When: A candidate's status changes; Statuses: Shortlisted. Task: "Prepare a technical interview plan for {{candidates}} for {{job}} and save it as a note." The plan is saved as a note on the candidate's match report.
5. **Follow up after interviews** — When: A candidate's status changes; Statuses: Interviewing. Task: "Draft a follow-up email for {{candidates}} about {{job}}."
