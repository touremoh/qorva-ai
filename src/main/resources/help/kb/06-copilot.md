---
routes: [copilot, copilot.activity]
requires: []
---
# Copilot

Copilot is Qorva's AI assistant. Ask it about one candidate or your whole library, or give it a task in plain words. It answers from the full resume, the job and its match report, analyses your library with charts, and can tag candidates, add notes, move candidates on the pipeline, create or update jobs and draft emails. Sending an email, running matching or starting an ATS import always waits for your approval. Copilot never deletes anything.

You need the "Use Copilot" permission; otherwise the **Copilot** menu item is hidden. Copilot has three tabs: **Chat**, **Activity** and **Rules**.

## Asking questions
1. Open **Copilot** (or click **New task** to start a new conversation).
2. Type a question or a task. Type **@** to pick a candidate and **#** to pick a job.
3. Press Enter. Copilot shows the steps it took (for example "Searched the resume library: 12 found", "Read the match report for …") and links to the candidates, jobs and reports it used.

You can also click **Ask Copilot about this candidate** on a resume, or **Ask Copilot about this candidate for this job** on a match report. The conversation is then focused on that candidate ("About … · …"). In the Resume Library, **Ask Copilot** under Filters helps with searches the filters cannot do.

Examples: "Who are the 5 best-matched candidates for our newest open job, and why?", "How many senior Java developers open to work do we have in Lisbon?", "Compare @Alice and @Bob against #Senior Java Developer."

## Library analyses
Questions about your whole library return counts, charts (for example seniority, skill depth, leadership, learning velocity, top skills, rare skills) and candidate cards. Typical analyses: talent pool snapshot, clustering, ranking for a role, rediscovery of past candidates, skill gaps, skills distribution, candidate comparison, location and salary analysis.

## Tasks Copilot can do
Tag or untag candidates; add a note to a profile; move candidates to a pipeline status; create or update a job post; draft an email (not sent until you approve); run matching (waits for approval, shows the cost); start an ATS import (waits for approval); create a standing rule (see Copilot rules).

## How Copilot helps automate your workflow
- After a bulk upload: "Tag every new candidate with Python and 5+ years as python-senior."
- Shortlisting: "Move the top 5 candidates for #Data Engineer to Shortlisted and draft an interview invitation for each."
- Keeping results fresh: "Run matching on all out-of-date jobs, top 10 each."
- Follow-ups: "Draft a follow-up email for candidates in Contacted on #Product Manager."
- Standing rules: "Whenever a candidate scores 80+ on a job, draft an invitation."

## Approvals
When a task needs approval, the task shows **Needs approval** and the Copilot menu shows a badge. You also get a notification ("Copilot needs your approval").
- **Send this email?** — check To, Subject and Message. Click **Approve and send**, **Edit in email composer**, or **Reject**.
- **Run matching?** — shows the jobs, the cost ("Up to N matching actions"), what is left, and unchanged reports reused for free.
- **Start an ATS import?** — shows the integration; runs in the background.
You can add an optional reason when rejecting; Copilot sees it. **Approve all (N)** approves several at once. Each approval shows when it expires; if nobody decides in time, the task stops ("Expired: an approval was not given in time").

## Activity tab
**Activity** lists Copilot tasks with their status (Queued, Working, Needs approval, Done, Failed, Cancelled, Expired) and when they started. Filter by **Status** and **Started from** (Chat or Rules, or one rule's tasks). Click a task to see its steps. **My tasks** shows yours; **Team** shows everyone's and is only available to users who can manage users.

## Limits
- Copilot works on one of your tasks at a time; wait for it to finish or **Cancel** it.
- A task must be 1 to 2,000 characters. Very broad tasks may stop early ("needed more steps than allowed") — try a narrower task.
- Usage is counted on three meters: candidate questions, library analyses and tasks. Allowances depend on your plan (see the plan limits section and the **Usage** page). When the task allowance is used up, new tasks are refused until the period renews, but questions still work. Actions Copilot takes, such as matching, also count against their own allowance.

## Conversations
Your conversations are listed on the left. **Delete conversation** removes its tasks and answers but changes nothing Copilot looked at. A conversation with a task in progress must be cancelled first.

## Accuracy
Copilot reads your data to answer. It can make mistakes, so check important results before acting on them.
