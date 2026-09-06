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
| `ATS_PUBLIC_BASE_URL` | This API's public origin (e.g. `https://api.qorva.ai`). Used to build webhook URLs shown to tenants and the OAuth redirect URI. |
| `GREENHOUSE_CLIENT_ID` / `GREENHOUSE_CLIENT_SECRET` | Greenhouse OAuth app (Harvest v3). Handed over after the partner application is approved. **Optional** — while these are unset, the Greenhouse card shows the API-key form instead, and a tenant connects with a Harvest key they generate in their own Greenhouse Dev Center (no partner approval involved). Setting them switches the card to one-click OAuth. |
| `GREENHOUSE_HARVEST_API_KEY_BASE_URL` | Harvest base for API-key connections (default `https://harvest.greenhouse.io/v1`). Separate from the OAuth base because the two flows can sit on different API versions. |
| `GREENHOUSE_REDIRECT_URI` | Optional override, only if the URI registered with Greenhouse differs from the default `$ATS_PUBLIC_BASE_URL/integrations/greenhouse/oauth/callback`. Must match byte for byte. |
| `GREENHOUSE_SCOPES` | Optional space-separated override of the requested scopes. Must be a subset of what the Greenhouse app registration was approved for — an unapproved scope fails the whole authorization. |
| `GREENHOUSE_HARVEST_BASE_URL` | Optional override of the Harvest base URL (default `https://harvest.greenhouse.io/v3`). |
| `ATS_ZOHO_CLIENT_ID` / `ATS_ZOHO_CLIENT_SECRET` | Zoho Recruit OAuth app (register at api-console.zoho.com; redirect URI = `$ATS_PUBLIC_BASE_URL/integrations/zoho_recruit/oauth/callback`, enable all datacenters). Optional until Zoho ships. |
| `ATS_LEVER_CLIENT_ID` / `ATS_LEVER_CLIENT_SECRET` | Lever OAuth app (partner program; redirect URI = `$ATS_PUBLIC_BASE_URL/integrations/lever/oauth/callback`). Optional until Lever ships. |

Redirect URIs are per provider — `$ATS_PUBLIC_BASE_URL/integrations/{provider}/oauth/callback`
— because providers match them byte for byte against the app registration. Register one per
environment (tst and prd have different `ATS_PUBLIC_BASE_URL` values). The old shared path
`/public/ats/oauth/callback` still resolves, so registrations made against it keep working.
