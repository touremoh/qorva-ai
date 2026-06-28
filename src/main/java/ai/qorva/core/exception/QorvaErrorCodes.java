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

    // access
    public static final String ACCESS_FORBIDDEN    = "error.access.forbidden";
    public static final String ACCESS_CROSS_TENANT = "error.access.cross_tenant";

    // user
    public static final String USER_ALREADY_EXISTS      = "error.user.already_exists";
    public static final String USER_NOT_FOUND           = "error.user.not_found";
    public static final String USER_PASSWORD_INCORRECT  = "error.user.password_incorrect";
    public static final String USER_SEAT_LIMIT_REACHED  = "error.user.seat_limit_reached";
    public static final String USER_COMPANY_ID_REQUIRED = "error.user.company_id_required";

    // cv
    public static final String CV_MAX_FILES_EXCEEDED = "error.cv.max_files_exceeded";
    public static final String CV_NO_FILES_PROCESSED = "error.cv.no_files_processed";
    public static final String CV_CONTENT_EMPTY      = "error.cv.content_empty";
    public static final String CV_EXTRACTION_FAILED  = "error.cv.extraction_failed";

    // file  (params: {0} = filename)
    public static final String FILE_EMPTY             = "error.file.empty";
    public static final String FILE_PDF_READ_FAILED   = "error.file.pdf_read_failed";
    public static final String FILE_WORD_READ_FAILED  = "error.file.word_read_failed";
    public static final String FILE_READ_FAILED       = "error.file.read_failed";
    public static final String FILE_NAME_NULL         = "error.file.name_null";
    public static final String FILE_UNSUPPORTED_TYPE  = "error.file.unsupported_type";

    // report
    public static final String REPORT_JOB_ID_REQUIRED          = "error.report.job_id_required";
    public static final String REPORT_CANDIDATE_INFO_REQUIRED   = "error.report.candidate_info_required";
    public static final String REPORT_RESUME_MATCH_NOT_FOUND    = "error.report.resume_match_not_found";
    public static final String REPORT_MATCH_NOT_FOUND_BY_ID     = "error.report.match_not_found_by_id";
    public static final String REPORT_NO_REPORTS_FOR_JOB        = "error.report.no_reports_for_job";
    public static final String REPORT_CSV_EXPORT_FAILED         = "error.report.csv_export_failed";

    // chat
    public static final String CHAT_NOT_FOUND      = "error.chat.not_found";
    public static final String CHAT_OWNER_REQUIRED = "error.chat.owner_required";
    public static final String CHAT_ACTOR_NOT_FOUND = "error.chat.actor_not_found";

    // ai
    public static final String AI_REQUEST_FAILED = "error.ai.request_failed";

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

    // http / generic
    public static final String HTTP_NOT_FOUND   = "error.http.not_found";
    public static final String HTTP_BAD_REQUEST = "error.http.bad_request";
    public static final String HTTP_UNAUTHORIZED = "error.http.unauthorized";
    public static final String HTTP_FORBIDDEN    = "error.http.forbidden";
    public static final String HTTP_CONFLICT     = "error.http.conflict";
    public static final String HTTP_VALIDATION   = "error.http.validation";
    public static final String HTTP_UNEXPECTED   = "error.http.unexpected";
}
