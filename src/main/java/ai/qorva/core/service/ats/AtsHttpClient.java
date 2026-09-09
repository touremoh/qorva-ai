package ai.qorva.core.service.ats;

import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Shared outbound HTTP for all connectors: per-provider request pacing, 429/5xx retry
 * with backoff, and auth-failure normalization to ATS_AUTH_FAILED so the engine can
 * flip a connection to AUTH_ERROR. Connectors pass fully built URLs on fixed provider
 * hosts — never a tenant-supplied origin.
 */
@Slf4j
@Component
public class AtsHttpClient {

	/** Conservative floor between requests per provider (ms) — well under every documented limit. */
	private static final Map<AtsProviderEnum, Long> MIN_INTERVAL_MS = Map.of(
		AtsProviderEnum.GREENHOUSE, 250L,   // 50 req / 10 s
		AtsProviderEnum.WORKABLE, 150L,     // 10 req / s
		AtsProviderEnum.RECRUITEE, 150L,
		AtsProviderEnum.MANATAL, 200L,
		AtsProviderEnum.BAMBOOHR, 250L,
		AtsProviderEnum.ZOHO_RECRUIT, 300L, // credits-based; stay gentle
		AtsProviderEnum.LEVER, 150L,
		AtsProviderEnum.ASHBY, 200L         // documented limits are per-endpoint; stay conservative
	);

	private static final int MAX_RETRIES = 3;

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final Map<AtsProviderEnum, Object> paceLocks = new ConcurrentHashMap<>();
	private final Map<AtsProviderEnum, Long> lastRequestAt = new ConcurrentHashMap<>();

	public AtsHttpClient(RestClient.Builder builder, ObjectMapper objectMapper) {
		this.restClient = builder.build();
		this.objectMapper = objectMapper;
	}

	public JsonNode getJson(AtsProviderEnum provider, String url, Map<String, String> headers) throws QorvaException {
		var body = execute(provider, () -> restClient.get().uri(url)
			.headers(h -> apply(headers, h))
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.body(String.class));
		return readTree(body);
	}

	public byte[] getBytes(AtsProviderEnum provider, String url, Map<String, String> headers) throws QorvaException {
		return execute(provider, () -> restClient.get().uri(url)
			.headers(h -> apply(headers, h))
			.retrieve()
			.body(byte[].class));
	}

	/** DELETE for providers whose webhook subscriptions are REST resources (Manatal, Lever). */
	public void delete(AtsProviderEnum provider, String url, Map<String, String> headers) throws QorvaException {
		execute(provider, () -> restClient.delete().uri(url)
			.headers(h -> apply(headers, h))
			.retrieve()
			.body(String.class));
	}

	public JsonNode postJson(AtsProviderEnum provider, String url, Map<String, String> headers, Object payload) throws QorvaException {
		var body = execute(provider, () -> restClient.post().uri(url)
			.headers(h -> apply(headers, h))
			.contentType(MediaType.APPLICATION_JSON)
			.body(payload)
			.retrieve()
			.body(String.class));
		return readTree(body);
	}

	/**
	 * POST that treats 409 Conflict as an acceptable outcome, for "create if absent" calls
	 * like Workable's webhook subscriptions: a conflict means the resource we wanted already
	 * exists, which is the end state we were after. Returns empty in that case.
	 */
	public Optional<JsonNode> postJsonIgnoringConflict(AtsProviderEnum provider, String url,
		Map<String, String> headers, Object payload) throws QorvaException {
		var conflict = new AtomicBoolean(false);
		var body = execute(provider, () -> {
			try {
				return restClient.post().uri(url)
					.headers(h -> apply(headers, h))
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.body(String.class);
			} catch (HttpClientErrorException.Conflict e) {
				// Swallowed here so execute() never sees it as a failure worth retrying.
				conflict.set(true);
				return null;
			}
		});
		return conflict.get() ? Optional.empty() : Optional.of(readTree(body));
	}

	/** application/x-www-form-urlencoded POST — OAuth token endpoints. */
	public JsonNode postForm(AtsProviderEnum provider, String url, Map<String, String> form) throws QorvaException {
		return postForm(provider, url, null, form);
	}

	/**
	 * Form POST with extra headers — token endpoints that authenticate the client itself
	 * (Greenhouse Harvest v3 sends client id and secret as HTTP Basic, not as form fields).
	 */
	public JsonNode postForm(AtsProviderEnum provider, String url, Map<String, String> headers,
		Map<String, String> form) throws QorvaException {
		var encoded = new StringBuilder();
		form.forEach((k, v) -> {
			if (!encoded.isEmpty()) encoded.append('&');
			encoded.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
				.append('=')
				.append(URLEncoder.encode(v, StandardCharsets.UTF_8));
		});
		var body = execute(provider, () -> restClient.post().uri(url)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.headers(h -> apply(headers, h))
			.body(encoded.toString())
			.retrieve()
			.body(String.class));
		return readTree(body);
	}

	private void apply(Map<String, String> headers, org.springframework.http.HttpHeaders target) {
		if (headers != null) {
			headers.forEach(target::set);
		}
	}

	private JsonNode readTree(String body) throws QorvaException {
		try {
			return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
		} catch (Exception e) {
			throw apiError("unparseable response");
		}
	}

	private <T> T execute(AtsProviderEnum provider, Supplier<T> call) throws QorvaException {
		for (int attempt = 0; ; attempt++) {
			pace(provider);
			try {
				return call.get();
			} catch (HttpClientErrorException e) {
				if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
					throw new QorvaException(QorvaErrorCodes.ATS_AUTH_FAILED,
						HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
				}
				if (e.getStatusCode().value() == 429 && attempt < MAX_RETRIES) {
					backoff(attempt);
					continue;
				}
				log.warn("ATS {} request failed with {}", provider, e.getStatusCode());
				throw apiError(e.getStatusCode().toString());
			} catch (HttpServerErrorException e) {
				if (attempt < MAX_RETRIES) {
					backoff(attempt);
					continue;
				}
				throw apiError(e.getStatusCode().toString());
			} catch (Exception e) {
				if (attempt < MAX_RETRIES) {
					backoff(attempt);
					continue;
				}
				log.warn("ATS {} request failed: {}", provider, e.getMessage());
				throw apiError(e.getMessage());
			}
		}
	}

	private QorvaException apiError(String detail) {
		return new QorvaException(QorvaErrorCodes.ATS_API_ERROR,
			HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY, detail);
	}

	private void pace(AtsProviderEnum provider) {
		var lock = paceLocks.computeIfAbsent(provider, p -> new Object());
		long interval = MIN_INTERVAL_MS.getOrDefault(provider, 200L);
		synchronized (lock) {
			long now = System.currentTimeMillis();
			long last = lastRequestAt.getOrDefault(provider, 0L);
			long wait = last + interval - now;
			if (wait > 0) {
				try {
					Thread.sleep(wait);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			lastRequestAt.put(provider, System.currentTimeMillis());
		}
	}

	private void backoff(int attempt) {
		try {
			Thread.sleep((long) (500L * Math.pow(2, attempt)));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
