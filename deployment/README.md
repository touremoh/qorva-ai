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
