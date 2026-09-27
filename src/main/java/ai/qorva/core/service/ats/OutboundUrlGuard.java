package ai.qorva.core.service.ats;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Outbound ATS calls go to public HTTPS hosts only. URLs are built by connectors on provider hosts,
 * but some parts come back from providers (pagination links, resume download links, Zoho's api
 * domain); a compromised or spoofed response must not make Qorva call its own network — cloud
 * metadata (169.254.169.254), localhost, private ranges — with or without the tenant's credentials.
 */
final class OutboundUrlGuard {

	private OutboundUrlGuard() {
	}

	/** Why the URL is refused, or null when it may be called. */
	static String refusal(URI uri) {
		if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
			return "not https";
		}
		var host = uri.getHost();
		if (host == null || host.isBlank()) {
			return "no host";
		}
		try {
			for (var address : InetAddress.getAllByName(host)) {
				if (isInternal(address)) {
					return "internal address";
				}
			}
		} catch (UnknownHostException unresolved) {
			// Nothing to reach: the call itself fails, as it always did.
		}
		return null;
	}

	static boolean isInternal(InetAddress address) {
		return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
			|| address.isAnyLocalAddress() || address.isMulticastAddress()
			|| isUniqueLocalV6(address);
	}

	/** fc00::/7 — IPv6's private range, which Java does not count as site-local. */
	private static boolean isUniqueLocalV6(InetAddress address) {
		var bytes = address.getAddress();
		return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
	}
}
