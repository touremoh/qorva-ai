package ai.qorva.core.dto;

/**
 * What the resume chat knows about one candidate/job pair for a turn.
 *
 * @param matchingReportId  id of the screening report found for the pair (by id or by lookup), null when none exists yet
 * @param finalScore        the report's official 0–100 fit score, null when there is no report
 * @param reportStale       true when the CV was updated after the report was generated
 */
public record ScreeningContext(String cvText, String jobText, String matchingReportText,
                               String matchingReportId, Double finalScore, boolean reportStale) {

    public ScreeningContext(String cvText, String jobText, String matchingReportText) {
        this(cvText, jobText, matchingReportText, null, null, false);
    }

    public boolean hasReport() {
        return matchingReportText != null && !matchingReportText.isBlank();
    }
}
