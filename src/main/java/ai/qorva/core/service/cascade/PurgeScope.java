package ai.qorva.core.service.cascade;

/** How much of a tenant a purge removes. Each scope covers the ones before it. */
public enum PurgeScope {
	/** The resume library and everything derived from it; jobs, usage and settings survive (clear library). */
	LIBRARY,
	/** All recruitment data: the library plus job posts and usage periods (demo account conversion). */
	RECRUITMENT,
	/** The whole account: recruitment data plus users, connections, settings, queues and the tenant itself (admin purge). */
	FULL;

	/** Whether a purge of this scope removes what {@code other} removes. */
	public boolean covers(PurgeScope other) {
		return ordinal() >= other.ordinal();
	}
}
