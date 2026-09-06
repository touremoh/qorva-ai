package ai.qorva.core.service.ats;

import ai.qorva.core.enums.AtsProviderEnum;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Provider → connector lookup, populated from every AtsConnector bean (Stripe-handlers pattern). */
@Component
public class AtsConnectorRegistry {

	private final Map<AtsProviderEnum, AtsConnector> connectors = new EnumMap<>(AtsProviderEnum.class);

	public AtsConnectorRegistry(List<AtsConnector> beans) {
		beans.forEach(connector -> connectors.put(connector.provider(), connector));
	}

	public AtsConnector get(AtsProviderEnum provider) {
		var connector = connectors.get(provider);
		if (connector == null) {
			throw new IllegalStateException("No connector registered for provider " + provider);
		}
		return connector;
	}
}
