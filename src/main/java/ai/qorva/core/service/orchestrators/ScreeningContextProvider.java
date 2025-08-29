package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.exception.QorvaException;

public interface ScreeningContextProvider {
    ScreeningContext load(String cvId, String jobPostId, String resumeMatchId) throws QorvaException;
}