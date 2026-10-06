---
routes: [settings.profile, cvs, reports, configuration.email-templates]
requires: []
---
# Emailing candidates

You can write to candidates from Qorva. You need the **Email candidates** permission.

## Where to start an email
- **Resume Library**: the resume's menu or detail view → **Email candidate**.
- **Match Reports**: open a report → **Email candidate**. Emailing from a report moves a candidate in New to Contacted on the Pipeline.
- **Copilot**: ask it to draft an email. Copilot drafts it; sending waits for your approval (**Approve and send**, or **Edit in email composer**).

The composer opens as a panel you can minimize. It shows when the candidate was last contacted and by whom, and a **History** of earlier emails (sent from Qorva, opened in Gmail/Outlook/mail app, failed, or via Copilot).

## Writing the message
1. Check **To**. If the candidate has no email on file, type an address or add one to the resume.
2. Write the **Subject** and **Message**, or use **Draft with AI**: choose the purpose (Introduce an opportunity, Invite to interview, Follow up, Keep in touch, Custom), the tone (Professional, Formal, Friendly, Short) and the language (English, Français, Deutsch, Español, Italiano, Nederlands, Português), optionally add what to mention, and click **Generate**. Use **Regenerate** or **Make it shorter**.
3. Send:
   - With a connected Microsoft 365 mailbox, click **Send**. The email goes from your own address and a copy is saved in your Sent folder.
   - Without one, choose a mail app: **Open in Gmail**, **Open in Outlook** or **Open in my mail app**. The message opens prefilled there and you send it from that app. If the message is too long for a compose link, use **Copy** and paste it into a new email.

If a candidate asked not to be contacted again, sending is disabled.

## Connecting a Microsoft 365 mailbox
Only Microsoft 365 mailboxes can be connected for sending from Qorva. Using Gmail or another mail app? There is nothing to connect — the composer opens your mail app with the message prefilled.
1. Open **Account Settings → My Profile** (avatar menu, top right).
2. In **Connected mailbox**, click **Connect Microsoft 365**.
3. Sign in with your work Microsoft account and accept.
The card shows **Connected**, since when, and when it was last used. Qorva can only send emails you write in the composer, as you. It cannot read your mailbox.

**Your organisation blocked the consent?** Some Microsoft 365 organisations only let an administrator approve new apps. Ask your IT admin to approve "Qorva" for your organisation (they find the request in Microsoft Entra › Enterprise applications › Admin consent requests), then connect again.

**Reconnect needed**: Microsoft no longer accepts the stored connection (password change, revoked access or expired consent). Click **Reconnect**.

**Disconnect**: Qorva stops sending as you and the composer opens your mail app instead. You can also revoke Qorva's access from your Microsoft account security page.

## Email templates (profile update requests)
**Configuration → Email Templates** holds the invitation emails candidates receive when you request a profile update from **Data Health** (see Data Health). They are not used by the candidate email composer.
1. Click **New template**, enter a **Template name**, **Subject** and **Message** (plain text; blank lines start a new paragraph). Use **Insert:** for placeholders.
2. Use **Preview** and **Send test to me** to check it, then **Save**.
The greeting, action button, your signature (name and company) and the unsubscribe link are added automatically. If you have no template, the standard Qorva message is used. The number of templates you can save depends on your plan ("N of M templates used").
