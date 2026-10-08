---
routes: [reports]
requires: []
---
# Matching and match reports

Matching scores candidates from your library against an open job, using the job's matching criteria. The result for each candidate–job pair is a **match report**.

## Running matching
You need the "Generate Reports" permission.
1. Open **Match Reports** and click **Run Matching**.
2. Select the jobs to match (search with **Search open jobs**). Each job shows when it was last matched and its Top N, or "Never matched". Jobs with out-of-date results are flagged **Out of date** with the reason. Use **Select all out-of-date jobs** to pick them all. You can select up to 50 jobs per run.
3. Choose **Candidates per job** (Top 5 up to Top 30). Higher values may show "Available on a higher plan".
4. Read the cost estimate: how many candidates, how many new or changed reports, how many unchanged reports are **reused for free**, and how many screening actions it uses and how many are left this period.
5. Click **Run matching**.

Matching runs in the background; you can leave the page. When it finishes you see how many reports were new or updated and how many were reused. If it says it stopped early, you ran out of screening actions for the period. If some candidates could not be scored or jobs were skipped, run it again to finish. Jobs that are still being indexed (just created) are skipped; try again a minute later.

**Only new or changed reports are charged.** A candidate already scored on an unchanged job, with an unchanged resume, is reused at no cost.

## When results are out of date
A job's results go out of date when:
- it was never matched,
- the job changed since the last run,
- new candidates would enter the top N,
- candidate profiles changed since the last run.
A banner shows how many jobs have out-of-date results ("Matching results are up to date" when none).

## Outdated reports
A report becomes **Outdated** when the candidate is no longer among the job's latest top candidates, or is no longer eligible (archived, or outside the job's availability rules). Outdated reports stay until you delete them. Use **Hide outdated** to hide them, or **Delete outdated (N)** for a job — this also deletes their notes and chats and cannot be undone.

## What a match report contains
- **Final Score** and sub-scores: Skills Match, Experience Match, Location Match, Industry Match. After a re-run, a change since the last run is shown ("was X%").
- **Matching Skills** and **Missing Skills**.
- **Strengths**, **Weaknesses** and **Red Flags**.
- A **Suggested interview question**.
- A recommendation: **Strong Interview**, **Interview**, **Maybe** or **Reject**, and a confidence (High, Medium, Low).
- The candidate profile and clustering, notes, and the **Candidate status**.
From a report you can **Email candidate**, **Download Resume**, and **Ask Copilot about this candidate for this job**.

## Finding reports
Filter by job (**Filter by Job**), tags, **Recommendation**, **Confidence** and **Status**; search; sort by score (High to Low or Low to High). You can rename or delete a report (needs the edit/delete reports permissions).

## Exporting
Select a job, then click **Export CSV** to download that job's reports as a CSV file.

## Candidate status
Every report has a status: New, Contacted, Shortlisted, Interviewing, Offered, Hired, Rejected or Withdrawn. Change it from the report (status chip) or on the **Pipeline** board. The report shows who changed it and when (in the app, by email, or by Copilot).

## ATS write-back
If your ATS integration has **Send scores to the ATS** turned on, Qorva posts the match score as a note on the candidate in your ATS (where the ATS supports it).
