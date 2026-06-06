package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dto.CVQueryParams;
import ai.qorva.core.dto.InsightHandlerResult;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
public class GeneralRecruitingChatHandler implements InsightHandler {

	@Override
	public InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId) {
		return InsightHandlerResult.empty();
	}
}
