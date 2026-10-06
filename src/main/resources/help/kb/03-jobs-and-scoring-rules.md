---
routes: [jobs]
requires: []
---
# Jobs and scoring rules

A job post holds the job description and the **matching criteria** (scoring rules) Qorva uses to score candidates. Jobs can be created by hand, by Copilot, or imported from a connected ATS (when **Import jobs** is on for the integration).

## Creating a job
You need the "Add Job Post" permission.
1. Open **Jobs** and click **New Job Post**.
2. **Step 1 – Basic Info**: enter the **Job Title** and the **Job Description**.
   - To let AI write the description, turn on **Generate the description with AI**, fill in any of Seniority, Contract type, Location / remote, Must-have skills (comma-separated) and "Anything else the description should mention", then click **Generate draft**. The draft lands in the editor for you to review and edit.
3. Click **Next**. AI drafts scoring rules from the description ("AI is drafting your scoring rules…"). Review and adjust them before saving. AI-suggested rules use screening actions.
4. **Step 2 – Matching Criteria**: adjust the rules (see below). Click **Save**, or **Skip** to save the job without scoring rules. Use **Back** to return to step 1.

## Matching criteria
- **Skills**: click **Add Skill**, then set the Skill Name, Importance (**Mandatory**, **Important**, **Nice to Have**), Weight, Min. Years, and **Exact Match** if only that exact skill counts.
- **Experience Requirements**: Min. Years of Experience, Min. Relevant Years, Seniority Level (Junior, Mid-Level, Senior).
- **Location Preferences**: Allowed Locations, **Remote Allowed**, Strictness (Strict, Medium, Relaxed).
- **Industry Preferences**: Preferred Industries.
- **Scoring Weights**: sliders for Skills, Experience, Location and Industry. They must add up to 100%.
- **Candidate Filters**: **Open to Work Only** excludes candidates who are not open to new opportunities; **Availability Statuses to Match** (Actively Looking, Open but Not Searching, Not Available, Freelance Only).

## Viewing and editing a job
Select a job in **Job Openings** to see its details (description and scoring rules). Click **Edit Job Post** to change the title, description or matching criteria, then **Update Job Post**. Editing needs the "Edit Job Posts" permission. **Copy reference** copies the job's reference.

When you change a job, its existing match results become out of date ("Job changed since the last run"). Run matching again to refresh them.

## Open and closed jobs
Each job is **Open** or **Closed**. Use the status switch in the job details to change it. Only open jobs can be selected in **Run Matching** and are watched by Copilot rules that use "any open job". Closing a job keeps its reports.

## Removing a job
Click **Remove Job Post** and confirm. Needs the "Delete Job Posts" permission. Copilot rules tied to a deleted job are paused ("Paused: job deleted").
