package ai.qorva.core.service.sso;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.config.MailboxProperties;
import ai.qorva.core.dao.entity.SsoLoginCode;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.OauthStateSigner;
import ai.qorva.core.service.QorvaUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * "Sign in with Microsoft" (OpenID Connect, authorization code + PKCE, multi-tenant Entra app shared with the mailbox
 * feature). Signs in an <b>existing, active</b> Qorva user whose email Microsoft vouches for; it never creates
 * accounts (invites stay the way users join). The browser only ever carries a signed {@code state}, then a
 * single-use one-minute code; the session token is handed over by {@code POST /auth/sso/exchange}.
 *
 * <p>Which email counts: {@code preferred_username} (the UPN of a work account, on a domain the organisation has
 * verified; the verified address of a personal account), else {@code email} only when Microsoft marks its domain
 * verified ({@code xms_edov}). A bare {@code email} claim is never trusted: an Entra admin can set it to anything.</p>
 */
@Slf4j
@Service
public class MicrosoftSsoService {

	static final String SCOPE = "openid profile email";
	static final long STATE_TTL_SECONDS = 600;
	static final long CODE_TTL_SECONDS = 60;
	static final String ISSUER_PREFIX = "https://login.microsoftonline.com/";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final MicrosoftSsoClient client;
	private final MailboxProperties mailboxProperties;
	private final AtsProperties atsProperties;
	private final UserRepository userRepository;
	private final QorvaUserDetailsService userDetailsService;
	private final MongoTemplate mongoTemplate;

	public MicrosoftSsoService(MicrosoftSsoClient client, MailboxProperties mailboxProperties, AtsProperties atsProperties,
	                           UserRepository userRepository, QorvaUserDetailsService userDetailsService, MongoTemplate mongoTemplate) {
		this.client = client;
		this.mailboxProperties = mailboxProperties;
		this.atsProperties = atsProperties;
		this.userRepository = userRepository;
		this.userDetailsService = userDetailsService;
		this.mongoTemplate = mongoTemplate;
	}

	public boolean isAvailable() {
		return client.isConfigured() && StringUtils.hasText(mailboxProperties.getCredentialsKey());
	}

	/** Registered in the Entra app as a Web redirect URI, byte for byte. */
	public String redirectUri() {
		return atsProperties.getPublicBaseUrl() + "/auth/sso/microsoft/callback";
	}

	/** Where the browser goes to sign in; {@code loginHint} pre-fills the Microsoft account picker. */
	public String authorizeUrl(String loginHint) throws QorvaException {
		assertAvailable();
		var nonce = UUID.randomUUID().toString();
		var state = signer().sign(List.of(nonce, String.valueOf(Instant.now().getEpochSecond() + STATE_TTL_SECONDS)));
		var url = new StringBuilder(client.authority()).append("/oauth2/v2.0/authorize")
			.append("?client_id=").append(enc(client.clientId()))
			.append("&response_type=code")
			.append("&response_mode=query")
			.append("&redirect_uri=").append(enc(redirectUri()))
			.append("&scope=").append(enc(SCOPE))
			.append("&state=").append(enc(state))
			.append("&nonce=").append(enc(nonce))
			.append("&code_challenge=").append(challenge(verifier(nonce)))
			.append("&code_challenge_method=S256")
			.append("&prompt=select_account");
		if (StringUtils.hasText(loginHint)) {
			url.append("&login_hint=").append(enc(loginHint.strip()));
		}
		return url.toString();
	}

	/** The callback: checks Microsoft's answer and returns the one-time code for the app. */
	public String complete(String code, String state) throws QorvaException {
		assertAvailable();
		var nonce = nonceOf(state);
		if (!StringUtils.hasText(code)) throw failed("no code");
		var idToken = client.exchangeCode(code, redirectUri(), verifier(nonce), SCOPE);
		if (!StringUtils.hasText(idToken)) throw failed("no id_token");
		Map<String, Object> claims;
		try {
			claims = client.decode(idToken).getClaims();
		} catch (Exception e) {
			throw failed("id_token rejected: " + e.getMessage());
		}
		var email = identityOf(claims, client.clientId(), nonce);
		var user = findActiveUser(email);
		log.info("Microsoft sign-in for user {} (tenant {}, entra tid {})", user.getId(), user.getTenantId(), claims.get("tid"));
		return issueCode(user);
	}

	/** Trades the one-time code for the user, once. */
	public User exchange(String code) throws QorvaException {
		if (!StringUtils.hasText(code)) throw QorvaErrors.badRequest(QorvaErrorCodes.AUTH_SSO_CODE_INVALID);
		var query = Query.query(Criteria.where("codeHash").is(sha256Hex(code)).and("expiresAt").gt(Instant.now()));
		var used = mongoTemplate.findAndRemove(query, SsoLoginCode.class);
		if (used == null) throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_SSO_CODE_INVALID);
		// Looked up in the tenant the code was issued for, never by id alone.
		var user = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(used.getUserId()))
			.and("tenantId").is(new ObjectId(used.getTenantId()))), User.class);
		if (user == null || !active(user)) throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_SSO_NO_ACCOUNT);
		return user;
	}

	// -------------------------------------------------------------------------

	/**
	 * The verified email in the ID token, or a failure: audience is this app, the nonce is the one sent, the issuer
	 * is the Microsoft tenant the token says it comes from.
	 */
	static String identityOf(Map<String, Object> claims, String clientId, String nonce) throws QorvaException {
		var aud = claims.get("aud");
		boolean audienceOk = aud instanceof Collection<?> list ? list.contains(clientId) : clientId.equals(aud);
		if (!audienceOk) throw failed("audience");
		if (!nonce.equals(claims.get("nonce"))) throw failed("nonce");
		var tid = claims.get("tid") instanceof String s ? s : null;
		var iss = claims.get("iss") == null ? null : claims.get("iss").toString();
		if (!StringUtils.hasText(tid) || !(ISSUER_PREFIX + tid + "/v2.0").equals(iss)) throw failed("issuer");
		var preferred = claims.get("preferred_username") instanceof String s ? s.strip() : null;
		if (preferred != null && preferred.contains("@")) return preferred;
		var email = claims.get("email") instanceof String s ? s.strip() : null;
		if (email != null && email.contains("@") && Boolean.TRUE.equals(claims.get("xms_edov"))) return email;
		throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_SSO_NO_ACCOUNT);
	}

	private User findActiveUser(String email) throws QorvaException {
		var user = userRepository.findByEmail(email);
		if (user == null) {
			// Microsoft may return the address in another case than the one the user was invited with.
			user = mongoTemplate.findOne(Query.query(Criteria.where("email").regex("^" + Pattern.quote(email) + "$", "i")), User.class);
		}
		if (user == null || !active(user)) throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_SSO_NO_ACCOUNT);
		return user;
	}

	/** The same account checks a password sign-in gets (disabled, locked, deleted). */
	private boolean active(User user) {
		try {
			var details = userDetailsService.loadUserByUsername(user.getEmail());
			return details.isEnabled() && details.isAccountNonLocked() && details.isAccountNonExpired();
		} catch (Exception e) {
			return false;
		}
	}

	private String issueCode(User user) {
		var bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		var code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		var now = Instant.now();
		mongoTemplate.insert(SsoLoginCode.builder().codeHash(sha256Hex(code)).userId(user.getId()).tenantId(user.getTenantId())
			.createdAt(now).expiresAt(now.plusSeconds(CODE_TTL_SECONDS)).build());
		return code;
	}

	private String nonceOf(String state) throws QorvaException {
		try {
			var parts = signer().verify(state);
			if (parts.length != 2 || Instant.now().getEpochSecond() > Long.parseLong(parts[1])) throw new IllegalStateException("expired");
			return parts[0];
		} catch (Exception e) {
			throw failed("state: " + e.getMessage());
		}
	}

	/** PKCE verifier derived from the nonce with a server secret: nothing to store, nothing guessable from the URL. */
	String verifier(String nonce) {
		var hex = OauthStateSigner.hmacHex(("pkce|" + nonce).getBytes(StandardCharsets.UTF_8), mailboxProperties.getCredentialsKey());
		return Base64.getUrlEncoder().withoutPadding().encodeToString(HexFormat.of().parseHex(hex));
	}

	static String challenge(String verifier) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static String sha256Hex(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private OauthStateSigner signer() {
		return new OauthStateSigner(mailboxProperties.getCredentialsKey());
	}

	private void assertAvailable() throws QorvaException {
		if (!isAvailable()) {
			throw new QorvaException(QorvaErrorCodes.AUTH_SSO_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE.value(), HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	private static QorvaException failed(String why) {
		log.warn("Microsoft sign-in refused: {}", why);
		return QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_SSO_FAILED);
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
