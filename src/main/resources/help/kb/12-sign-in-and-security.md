---
routes: [settings.profile, settings.company]
requires: []
---
# Sign-in and security

## Signing in
Sign in with your **Work Email** and **Password**, or click **Sign in with Microsoft**.
- "Incorrect email or password" — check both, or reset your password.
- "No active Qorva account uses this Microsoft identity" — your administrator must invite you with the same work email as your Microsoft account.
- "Your account is not active" or "Your subscription is not active" — the company subscription has ended or a payment failed; an account owner can fix it in **Billing**.

## Forgot your password
1. On the sign-in page, click **Forgot password?**
2. Enter your work email and click **Send reset link**.
3. Open the email and choose a new password. The link is valid for 1 hour.
If no email arrives, check your spam folder and that you used the email of your Qorva account.

## Changing your password
Open **Account Settings → My Profile**, click **Change Password**, enter the current password and the new one twice, and save.

## Two-step verification (email code)
Two-step verification asks for a 6-digit code sent to your email each time you sign in, on top of your password.
1. Open **Account Settings → My Profile**.
2. In **Two-step verification**, click **Turn on**.
3. Enter the 6-digit code sent to your email and click **Confirm**.
To turn it off, click **Turn off** and confirm with a code. When you sign in, enter the code from the email ("Check your email"). Didn't get it? Check your spam folder, or click **Resend code** (available after a short countdown).

## Requiring Microsoft sign-in for your company
Users who can manage users can require everyone to sign in with their company Microsoft account.
1. Open **Account Settings → Company**.
2. In **Microsoft sign-in**, switch on **Require Microsoft sign-in** and click **Require it**.
Your teammates can then no longer sign in with a password; make sure they all use a company Microsoft account with the same email as in Qorva. The account owner keeps their password as a way in if Microsoft sign-in ever fails.

## Sessions
For security, your session expires after a period. You then see "Your session expired. Please sign in again." Sign in again; saved work is kept. Use **Sign Out** in the avatar menu on shared computers.

## Data security and privacy
- **Encryption**: all traffic uses TLS. Data is encrypted at rest. Credentials you give Qorva for integrations (ATS keys, mailbox connections) are additionally encrypted before they are stored.
- **Tenant isolation**: each company's data is kept in its own tenant, and every query is limited to the signed-in user's company.
- **Permissions**: owners decide what each team member can view, create, modify or delete.
- **Passwords** are stored as one-way hashes, never in clear text. Email two-step verification is available to every user.
- **Hosting**: Qorva runs on Amazon Web Services in the United States, with the database in the same region. Subprocessors are listed on Qorva's public Security page.
- **Deletion**: deleting a candidate or job also deletes its related data (match reports, notes, conversations). Users with permission to delete resumes can clear the whole library. Deleting a company account is done on request.
- **GDPR**: Qorva processes candidate and user data on behalf of its customers, who remain the data controllers. A Data Processing Agreement (DPA) is available on request — contact support.
- Candidate data is never sold or shared with third parties other than the listed subprocessors.
- Qorva does not currently hold SOC 2 or ISO 27001 certification.
- Microsoft 365 mailbox connections can only send emails you write; Qorva cannot read your mailbox.
