---
routes: [settings.integrations]
requires: []
---
# Connecting your ATS

Qorva works without an ATS: you can always upload PDF or DOCX resumes. If you use an applicant tracking system, you can connect it to import candidates and jobs automatically and push match scores back. ATS integrations are included in every plan; you need an active subscription to connect. How many integrations you can connect at once depends on your plan (see the plan limits section); when the limit is reached, the **Connect** buttons are disabled.

## Who can do it
You need the **Manage ATS integrations** permission (Account Settings → Users → Manage Permissions → ATS Integrations). Other users see the last sync status in the Resume Library ("Synced with … · …" or "Last sync with … failed").

## Connecting
1. Open **Account Settings → Integrations** (avatar menu, top right).
2. Find your ATS and read **Before you connect** on its card (the steps for your ATS are below).
3. Click **Connect**, paste the requested values, and confirm. For Zoho Recruit you sign in to Zoho instead.
4. Click **Test** to check the connection ("Connection works.").
5. Click **Sync now** to start the first import. New candidates appear in your Resume Library.

## Settings per integration
- **Automatic import** — check the ATS for new candidates every 30 minutes.
- **Import jobs** — create Qorva job posts from open ATS jobs.
- **Send scores to the ATS** — post the match score as a note on the candidate (where the ATS supports it).

## Real-time updates (webhooks)
Qorva checks your ATS every 30 minutes. With a webhook, updates arrive as they happen.
- For **Workable**, **Lever**, **Manatal** and **Ashby**, Qorva sets up real-time updates itself. The card says "Real-time updates are active" when it worked. If it says Qorva could not set them up, click **Retry setup**, or follow "Or set it up yourself" where offered.
- For **Greenhouse**, **Recruitee** and **Zoho Recruit**, copy the **Webhook URL** (and **Webhook secret** where shown) from the card into your ATS (steps below). Treat both like passwords.
- **BambooHR** has no applicant webhooks; Qorva syncs every 30 minutes and you can click **Sync now** at any time.
Real-time updates need Qorva's address to be publicly reachable; the card warns you if it is not.

## First import safety limit
The first import stops at a safety limit so a large ATS does not use your whole allowance by surprise. The card then shows "awaiting confirmation". Click **Import the rest** to continue. Each new resume imported uses one screening action. If your plan's allowance runs out, the import stops with "plan quota reached"; it can continue once the period renews or your plan changes.

## Status and recent syncs
- **Connected** — working.
- **Auth error** — the ATS refused the credentials (expired, revoked or wrong key). Create new credentials in your ATS and connect again.
- **Paused** — syncing is switched off.
**Recent syncs** lists each run with imported, skipped and failed counts.

## Disconnecting
Click **Disconnect** and confirm. Imported CVs stay in your library. Copilot rules that watch this integration are paused.

## Greenhouse
You create a Harvest v3 credential. You need permission to manage your organisation's API credentials. Fields: **Client ID**, **Client secret**, and optional **Greenhouse user ID** (only needed to post score notes back).
1. In Greenhouse, open Configure → Dev Center → API Credential Management and click Create new API credentials.
2. Set API type to Harvest v3 (OAuth) and Partner to Unlisted vendor, and describe the credential as Qorva AI.
3. Create it while signed in as an integration system user rather than a personal account, so the connection keeps working if that person leaves.
4. Grant read access to candidates, applications, jobs, job posts, users, departments and offices — and nothing beyond that.
5. Copy the client ID and the client secret and paste them in Qorva. Greenhouse shows the secret only once.
6. Optionally add the Greenhouse user ID if you want match scores written back as notes.
Note: Harvest v1 and v2 no longer accept requests, so an older Harvest API key cannot connect. Create a Harvest v3 credential even if you already have a key.
Real-time updates: in Greenhouse open Configure → Dev Center → Web Hooks; create a webhook named Qorva AI and choose the candidate event; paste the Webhook URL into Endpoint URL and the secret into Secret Key; save (Greenhouse sends a test call that Qorva answers); repeat for each event you want to follow.

## Recruitee
You need your **Company ID** and a personal API token (**API key** field). The token has the permissions of whoever creates it, so use a stable account.
1. In Recruitee, open Settings → Apps and plugins → API tokens, select the Careers site API tokens tab and turn the toggle on to enable API access.
2. Open Settings → Apps and plugins → Personal API Tokens and click Add new token.
3. Name the token Qorva AI and create it.
4. Copy the token and note the company ID shown alongside it. Paste both in Qorva.
Real-time updates: check your Recruitee role has the Manage webhooks ability; open Settings → Apps and plugins → Webhooks and click New webhook; name it Qorva AI and paste the Webhook URL into POST URL; select the candidate events, click Test, then Verify and create. Ignore the signing secret Recruitee shows — the address authenticates itself, so there is nothing to paste back.

## Workable
You need an API access token (**API key**) and your **Subdomain**.
1. In Workable, open the menu in the top-right corner, choose Integrations, and find the API access tokens section.
2. Click Generate new token and name it Qorva AI.
3. Enable the r_jobs and r_candidates scopes. Add w_candidates only if you want Qorva to write match scores back.
4. Choose an expiry date. Syncing stops when the token expires, so note when to replace it.
5. Copy the token straight away — Workable shows it in full only once.
6. Your subdomain is the first part of your Workable web address (for example "acme").
Qorva registers Workable webhooks itself; there is no webhook screen to fill in.

## Manatal
You need an Open API token (**API key**). Manatal includes the Open API on Enterprise Plus; on other plans, ask Manatal to switch it on.
1. In Manatal, open Administration → Features → Open API. If you cannot see it, use Contact our support in Manatal to request access.
2. Click Generate new token, name it Qorva AI and generate it.
3. Copy the token and paste it in Qorva. Only Manatal administrators can see API tokens.
Note: if your Manatal plan is downgraded or a payment is missed, Manatal deactivates existing tokens; generate a new one and reconnect.
Qorva sets up real-time updates itself. To do it by hand: in Administration → Features → Open API, go to the webhooks section, add a webhook for candidate events with the Webhook URL as destination, and save.

## BambooHR
You need your BambooHR company domain (**Subdomain**) and an **API key**.
1. Your company domain is the first part of your BambooHR web address (for example "acme").
2. Sign in with an account that can already see the recruiting data you want Qorva to read — an API key carries exactly that user's permissions.
3. Click your name in the lower-left corner and choose API Keys.
4. Add a key, name it Qorva AI, and copy it.
BambooHR webhooks cover employee records, not applicants, so Qorva syncs every 30 minutes instead.

## Zoho Recruit
There is nothing to copy. You choose the datacenter where your Zoho account is hosted and sign in.
1. Click **Connect** and pick the region (United States, Europe, India, Australia, Japan, Canada, Saudi Arabia, China) that matches the Zoho domain you sign in on — for example, the European Zoho domain means Europe. This is where your account is hosted, not where your company is registered.
2. Sign in to the Zoho account that holds your Recruit organisation and review the access Qorva asks for.
3. Click Accept. Zoho sends you back to Qorva and the connection finishes on its own.
Real-time updates: in Zoho Recruit open Setup → Automation → Actions → Webhooks and click Configure Webhook; name it Qorva AI, choose POST and paste the Webhook URL into URL to Notify; save; then open Setup → Automation → Workflow Rules, create a rule on the Candidates module, choose the record events, add the Qorva webhook under Workflow Actions and activate the rule. Do not skip the rule: Zoho only sends a webhook that an active workflow rule triggers.

## Lever
You need an **API key**. Only a Lever Super Admin can create one. Optional: **Lever signing token**.
1. In Lever, open Settings → Integrations and API and select the API credentials tab.
2. Under Lever API credentials — not Postings API credentials — click Generate new key.
3. Name it Qorva AI and grant the read access Qorva needs; selecting all read permissions is the usual choice.
4. Copy the key and paste it in Qorva. Lever shows it only once.
Qorva registers Lever webhooks itself. If real-time updates stay inactive, copy the signing token from Settings → Integrations and API → Webhooks in Lever and paste it in the Lever signing token field.

## Ashby
You need an **API key**. Only an Ashby organisation admin can create one.
1. In Ashby, open Admin → Integrations → API Keys and click New.
2. Name the key Qorva AI and continue to the permissions screen.
3. Ashby keys start with no access. Grant read access to candidates, jobs, applications, hiring process and organisation data.
4. Add the apiKeysWrite permission only if you want Qorva to create its webhooks for you.
5. Save and copy the key. Ashby will not show it again once the wizard closes.
Real-time updates by hand: in Ashby open Admin → Integrations → Webhooks and click New; choose the event; paste the Webhook URL into the request URL and the secret into the secret token; save. Ashby sends a test call and the webhook turns on once Qorva accepts it.
