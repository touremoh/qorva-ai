package ai.qorva.core.controller;

import ai.qorva.core.dto.CandidateUpdateData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CandidateUpdateService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unauthenticated candidate self-update endpoints. Hardened: opaque single-use tokens
 * (the token IS the authorization), uniform 404s (no enumeration), per-IP rate limiting,
 * minimal prefill payload.
 */
@Slf4j
@RestController
@RequestMapping("/public/candidate-update")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class PublicCandidateUpdateController {

	private static final int MAX_REQUESTS_PER_MINUTE_PER_IP = 30;
	// Status polling gets its own, higher budget: at one poll every 3s a single candidate
	// uses 20/min, and corporate NATs put many candidates behind one IP.
	private static final int MAX_STATUS_REQUESTS_PER_MINUTE_PER_IP = 120;

	private final CandidateUpdateService candidateUpdateService;
	private final Cache<String, AtomicInteger> rateLimiter = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofMinutes(1))
		.maximumSize(100_000)
		.build();
	private final Cache<String, AtomicInteger> statusRateLimiter = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofMinutes(1))
		.maximumSize(100_000)
		.build();

	@Autowired
	public PublicCandidateUpdateController(CandidateUpdateService candidateUpdateService) {
		this.candidateUpdateService = candidateUpdateService;
	}

	@GetMapping(path = "/{token}", produces = "application/json")
	public ResponseEntity<CandidateUpdateData.Prefill> getPrefill(
		@PathVariable String token, HttpServletRequest request) throws QorvaException {
		enforceRateLimit(request);
		return ResponseEntity.ok(this.candidateUpdateService.getPrefill(token));
	}

	/**
	 * Fields-only updates complete synchronously (204 — milliseconds, no LLM). A file
	 * upload is staged and processed asynchronously (202 — poll {@code /{token}/status}).
	 */
	@PostMapping(path = "/{token}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Void> submit(
		@PathVariable String token,
		@RequestPart(value = "submission", required = false) CandidateUpdateData.Submission submission,
		@RequestPart(value = "file", required = false) MultipartFile file,
		HttpServletRequest request) throws QorvaException {
		enforceRateLimit(request);
		if (file == null || file.isEmpty()) {
			this.candidateUpdateService.complete(token, submission, null);
			return ResponseEntity.noContent().build();
		}
		this.candidateUpdateService.enqueue(token, submission, file);
		return ResponseEntity.accepted().build();
	}

	@GetMapping(path = "/{token}/status", produces = "application/json")
	public ResponseEntity<CandidateUpdateData.StatusView> status(
		@PathVariable String token, HttpServletRequest request) throws QorvaException {
		enforceRateLimit(request, statusRateLimiter, MAX_STATUS_REQUESTS_PER_MINUTE_PER_IP);
		return ResponseEntity.ok(this.candidateUpdateService.status(token));
	}

	@PostMapping(path = "/{token}/unsubscribe")
	public ResponseEntity<Void> unsubscribe(@PathVariable String token, HttpServletRequest request) throws QorvaException {
		enforceRateLimit(request);
		this.candidateUpdateService.unsubscribe(token);
		return ResponseEntity.noContent().build();
	}

	private void enforceRateLimit(HttpServletRequest request) throws QorvaException {
		enforceRateLimit(request, rateLimiter, MAX_REQUESTS_PER_MINUTE_PER_IP);
	}

	private void enforceRateLimit(HttpServletRequest request, Cache<String, AtomicInteger> limiter, int maxPerMinute)
		throws QorvaException {
		var forwarded = request.getHeader("X-Forwarded-For");
		var ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
		var counter = limiter.get(ip, k -> new AtomicInteger());
		if (counter.incrementAndGet() > maxPerMinute) {
			throw new QorvaException("Too many requests", HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS);
		}
	}
}
