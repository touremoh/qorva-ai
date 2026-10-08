---
routes: []
requires: []
---
# Troubleshooting

**A resume upload fails.**
Only .pdf and .docx files are accepted; other types are ignored. "The file is empty", "Could not read the PDF/Word file" or "The resume content could not be read" mean the file is empty, corrupted, or has no readable text (for example a scanned image). Open it on your computer, save it again as PDF or DOCX, and re-upload. If you selected more files than your plan allows per upload, only the first ones are kept. If files were "skipped — your plan's screening limit was reached", see Usage.

**I don't see a menu item or button, or get "You do not have permission to perform this action."**
Three possible reasons: (1) you lack the permission — ask a user with Manage Users to update your permissions in **Account Settings → Users**; (2) the feature is not included in your plan (for example some Top N options say "Available on a higher plan", notes may be read-only); (3) the feature is not available for your account right now (for example Copilot rules). Copilot only appears with the "Use Copilot" permission.

**My ATS integration shows Auth error.**
The ATS no longer accepts the credentials: the token expired (Workable tokens have an expiry date), was revoked, the user who created it lost access, or the ATS plan changed (Manatal deactivates tokens on downgrade). Create new credentials in the ATS, click **Disconnect**, then **Connect** again with the new values. For Greenhouse, only Harvest v3 credentials work.

**The ATS sync is not importing candidates.**
Check that **Automatic import** is on, then click **Sync now** and look at **Recent syncs**. "awaiting confirmation" means the first import reached its safety limit — click **Import the rest**. "plan quota reached" means your screening allowance is used up for the period. Make sure the credentials have read access to candidates (and jobs if **Import jobs** is on).

**Real-time updates (webhook) are not active.**
For Workable, Lever, Manatal and Ashby, click **Retry setup** on the card (for Ashby, the key needs the apiKeysWrite permission; for Lever, paste the Lever signing token). For Greenhouse, Recruitee and Zoho Recruit, set up the webhook in your ATS using the Webhook URL (and secret) from the card. For Zoho, the workflow rule must be active. BambooHR has no applicant webhooks. Without webhooks, Qorva still syncs every 30 minutes.

**Copilot says a limit was reached.**
Your plan's allowance for that Copilot meter is used up for the period. Check **Usage**. It resets at the start of the next period, or you can change plan in **Billing**. When tasks are used up, questions about candidates and the library still work.

**Copilot is "already working on one of your tasks".**
Copilot runs one task per user at a time. Wait for it to finish or click **Cancel**.

**Emails are not sending.**
If you see "Your mailbox connection has expired" or **Reconnect needed**, reconnect your Microsoft 365 mailbox in **Account Settings → My Profile**. If no mailbox is connected, the composer opens Gmail, Outlook or your mail app instead — send from there. "This candidate has no email address on file" — add one to the resume or type an address. If a candidate asked not to be contacted, sending is disabled. If your organisation blocks the Microsoft consent, ask your IT admin to approve Qorva.

**I was signed out / "Your session expired".**
Sessions expire for security. Sign in again; your saved work is safe. If it happens right after signing in, clear your browser's site data for Qorva and try again.

**The language did not change.**
Change it in **Account Settings → My Profile** (selector at the top). The choice is stored in your browser, so it must be set again on another browser or device, or after clearing browser data. Reload the page if some text stays in the old language.

**I did not receive my verification code (two-step verification).**
Check your spam folder, wait a moment, then click **Resend code**. Use the most recent code. Make sure you check the inbox of your Qorva email.

**A screen shows "Something went wrong".**
Click **Reload**. Your saved work is safe. If it keeps happening, contact support.
