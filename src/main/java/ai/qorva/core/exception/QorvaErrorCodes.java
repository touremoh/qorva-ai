package ai.qorva.core.exception;

public final class QorvaErrorCodes {

    private QorvaErrorCodes() {}

    // auth
    public static final String AUTH_TOKEN_EXPIRED         = "error.auth.token_expired";
    public static final String AUTH_TOKEN_INVALID         = "error.auth.token_invalid";
    public static final String AUTH_FAILED                = "error.auth.authentication_failed";
    public static final String AUTH_SUBSCRIPTION_INACTIVE = "error.auth.subscription_inactive";
    public static final String AUTH_USER_NOT_FOUND        = "error.auth.user_not_found";
    public static final String AUTH_USER_LOOKUP_FAILED    = "error.auth.user_lookup_failed";
    public static final String AUTH_SET_PASSWORD_TOKEN_INVALID = "error.auth.set_password_token_invalid";
    public static final String AUTH_SET_PASSWORD_TOKEN_USED    = "error.auth.set_password_token_used";
    public static final String AUTH_MFA_CHALLENGE_INVALID = "error.auth.mfa_challenge_invalid";
    public static final String AUTH_MFA_CODE_INVALID      = "error.auth.mfa_code_invalid";
    public static final String AUTH_MFA_TOO_MANY_ATTEMPTS = "error.auth.mfa_too_many_attempts";
    public static final String AUTH_MFA_RESEND_TOO_SOON   = "error.auth.mfa_resend_too_soon";
    public static final String AUTH_MFA_TOO_MANY_CODES    = "error.auth.mfa_too_many_codes";
    public static final String AUTH_MFA_DELIVERY_FAILED   = "error.auth.mfa_delivery_failed";
    public static final String AUTH_SSO_NOT_CONFIGURED    = "error.auth.sso_not_configured";
    public static final String AUTH_SSO_FAILED            = "error.auth.sso_failed";
    public static final String AUTH_SSO_NO_ACCOUNT        = "error.auth.sso_no_account";
    public static final String AUTH_SSO_REQUIRED          = "error.auth.sso_required";
    public static final String AUTH_SSO_CODE_INVALID      = "error.auth.sso_code_invalid";
    public static final String MFA_ALREADY_ENABLED        = "error.mfa.already_enabled";
    public static final String MFA_ALREADY_DISABLED       = "error.mfa.already_disabled";

    // access
    public static final String ACCESS_FORBIDDEN    = "error.access.forbidden";

    // user
    public static final String USER_ALREADY_EXISTS      = "error.user.already_exists";
    public static final String USER_NOT_FOUND           = "error.user.not_found";
    public static final String USER_INVITE_NOT_PENDING  = "error.user.invite_not_pending";
    public static final String USER_INVITE_RESEND_TOO_SOON = "error.user.invite_resend_too_soon";
    public static final String USER_PASSWORD_INCORRECT  = "error.user.password_incorrect";
    public static final String USER_SEAT_LIMIT_REACHED  = "error.user.seat_limit_reached";
    public static final String USER_COMPANY_ID_REQUIRED = "error.user.company_id_required";

    // cv
    public static final String CV_MAX_FILES_EXCEEDED = "error.cv.max_files_exceeded";
    public static final String CV_NO_FILES_PROCESSED = "error.cv.no_files_processed";
    public static final String CV_CONTENT_EMPTY      = "error.cv.content_empty";
    public static final String CV_EXTRACTION_FAILED  = "error.cv.extraction_failed";
    public static final String CV_ATTACHMENT_UPLOAD_FAILED = "error.cv.attachment_upload_failed";

    // file  (params: {0} = filename)
    public static final String FILE_EMPTY             = "error.file.empty";
    public static final String FILE_PDF_READ_FAILED   = "error.file.pdf_read_failed";
    public static final String FILE_WORD_READ_FAILED  = "error.file.word_read_failed";
    public static final String FILE_NAME_NULL         = "error.file.name_null";
    public static final String FILE_UNSUPPORTED_TYPE  = "error.file.unsupported_type";

    // report
    public static final String REPORT_JOB_ID_REQUIRED          = "error.report.job_id_required";
    public static final String REPORT_CANDIDATE_INFO_REQUIRED   = "error.report.candidate_info_required";
    public static final String REPORT_RESUME_MATCH_NOT_FOUND    = "error.report.resume_match_not_found";
    public static final String REPORT_NO_REPORTS_FOR_JOB        = "error.report.no_reports_for_job";
    public static final String REPORT_CSV_EXPORT_FAILED         = "error.report.csv_export_failed";
    public static final String REPORT_NOT_FOUND                 = "error.report.not_found";
    public static final String REPORT_STATUS_INVALID            = "error.report.status_invalid";
    public static final String DASHBOARD_PERIOD_INVALID         = "error.dashboard.period_invalid";
    public static final String REPORT_STATUS_CONFLICT           = "error.report.status_conflict";
    public static final String PIPELINE_CURSOR_INVALID          = "error.pipeline.cursor_invalid";


    // notes
    public static final String NOTE_TARGET_TYPE_INVALID = "error.note.target_type_invalid";
    public static final String NOTE_TEXT_INVALID        = "error.note.text_invalid";
    public static final String NOTE_NOT_FOUND           = "error.note.not_found";
    public static final String NOTE_NOT_AUTHOR          = "error.note.not_author";

    // candidate outreach
    public static final String OUTREACH_INTENT_INVALID  = "error.outreach.intent_invalid";
    public static final String OUTREACH_DRAFT_FAILED    = "error.outreach.draft_failed";
    public static final String OUTREACH_NO_EMAIL        = "error.outreach.no_email";
    public static final String OUTREACH_SUPPRESSED      = "error.outreach.suppressed";
    public static final String OUTREACH_VIA_INVALID     = "error.outreach.via_invalid";

    // connected mailbox
    public static final String MAILBOX_PROVIDER_UNKNOWN   = "error.mailbox.provider_unknown";
    public static final String MAILBOX_NOT_CONFIGURED     = "error.mailbox.not_configured";
    public static final String MAILBOX_NOT_CONNECTED      = "error.mailbox.not_connected";
    public static final String MAILBOX_REAUTH_REQUIRED    = "error.mailbox.reauth_required";
    public static final String MAILBOX_SEND_FAILED        = "error.mailbox.send_failed";
    public static final String MAILBOX_OAUTH_STATE_INVALID = "error.mailbox.oauth_state_invalid";

    // ai
    public static final String AI_REQUEST_FAILED = "error.ai.request_failed";
    public static final String AI_ANSWER_TOO_LONG = "error.ai.answer_too_long";

    // usage
    public static final String USAGE_SCREENING_LIMIT_EXCEEDED = "error.usage.screening_limit_exceeded";
    public static final String USAGE_INSIGHT_UNAVAILABLE = "error.usage.insight_unavailable";
    public static final String USAGE_AGENT_LIMIT_EXCEEDED = "error.usage.agent_limit_exceeded";

    // library clear
    public static final String CV_CLEAR_BLOCKED_BY_ACTIVE_JOB = "error.cv.clear_blocked_by_active_job";

    // bulk upload
    public static final String BULK_LIMIT_FOR_PLAN     = "error.cv.bulk_limit_for_plan";
    public static final String BULK_JOB_ACTIVE_EXISTS  = "error.cv.bulk_job_active_exists";
    public static final String BULK_JOB_NOT_FOUND      = "error.cv.bulk_job_not_found";
    public static final String BULK_JOB_NOT_DRAFT      = "error.cv.bulk_job_not_draft";
    public static final String BULK_JOB_NO_FILES       = "error.cv.bulk_job_no_files";

    // ats integrations
    public static final String ATS_PROVIDER_UNKNOWN          = "error.ats.provider_unknown";
    public static final String ATS_CONNECTION_NOT_FOUND      = "error.ats.connection_not_found";
    public static final String ATS_CONNECTION_EXISTS         = "error.ats.connection_exists";
    public static final String ATS_CONNECTION_LIMIT_FOR_PLAN = "error.ats.connection_limit_for_plan";
    public static final String ATS_CONNECTION_NOT_CONNECTED  = "error.ats.connection_not_connected";
    public static final String ATS_SYNC_ACTIVE_EXISTS        = "error.ats.sync_active_exists";
    public static final String ATS_AUTH_FAILED               = "error.ats.auth_failed";
    public static final String ATS_API_ERROR                 = "error.ats.api_error";
    public static final String ATS_OAUTH_STATE_INVALID       = "error.ats.oauth_state_invalid";
    public static final String ATS_OAUTH_NOT_CONFIGURED      = "error.ats.oauth_not_configured";

    // company
    public static final String COMPANY_LOGO_UPLOAD_FAILED = "error.company.logo_upload_failed";
    public static final String COMPANY_LOGO_NOT_FOUND     = "error.company.logo_not_found";
    public static final String COMPANY_LOGO_FETCH_FAILED  = "error.company.logo_fetch_failed";

    // billing
    public static final String BILLING_PORTAL_FAILED             = "error.billing.portal_failed";
    public static final String BILLING_CUSTOMER_CREATION_FAILED  = "error.billing.customer_creation_failed";
    public static final String BILLING_CHECKOUT_FAILED           = "error.billing.checkout_failed";
    public static final String BILLING_PRICE_ID_REQUIRED         = "error.billing.price_id_required";
    public static final String BILLING_NO_STRIPE_CUSTOMER        = "error.billing.no_stripe_customer";
    public static final String BILLING_UPGRADE_DEMO_ONLY         = "error.billing.upgrade_demo_only";

    // http / generic
    public static final String HTTP_NOT_FOUND   = "error.http.not_found";
    public static final String HTTP_BAD_REQUEST = "error.http.bad_request";
    public static final String HTTP_UNAUTHORIZED = "error.http.unauthorized";
    public static final String HTTP_FORBIDDEN    = "error.http.forbidden";
    public static final String HTTP_CONFLICT     = "error.http.conflict";
    public static final String HTTP_VALIDATION   = "error.http.validation";
    public static final String HTTP_UNEXPECTED   = "error.http.unexpected";
    public static final String HTTP_TOO_MANY_REQUESTS   = "error.http.too_many_requests";
    public static final String HTTP_SERVICE_UNAVAILABLE = "error.http.service_unavailable";

    // domain validation (phase 9)
    public static final String EMAIL_TEMPLATE_NAME_EXISTS               = "error.email_template.name_exists";
    public static final String EMAIL_TEMPLATE_NOT_FOUND                 = "error.email_template.not_found";
    public static final String EMAIL_TEMPLATE_NAME_REQUIRED             = "error.email_template.name_required";
    public static final String EMAIL_TEMPLATE_NAME_TOO_LONG             = "error.email_template.name_too_long";
    public static final String EMAIL_TEMPLATE_SUBJECT_REQUIRED          = "error.email_template.subject_required";
    public static final String EMAIL_TEMPLATE_SUBJECT_TOO_LONG          = "error.email_template.subject_too_long";
    public static final String EMAIL_TEMPLATE_BODY_REQUIRED             = "error.email_template.body_required";
    public static final String EMAIL_TEMPLATE_BODY_TOO_LONG             = "error.email_template.body_too_long";
    public static final String EMAIL_TEMPLATE_UNKNOWN_PLACEHOLDER       = "error.email_template.unknown_placeholder";
    public static final String EMAIL_TEMPLATE_LIMIT_REACHED             = "error.email_template.limit_reached";
    public static final String QUALITY_DUPLICATES_SEPARATE              = "error.library_quality.duplicates_endpoint";
    public static final String QUALITY_DUPLICATES_NOT_BULK              = "error.library_quality.duplicates_not_bulk";
    public static final String QUALITY_CONFIRM_CURRENT_LIMIT            = "error.library_quality.confirm_current_limit";
    public static final String QUALITY_UNKNOWN_ACTION                   = "error.library_quality.unknown_action";
    public static final String QUALITY_UNKNOWN_ISSUE                    = "error.library_quality.unknown_issue";
    public static final String QUALITY_INVALID_CV_ID                    = "error.library_quality.invalid_cv_id";
    public static final String QUALITY_INSIGHT_UNAVAILABLE              = "error.library_quality.insight_unavailable";
    public static final String CV_REPLACE_SELF                          = "error.cv.replace_self";
    public static final String JOB_DESCRIPTION_REQUIRED                 = "error.job.description_required";
    public static final String BACKGROUND_JOB_UNSUPPORTED_TYPE          = "error.background_job.unsupported_type";
    public static final String BACKGROUND_JOB_DUPLICATES_INDIVIDUAL     = "error.background_job.duplicates_individual";
    public static final String BACKGROUND_JOB_CAMPAIGN_FRESHNESS_ONLY   = "error.background_job.campaign_freshness_only";
    public static final String BACKGROUND_JOB_TEMPLATE_CAMPAIGN_ONLY    = "error.background_job.template_campaign_only";
    public static final String BACKGROUND_JOB_ALREADY_RUNNING           = "error.background_job.already_running";
    public static final String BACKGROUND_JOB_QUOTA_EXCEEDED            = "error.background_job.quota_exceeded";
    public static final String BACKGROUND_JOB_NO_REANALYZABLE           = "error.background_job.no_reanalyzable";
    public static final String BACKGROUND_JOB_NO_RESUMES                = "error.background_job.no_resumes";

    // Matching runs
    public static final String MATCHING_TOP_N_INVALID                   = "error.matching.top_n_invalid";
    public static final String MATCHING_TOP_N_NOT_IN_PLAN               = "error.matching.top_n_not_in_plan";
    public static final String MATCHING_NO_JOBS                         = "error.matching.no_jobs";
    public static final String MATCHING_TOO_MANY_JOBS                   = "error.matching.too_many_jobs";
    public static final String MATCHING_JOB_NOT_OPEN                    = "error.matching.job_not_open";
    public static final String MATCHING_RUN_ACTIVE                      = "error.matching.run_active";
    public static final String MATCHING_QUOTA_EXCEEDED                  = "error.matching.quota_exceeded";
    public static final String MATCHING_RUN_NOT_FOUND                   = "error.matching.run_not_found";
    public static final String CANDIDATE_UPDATE_INVALID_SUBMISSION      = "error.candidate_update.invalid_submission";
    public static final String CANDIDATE_UPDATE_UNSUPPORTED_FILE        = "error.candidate_update.unsupported_file";
    public static final String CANDIDATE_UPDATE_LINK_INVALID            = "error.candidate_update.link_invalid";

    // Copilot (AI agent)
    public static final String AGENT_DISABLED                           = "error.agent.disabled";
    public static final String AGENT_RUN_ACTIVE                         = "error.agent.run_active";
    public static final String AGENT_RUN_NOT_FOUND                      = "error.agent.run_not_found";
    public static final String AGENT_GOAL_INVALID                       = "error.agent.goal_invalid";
    public static final String AGENT_FOCUS_INVALID                      = "error.agent.focus_invalid";
    public static final String AGENT_CONVERSATION_ACTIVE                = "error.agent.conversation_active";
    public static final String AGENT_TEAM_SCOPE_FORBIDDEN               = "error.agent.team_scope_forbidden";
    public static final String AGENT_USER_UNAVAILABLE                   = "error.agent.user_unavailable";
    public static final String AGENT_MODEL_FAILED                       = "error.agent.model_failed";
    public static final String AGENT_TOO_MANY_ERRORS                    = "error.agent.too_many_errors";
    public static final String AGENT_ACTION_STALE                       = "error.agent.action_stale";
    public static final String AGENT_ACTION_INVALID                     = "error.agent.action_invalid";
    public static final String AGENT_APPROVAL_EXPIRED                   = "error.agent.approval_expired";
    public static final String AGENT_RULES_DISABLED                     = "error.agent.rules_disabled";
    public static final String AGENT_RULE_NOT_FOUND                     = "error.agent.rule_not_found";
    public static final String AGENT_RULE_INVALID                       = "error.agent.rule_invalid";
    public static final String AGENT_RULE_LIMIT_REACHED                 = "error.agent.rule_limit_reached";

    // Qorva Help (in-app product help assistant)
    public static final String HELP_DISABLED                            = "error.help.disabled";
    public static final String HELP_MESSAGE_INVALID                     = "error.help.message_invalid";
    public static final String HELP_RATE_LIMITED                        = "error.help.rate_limited";
    public static final String HELP_UNAVAILABLE                         = "error.help.unavailable";
    public static final String HELP_TICKET_INVALID                      = "error.help.ticket_invalid";
    public static final String HELP_TICKET_RATE_LIMITED                 = "error.help.ticket_rate_limited";
}
