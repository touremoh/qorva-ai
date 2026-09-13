package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.exception.QorvaException;

public interface ScreeningContextProvider {
    /**
     * Loads the CV, job post and — when one exists — the screening report for the chat's pair.
     * The report is resolved on every turn: by id when the chat is linked to one, otherwise by
     * (tenant, job, candidate) lookup, so a report generated after the chat was created is
     * picked up on the next message.
     */
    ScreeningContext load(Chat chat) throws QorvaException;
}
