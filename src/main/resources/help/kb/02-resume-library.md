---
routes: [cvs]
requires: []
---
# Resume Library

The **Resume Library** holds every candidate profile. Each resume is analysed by AI and shown as a structured profile.

## Uploading resumes
Accepted formats: **.pdf** and **.docx** only. Other files are ignored ("unsupported file(s) ignored — only .pdf and .docx are accepted"). You need the "Add Resume" permission.
1. Open **Resume Library** and click **Bulk Upload Resumes**.
2. Drag & drop files into the box, or click to browse.
3. Click **Upload Files**.

How many files you can upload at once depends on your plan (the dialog says "Upload up to N resumes"); see the plan limits section. If you pick more, only the first N are kept.

**Small batches (up to 20 files)** are processed right away. You see the progress phases (uploading, extracting text and contact details, parsing experience and skills, structuring profiles, clustering, checking for duplicates), then **Upload results** for each file:
- files that could not be processed,
- warnings such as missing phone, missing email, no contact info, no work experience, low AI confidence,
- possible duplicates ("Matches existing … (added …)") with **Replace old version**, **Keep both** or **Replace all old versions**.

**Larger batches** run as a background import. Files are uploaded first ("Uploading files… X of Y"), then analysed on Qorva's servers ("Importing resumes… X of Y"). You can click **Continue in background** and keep working; a progress chip ("Importing X / Y", with an estimated time left) stays visible, and new resumes keep appearing in the library even if you close the window. You can **Cancel import**. Very large imports ask you to confirm first, because each file uses one screening action. At the end you see how many were imported, failed or skipped. Files skipped because your plan's screening limit was reached are listed as such. Possible duplicates from a bulk import are flagged in **Data Health**.

## What extraction does
AI reads the file and fills in: name, contact information (phone, email, LinkedIn, GitHub, website), availability (open to work, notice period, start date, preferred work and contract types, relocation), summary, key skills, work experience, education, certifications, technical and soft skills, languages, projects, interests and references. The **Talent Intelligence** tab adds a candidate clustering: functional expertise, skill depth (generalist, specialist, T-shaped, hybrid), seniority, leadership, learning velocity, industry domains and business impact, with the reasoning. You can open the original file with **View File** or **Download Resume**.

## Searching and filtering
- Use **Quick search** for a name, role or skill.
- Click **Filters** to filter by industry, location, skills (has all of), tags, years of experience, seniority, leadership, availability, skill depth, source (uploaded manually or a connected ATS) and dates (Added since, Updated since).
- **Sort by**: Last updated, Newest first, Name A–Z, Most experience.
- For fuzzier searches ("all finance-related profiles", a degree level, a past employer), click **Ask Copilot**.

## Tags and notes
- **Tags**: open a resume, edit the Tags section, type in "Add a tag…" and click **Update Tags**. Tags can be used as filters here and in Match Reports. Copilot can also tag candidates.
- **Notes**: add notes for your team on a candidate. Everyone in your company sees them; edited notes are marked "edited". On some plans notes are read-only ("Upgrade to add notes for your team").

## Blind CV
Click **Blind CV** to hide the candidate's identity and contact details (shown as "Anonymous Candidate"); **Reveal Identity** shows them again.

## Archiving and deleting
- Archived resumes are excluded from matching and quality reporting. You can archive resumes in bulk from **Data Health** (**Archive all** on an issue). To see archived resumes, click **Archived** in the library toolbar; use **Unarchive** to restore one.
- **Delete Resume** permanently removes a resume and its related data (match reports, notes, conversations). Needs the "Delete Resumes" permission.
- **Clear the whole library** (trash icon in the toolbar) permanently deletes all resumes, matching reports and Copilot conversations. Job posts and usage history are kept. You must type DELETE to confirm. This cannot be undone.

## Duplicates
Qorva checks every new resume for duplicates. Small uploads let you decide immediately; all duplicate groups are listed under **Data Health → Uniqueness**, where you can delete the extra copies.

