package ai.qorva.core.service.mailbox;

import ai.qorva.core.dao.entity.MailboxConnection;
import ai.qorva.core.dao.repository.MailboxConnectionRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.CandidateOutreachData.MailboxState;
import ai.qorva.core.dto.MailboxConnectionData;
import ai.qorva.core.enums.MailboxProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A recruiter's connected mailbox: connect through OAuth, keep the tokens fresh, send as them,
 * disconnect. Everything is keyed by (tenant, user) — a connection is personal and never shared
 * across a team, so the caller's login email is resolved to their user id here.
 */
@Slf4j
@Service
public class MailboxConnectionService {

	private final MailboxConnectionRepository repository;
	private final MailboxOauthService oauthService;
	private final MailboxTokenCipher tokenCipher;
	private final UserRepository userRepository;
	private final Map<MailboxProviderEnum, MailboxSender> senders;

	public MailboxConnectionService(MailboxConnectionRepository repository, MailboxOauthService oauthService,
	                                MailboxTokenCipher tokenCipher, UserRepository userRepository,
	                                List<MailboxSender> senders) {
		this.repository = repository;
		this.oauthService = oauthService;
		this.tokenCipher = tokenCipher;
		this.userRepository = userRepository;
		this.senders = senders.stream().collect(Collectors.toMap(MailboxSender::provider, Function.identity()));
	}

	public MailboxConnectionData.Availability availability() {
		return new MailboxConnectionData.Availability(oauthService.isConfigured(MailboxProviderEnum.MICROSOFT));
	}

	public Optional<MailboxConnectionData.View> view(String tenantId, String username) {
		return findMine(tenantId, username).map(c -> new MailboxConnectionData.View(
			c.getProvider(), c.getEmailAddress(), c.getStatus(), c.getConnectedAt(), c.getLastUsedAt()));
	}

	/** What the composer needs: can this user send from Qorva, and through which mailbox. */
	public record ComposerState(MailboxState state, String emailAddress) {}

	public ComposerState composerState(String tenantId, String username) {
		return findMine(tenantId, username)
			.map(c -> new ComposerState(
				MailboxConnection.STATUS_ACTIVE.equals(c.getStatus()) ? MailboxState.MICROSOFT : MailboxState.REAUTH_REQUIRED,
				c.getEmailAddress()))
			.orElse(new ComposerState(MailboxState.NONE, null));
	}

	public String startOauth(String tenantId, String username, String providerValue) throws QorvaException {
		var provider = parseProvider(providerValue);
		var userId = requireUserId(username);
		return oauthService.buildConsentUrl(provider, tenantId, userId);
	}

	/** Callback leg: exchange the code, read the mailbox address, upsert the user's single connection. */
	public MailboxConnection createFromOauth(MailboxOauthService.StateClaims claims, String code) throws QorvaException {
		var tokens = oauthService.exchangeCode(claims.provider(), code);
		var email = sender(claims.provider()).resolveEmailAddress(tokens);

		var connection = repository.findByTenantIdAndUserId(claims.tenantId(), claims.userId())
			.orElseGet(() -> MailboxConnection.builder()
				.tenantId(claims.tenantId())
				.userId(claims.userId())
				.build());
		connection.setProvider(claims.provider().name());
		connection.setEmailAddress(email);
		connection.setEncryptedTokens(tokenCipher.encrypt(tokens));
		connection.setStatus(MailboxConnection.STATUS_ACTIVE);
		connection.setLastError(null);
		connection.setConnectedAt(Instant.now());
		var saved = repository.save(connection);
		log.info("Mailbox {} connected for user {} (tenant {})", email, claims.userId(), claims.tenantId());
		return saved;
	}

	public void disconnect(String tenantId, String username) {
		var userId = userIdOf(username);
		if (userId == null) return;
		// Microsoft has no per-app revoke endpoint; the user can also revoke at myaccount.microsoft.com.
		var removed = repository.deleteByTenantIdAndUserId(tenantId, userId);
		log.info("Mailbox disconnected for user {} (tenant {}): {} row(s)", userId, tenantId, removed);
	}

	/**
	 * Sends as the caller. Refreshes tokens first; a refused refresh or a 401 from the provider
	 * flips the connection to REAUTH_REQUIRED so the composer can offer "Reconnect" right away.
	 */
	public MailboxSender.SendResult send(String tenantId, String username, String to, String subject, String body)
		throws QorvaException {
		var connection = findMine(tenantId, username)
			.orElseThrow(() -> new QorvaException(QorvaErrorCodes.MAILBOX_NOT_CONNECTED,
				HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
		var provider = MailboxProviderEnum.fromValue(connection.getProvider());
		if (provider == null) {
			throw new QorvaException(QorvaErrorCodes.MAILBOX_PROVIDER_UNKNOWN,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		try {
			var refreshed = oauthService.ensureFreshToken(provider, tokenCipher.decrypt(connection.getEncryptedTokens()));
			if (refreshed.changed()) {
				connection.setEncryptedTokens(tokenCipher.encrypt(refreshed.tokens()));
				repository.save(connection);
			}
			var result = sender(provider).send(connection, refreshed.tokens(), to, subject, body);
			connection.setLastUsedAt(Instant.now());
			connection.setLastError(null);
			connection.setStatus(MailboxConnection.STATUS_ACTIVE);
			repository.save(connection);
			return result;
		} catch (QorvaException e) {
			if (QorvaErrorCodes.MAILBOX_REAUTH_REQUIRED.equals(e.getMessage())) {
				connection.setStatus(MailboxConnection.STATUS_REAUTH_REQUIRED);
				connection.setLastError("Provider rejected the stored token");
				repository.save(connection);
			}
			throw e;
		}
	}

	// -------------------------------------------------------------------------

	private Optional<MailboxConnection> findMine(String tenantId, String username) {
		var userId = userIdOf(username);
		return userId == null ? Optional.empty() : repository.findByTenantIdAndUserId(tenantId, userId);
	}

	private String userIdOf(String username) {
		var user = userRepository.findByEmail(username);
		return user != null ? user.getId() : null;
	}

	private String requireUserId(String username) throws QorvaException {
		var userId = userIdOf(username);
		if (userId == null) {
			throw new QorvaException(QorvaErrorCodes.HTTP_FORBIDDEN, HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN);
		}
		return userId;
	}

	private MailboxSender sender(MailboxProviderEnum provider) throws QorvaException {
		var sender = senders.get(provider);
		if (sender == null) {
			throw new QorvaException(QorvaErrorCodes.MAILBOX_PROVIDER_UNKNOWN,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		return sender;
	}

	private static MailboxProviderEnum parseProvider(String value) throws QorvaException {
		var provider = MailboxProviderEnum.fromValue(value);
		if (provider == null) {
			throw new QorvaException(QorvaErrorCodes.MAILBOX_PROVIDER_UNKNOWN,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		return provider;
	}
}
