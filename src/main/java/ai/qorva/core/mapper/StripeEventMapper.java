package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.StripeEventLog;
import ai.qorva.core.dto.events.EventSubscriptionUpdated;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.events.EventCheckoutSessionCompleted;
import ai.qorva.core.dto.events.EventSubscriptionCreated;
import ai.qorva.core.dto.events.EventSubscriptionDeleted;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stripe.model.Event;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface StripeEventMapper extends AbstractQorvaMapper<StripeEventLog, StripeEventLogDTO> {

	ObjectMapper OM = JsonMapper.builder()
		.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
		.build();

	/**
	 * Convert Stripe Event -> DTO representing the *data.object* portion.
	 */
	default <T> T mapStripeEventToDTO(Event event, Class<T> clazz) throws QorvaException {
		try {
			// 1) Best path: use Stripe's typed deserializer first
			var deserializer = event.getDataObjectDeserializer();

			if (deserializer.getObject().isPresent()) {
				// We have a concrete StripeObject (Subscription, CheckoutSession, etc.)
				// Serialize that object to JSON and let Jackson map it to your DTO.
				var stripeObject = deserializer.getObject().get();
				// Serialize with Stripe’s Gson, then Jackson -> DTO
				String json = com.stripe.net.ApiResource.GSON.toJson(stripeObject);
				return OM.readValue(json, clazz);
			}

			// 2) Fallback: manually slice "data.object" from the event raw JSON
			String raw = event.getRawJsonObject().toString();
			var root = OM.readTree(raw);
			var dataObject = root.path("data").path("object");
			if (dataObject.isMissingNode() || dataObject.isNull()) {
				throw new QorvaException("Stripe event missing data.object");
			}
			return OM.treeToValue(dataObject, clazz);

		} catch (Exception ex) {
			throw new QorvaException("Failed to parse Stripe event data.object", ex);
		}
	}

	default EventSubscriptionCreated mapStripeEventToEventSubscriptionCreated(Event event) throws QorvaException {
		return mapStripeEventToDTO(event, EventSubscriptionCreated.class);
	}

	default EventCheckoutSessionCompleted mapStripeEventToEventCheckoutSessionCompleted(Event event) throws QorvaException {
		return mapStripeEventToDTO(event, EventCheckoutSessionCompleted.class);
	}

	default EventSubscriptionUpdated mapStripeEventToEventSubscriptionUpdated(Event event) throws QorvaException {
		try {
			return OM.readValue(event.getRawJsonObject().toString(), EventSubscriptionUpdated.class);
		} catch (JsonMappingException e) {
			throw new QorvaException("Failed to map Stripe event to EventSubscriptionUpdated", e);
		} catch (JsonProcessingException e) {
			throw new QorvaException("Failed to process JSON object", e);
		}
	}

	default EventSubscriptionDeleted mapStripeEventToEventSubscriptionDeleted(Event event) throws QorvaException {
		try {
			return OM.readValue(event.getRawJsonObject().toString(), EventSubscriptionDeleted.class);
		} catch (JsonMappingException e) {
			throw new QorvaException("Failed to map Stripe event to EventSubscriptionUpdated", e);
		} catch (JsonProcessingException e) {
			throw new QorvaException("Failed to process JSON object", e);
		}
	}
}
