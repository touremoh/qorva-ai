package ai.qorva.core.service.cascade;

/** How much of a tenant a purge removes. */
public enum PurgeScope {
	/** The resume library and everything derived from it; jobs, usage and settings survive (clear library). */
	LIBRARY,
	/** All recruitment data: the library plus job posts and usage periods (demo account conversion). */
	RECRUITMENT
}
