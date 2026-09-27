package ai.qorva.core.security;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Which tenant the current code acts for, everywhere — request threads, workers, webhooks, virtual
 * threads. The JWT filter sets it for requests; everything that runs off the request thread must
 * enter it explicitly:
 *
 * <ul>
 *   <li>{@link #runAs} / {@link #callAs} — a unit of work that belongs to one tenant (a background
 *       job, a pending email, a webhook for a known connection). Restores the previous context.</li>
 *   <li>{@link #runAsSystem} / {@link #callAsSystem} — deliberately cross-tenant work (a Stripe
 *       webhook that finds its tenant by Stripe id, the product catalogue). Tenant checks are
 *       skipped, visibly, with the reason in the logs.</li>
 *   <li>{@link #propagating} — an executor whose tasks inherit the submitting thread's scope.</li>
 * </ul>
 *
 * <p>Code that needs a tenant and finds none calls {@link #missing}: with
 * {@code qorva.tenancy.fail-closed=true} that throws; otherwise it logs a warning with a stack trace
 * and the caller keeps its historical behaviour, so a deployment can surface every such path before
 * enforcement is switched on.</p>
 */
@Slf4j
public final class TenantScope {

	private static final ThreadLocal<String> SYSTEM_REASON = new ThreadLocal<>();
	private static volatile boolean failClosed = false;

	private TenantScope() {
	}

	@FunctionalInterface
	public interface ThrowingRunnable<E extends Exception> {
		void run() throws E;
	}

	@FunctionalInterface
	public interface ThrowingSupplier<T, E extends Exception> {
		T get() throws E;
	}

	/** Raised when tenant-scoped code runs without a tenant and enforcement is on. */
	public static class MissingTenantException extends IllegalStateException {
		public MissingTenantException(String message) {
			super(message);
		}
	}

	static void setFailClosed(boolean enabled) {
		failClosed = enabled;
	}

	public static boolean isFailClosed() {
		return failClosed;
	}

	/** The tenant in scope, or null. */
	public static String current() {
		return TenantContextHolder.getTenantId();
	}

	/** The reason given to {@link #runAsSystem}, or null outside a system block. */
	public static String systemReason() {
		return isSystem() ? SYSTEM_REASON.get() : null;
	}

	/** True inside {@link #runAsSystem}: cross-tenant work that was declared as such. */
	public static boolean isSystem() {
		return SYSTEM_REASON.get() != null && current() == null;
	}

	/**
	 * Signals tenant-scoped code running without a tenant. Throws when fail-closed, otherwise logs
	 * (with the stack, so the path can be found) and lets the caller continue.
	 */
	public static void missing(String what) {
		var message = "No tenant in scope for " + what;
		if (failClosed) {
			throw new MissingTenantException(message);
		}
		log.warn("{} — running without tenant isolation (qorva.tenancy.fail-closed=false)", message,
			new MissingTenantException(message));
	}

	public static <E extends Exception> void runAs(String tenantId, ThrowingRunnable<E> work) throws E {
		callAs(tenantId, () -> {
			work.run();
			return null;
		});
	}

	public static <T, E extends Exception> T callAs(String tenantId, ThrowingSupplier<T, E> work) throws E {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("A tenant id is required to enter a tenant scope");
		}
		return within(tenantId, null, work);
	}

	public static <E extends Exception> void runAsSystem(String reason, ThrowingRunnable<E> work) throws E {
		callAsSystem(reason, () -> {
			work.run();
			return null;
		});
	}

	public static <T, E extends Exception> T callAsSystem(String reason, ThrowingSupplier<T, E> work) throws E {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("System scope needs a reason");
		}
		log.debug("Entering system scope: {}", reason);
		return within(null, reason, work);
	}

	private static <T, E extends Exception> T within(String tenantId, String systemReason, ThrowingSupplier<T, E> work) throws E {
		var previousTenant = TenantContextHolder.getTenantId();
		var previousReason = SYSTEM_REASON.get();
		set(tenantId, systemReason);
		try {
			return work.get();
		} finally {
			set(previousTenant, previousReason);
		}
	}

	private static void set(String tenantId, String systemReason) {
		if (tenantId == null) TenantContextHolder.clear(); else TenantContextHolder.setTenantId(tenantId);
		if (systemReason == null) SYSTEM_REASON.remove(); else SYSTEM_REASON.set(systemReason);
	}

	// -------------------------------------------------------------------------
	// Propagation to other threads
	// -------------------------------------------------------------------------

	/** Captures the caller's tenant/system scope and language, and re-enters them where the task runs. */
	public static Runnable wrap(Runnable task) {
		var snapshot = Snapshot.capture();
		return () -> snapshot.<Void, RuntimeException>run(() -> {
			task.run();
			return null;
		});
	}

	public static <T> Callable<T> wrap(Callable<T> task) {
		var snapshot = Snapshot.capture();
		return () -> snapshot.run(task::call);
	}

	/** An executor whose tasks run in the scope of the thread that submitted them. */
	public static ExecutorService propagating(ExecutorService delegate) {
		return new PropagatingExecutorService(delegate);
	}

	private record Snapshot(String tenantId, String systemReason, String language) {

		static Snapshot capture() {
			return new Snapshot(TenantContextHolder.getTenantId(), SYSTEM_REASON.get(), LanguageContextHolder.getLanguage());
		}

		<T, E extends Exception> T run(ThrowingSupplier<T, E> work) throws E {
			var previousLanguage = LanguageContextHolder.getLanguage();
			if (language != null) LanguageContextHolder.setLanguage(language);
			try {
				return within(tenantId, systemReason, work);
			} finally {
				if (previousLanguage == null) LanguageContextHolder.clear(); else LanguageContextHolder.setLanguage(previousLanguage);
			}
		}
	}

	private static final class PropagatingExecutorService implements ExecutorService {
		private final ExecutorService delegate;

		private PropagatingExecutorService(ExecutorService delegate) {
			this.delegate = delegate;
		}

		@Override public void execute(Runnable command) { delegate.execute(wrap(command)); }
		@Override public Future<?> submit(Runnable task) { return delegate.submit(wrap(task)); }
		@Override public <T> Future<T> submit(Runnable task, T result) { return delegate.submit(wrap(task), result); }
		@Override public <T> Future<T> submit(Callable<T> task) { return delegate.submit(wrap(task)); }
		@Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
			return delegate.invokeAll(tasks.stream().map(TenantScope::wrap).toList());
		}
		@Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
			return delegate.invokeAll(tasks.stream().map(TenantScope::wrap).toList(), timeout, unit);
		}
		@Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
			return delegate.invokeAny(tasks.stream().map(TenantScope::wrap).toList());
		}
		@Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
			return delegate.invokeAny(tasks.stream().map(TenantScope::wrap).toList(), timeout, unit);
		}
		@Override public void shutdown() { delegate.shutdown(); }
		@Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
		@Override public boolean isShutdown() { return delegate.isShutdown(); }
		@Override public boolean isTerminated() { return delegate.isTerminated(); }
		@Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
			return delegate.awaitTermination(timeout, unit);
		}
		@Override public void close() { delegate.close(); }
	}
}
