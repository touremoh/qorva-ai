package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.exception.QorvaException;

public interface ScreeningContextProvider {
    /**
     * Loads the CV, job post and — when one exists — the screening report for the pair, all within
     * the tenant. The report is looked up on every answer, so one generated after the conversation
     * started is picked up by the next question.
     */
    ScreeningContext load(String tenantId, String cvId, String jobPostId) throws QorvaException;
}
