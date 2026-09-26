package ai.qorva.core.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which inherited CRUD operations a controller exposes, and the action each one requires.
 *
 * <p>Fail-closed: an operation that is not declared is disabled and answers 404, so extending
 * {@code AbstractQorvaController} no longer publishes routes nobody decided to publish.</p>
 */
public final class CrudPolicy {

	/** Marker for "any authenticated user of the tenant", with no action required. */
	private static final String AUTHENTICATED = "";

	private final Map<CrudOperation, String> actions;

	private CrudPolicy(Map<CrudOperation, String> actions) {
		this.actions = Collections.unmodifiableMap(actions);
	}

	public static Builder builder() {
		return new Builder();
	}

	public boolean isEnabled(CrudOperation operation) {
		return actions.containsKey(operation);
	}

	/** The action the operation requires; empty when any authenticated user may call it. */
	public Optional<String> requiredAction(CrudOperation operation) {
		var action = actions.get(operation);
		return action == null || action.isEmpty() ? Optional.empty() : Optional.of(action);
	}

	public static final class Builder {
		private final Map<CrudOperation, String> actions = new EnumMap<>(CrudOperation.class);

		private Builder() {
		}

		public Builder allow(String action, CrudOperation... operations) {
			for (var operation : operations) {
				actions.put(operation, action);
			}
			return this;
		}

		public Builder allowAuthenticated(CrudOperation... operations) {
			return allow(AUTHENTICATED, operations);
		}

		public CrudPolicy build() {
			return new CrudPolicy(actions);
		}
	}
}
