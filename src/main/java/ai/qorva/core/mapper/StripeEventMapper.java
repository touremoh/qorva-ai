package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.StripeEventLog;
import ai.qorva.core.dto.StripeEventLogDTO;
import com.stripe.model.Event;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface StripeEventMapper extends AbstractQorvaMapper<StripeEventLog, StripeEventLogDTO> {

	default StripeEventLogDTO mapStripeEventToDTO(Event event) {
		var data = event.getData().getRawJsonObject();
		var tenantId = data.get("tenantId").getAsString();
		var stripeCustomerId = data.get("customer").getAsString();
		var stripeSubscriptionId = data.get("subscriptionI").getAsString();
		// var paymentStatus = data.get("subscriptionI").getAsString();

		var eventType = event.getType();

		var dto = new StripeEventLogDTO();

		dto.setTenantId(tenantId);
		dto.setStripeCustomerId(stripeCustomerId);
		dto.setStripeSubscriptionId(stripeSubscriptionId);
		dto.setEventType(eventType);

		return dto;
	}
}
