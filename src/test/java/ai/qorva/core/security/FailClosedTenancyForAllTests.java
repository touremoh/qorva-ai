package ai.qorva.core.security;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Every test in the JVM runs with tenant isolation enforced — unit tests included, whatever order
 * they run in — so code that touches tenant data without a tenant in scope fails its test.
 */
public class FailClosedTenancyForAllTests implements LauncherSessionListener {

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		TenantScope.setFailClosed(true);
	}
}
