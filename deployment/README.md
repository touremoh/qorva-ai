# Deployment odds and ends

## S3 lifecycle rules (staging cleanup backstop)

Workers delete staged objects as they process them, but a crashed or abandoned job
would leak files under `staged-cv-uploads/` (bulk CV imports) and
`candidate-submissions/` (candidate self-service updates). Apply
`s3-staging-lifecycle.json` to the CV bucket once per environment:

```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket "$AWS_S3_BUCKET_NAME" \
  --lifecycle-configuration file://deployment/s3-staging-lifecycle.json
```

Note: `put-bucket-lifecycle-configuration` replaces the bucket's whole lifecycle
config — if the bucket already has rules, merge them into the JSON first
(`aws s3api get-bucket-lifecycle-configuration --bucket "$AWS_S3_BUCKET_NAME"`).

## ATS integrations — required environment (tst/prd)

The ATS feature needs these variables on the App Runner service:

| Variable | Purpose |
|---|---|
| `ATS_CREDENTIALS_ENCRYPTION_KEY` | Encrypts stored ATS credentials (AES-GCM). Any long random string; **losing it invalidates every stored connection** (tenants must reconnect — no data loss otherwise). |
| `ATS_PUBLIC_BASE_URL` | This API's public origin (e.g. `https://api.qorva.ai`). Used to build webhook URLs and the OAuth redirect URI. **Changing it re-registers every automatically created webhook** — the sync scheduler compares it against `webhookState.registeredUrl` and re-subscribes on the next pass, so a stale value leaves connections receiving nothing until it is corrected. |
| `GREENHOUSE_CLIENT_ID` / `GREENHOUSE_CLIENT_SECRET` | Greenhouse **partner** OAuth app. **Not needed and not used by the current connect flow** — tenants paste their own Harvest v3 client id and secret, which Qorva exchanges for tokens itself. Only set these if the partner authorization-code flow is revived. |
| `GREENHOUSE_TOKEN_URL` | Where Greenhouse mints Harvest v3 tokens from a tenant's own client id and secret (default `https://auth.greenhouse.io/token`). Not the `api.greenhouse.io` host the partner authorization-code flow uses. |
| `GREENHOUSE_REDIRECT_URI` | Optional override, only if the URI registered with Greenhouse differs from the default `$ATS_PUBLIC_BASE_URL/integrations/greenhouse/oauth/callback`. Must match byte for byte. |
| `GREENHOUSE_SCOPES` | Optional space-separated override of the requested scopes. Must be a subset of what the Greenhouse app registration was approved for — an unapproved scope fails the whole authorization. |
| `GREENHOUSE_HARVEST_BASE_URL` | Optional override of the Harvest base URL (default `https://harvest.greenhouse.io/v3`). |
| `ATS_ZOHO_CLIENT_ID` / `ATS_ZOHO_CLIENT_SECRET` | Zoho Recruit OAuth app (register at api-console.zoho.com; redirect URI = `$ATS_PUBLIC_BASE_URL/integrations/zoho_recruit/oauth/callback`, enable all datacenters). Optional until Zoho ships. |
| `ATS_LEVER_CLIENT_ID` / `ATS_LEVER_CLIENT_SECRET` | Lever **partner** OAuth app (redirect URI = `$ATS_PUBLIC_BASE_URL/integrations/lever/oauth/callback`). **Not needed by the current connect flow** — tenants paste an API key a Lever Super Admin generates. Set these only once Qorva is an approved Lever partner and the one-click flow is switched on. |

### Automatic webhook registration

Ashby, Workable, Manatal and Lever have their webhooks created by Qorva over their APIs on
connect — no tenant-side setup. Greenhouse, Recruitee and Zoho Recruit still need the tenant
to configure one by hand (the Integrations tab shows the steps); BambooHR has no applicant
webhooks at all and runs on scheduled syncing only.

Registration is best-effort: a failure never blocks a connection, it is recorded on
`webhookState.lastError` and offered as a retry in the UI, and the scheduler retries it on
its next pass. Two provider quirks to know when debugging a webhook that never arrives:
Workable signs deliveries with the tenant's **account API token**, and Lever signs with its
own account signing token — neither uses the connection's generated secret. `AtsWebhookService.signingSecret`
is the single place that decides which key is used.

Requirements per provider: the Ashby key needs `apiKeysWrite`, the Workable token needs
`r_candidates`, Manatal needs Open API enabled, and Lever's signing token must either come
back from its create call or be pasted into the connect form.

Redirect URIs are per provider — `$ATS_PUBLIC_BASE_URL/integrations/{provider}/oauth/callback`
— because providers match them byte for byte against the app registration. Register one per
environment (tst and prd have different `ATS_PUBLIC_BASE_URL` values). The old shared path
`/public/ats/oauth/callback` still resolves, so registrations made against it keep working.
