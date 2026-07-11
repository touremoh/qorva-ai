package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dto.CVQueryParams;
import ai.qorva.core.dto.InsightHandlerResult;
import ai.qorva.core.service.orchestrators.MentionResolver;
import org.bson.types.ObjectId;

public interface InsightHandler {

	InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId);

	default InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId, MentionResolver.ResolvedMentions mentions) {
		return handle(params, tenantId);
	}
}
