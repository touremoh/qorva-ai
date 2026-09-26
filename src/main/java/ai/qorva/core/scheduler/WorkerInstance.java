package ai.qorva.core.scheduler;

import java.util.UUID;

/** This JVM's identity as a lease owner: which instance holds a claimed job or submission. */
public final class WorkerInstance {

	public static final String ID = UUID.randomUUID().toString();

	private WorkerInstance() {
	}
}
