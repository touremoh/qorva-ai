package ai.qorva.core.security;

/** The operations {@code AbstractQorvaController} exposes; each one must be allowed by the controller's {@link CrudPolicy}. */
public enum CrudOperation {
	GET_ONE,
	LIST,
	SEARCH,
	FIND_BY_IDS,
	EXISTS,
	CREATE,
	UPDATE,
	DELETE
}
