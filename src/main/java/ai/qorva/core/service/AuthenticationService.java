package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaErrors;

import ai.qorva.core.security.TenantScope;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.AuthResponse;
import ai.qorva.core.dto.MfaData;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import ai.qorva.core.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class AuthenticationService {
	private final QorvaUserDetailsService userDetailsService;
	private final UserRepository userRepository;
	private final AuthenticationManager authenticationManager;
	private final JwtConfig jwtConfig;
	private final UserMapper userMapper;
	private final TenantService tenantService;
	private final MfaService mfaService;

	@Autowired
	public AuthenticationService(
		QorvaUserDetailsService userDetailsService,
		UserRepository userRepository,
		AuthenticationManager authenticationManager,
		JwtConfig jwtConfig, UserMapper userMapper, TenantService tenantService, MfaService mfaService) {
		this.userDetailsService = userDetailsService;
		this.userRepository = userRepository;
		this.authenticationManager = authenticationManager;
		this.jwtConfig = jwtConfig;
		this.userMapper = userMapper;
		this.tenantService = tenantService;
		this.mfaService = mfaService;
	}

	/**
	 * Password step. With email MFA off this signs the user in as before; with it on, no token is
	 * minted yet — a code is emailed and the response only carries the challenge to answer.
	 */
	public AuthResponse authenticate(UserDTO userDTO) throws QorvaException {
		User user;
		try {
			// Authenticate user
			this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(userDTO.getEmail(), userDTO.getRawPassword()));

			// Retrieve the tenantId from the database
			user = Optional.ofNullable(this.userRepository.findByEmail(userDTO.getEmail()))
				           .orElseThrow(() -> new QorvaException(QorvaErrorCodes.AUTH_USER_NOT_FOUND));
		} catch (Exception e) {
			throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_FAILED);
		}

		if (user.isMfaEnabledOrFalse()) {
			return AuthResponse.mfaRequired(this.mfaService.issueLogin(user));
		}
		return completeLogin(user);
	}

	/** Second step of an MFA sign-in: a valid code yields exactly what a plain login returns. */
	public AuthResponse verifyMfa(String challengeId, String code) throws QorvaException {
		return completeLogin(this.mfaService.verifyLogin(challengeId, code));
	}

	public MfaData.Challenge resendMfa(String challengeId) throws QorvaException {
		return this.mfaService.resendLogin(challengeId);
	}

	private AuthResponse completeLogin(User user) throws QorvaException {
		try {
			// Get the authenticated user's details
			UserDetails userDetails = this.userDetailsService.loadUserByUsername(user.getEmail());

			// Sign-in happens before any token exists: the verified user's own tenant is the scope.
			var tenant = TenantScope.callAs(user.getTenantId(), () -> Optional.ofNullable(this.tenantService.findOneById(user.getTenantId()))
				                 .orElseThrow(() -> new QorvaException(QorvaErrorCodes.AUTH_USER_NOT_FOUND)));

			// Generate a JWT including tenantId
			var jwt = JwtUtils.generateAndBuildToken(userDetails, jwtConfig, tenant);

			// Add subscription status to the JWT
			var authenticatedUserInfo = this.userMapper.map(user);
			authenticatedUserInfo.setTenant(tenant);

			// Build AuthResponse
			return new AuthResponse(jwt, authenticatedUserInfo);
		} catch (Exception e) {
			throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_FAILED);
		}
	}

	public Boolean isTokenValid(String authorizationHeader) throws QorvaException {
		if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
			String token = authorizationHeader.substring(7);
			boolean expired;
			try {
				expired = Boolean.TRUE.equals(JwtUtils.isTokenExpired(token, jwtConfig.getSecretKey()));
			} catch (ExpiredJwtException ex) {
				// jjwt refuses to parse an expired token at all, so expiry arrives here rather
				// than as a true return value.
				expired = true;
			} catch (JwtException ex) {
				// Malformed, truncated or wrongly signed: a failed auth check, not a server
				// fault — the same answer refreshToken gives for the same input.
				throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_TOKEN_INVALID);
			}
			if (expired) {
				throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_TOKEN_EXPIRED);
			}
			return true;
		}
		return false;
	}

	public AuthResponse refreshToken(String authorizationHeader) throws QorvaException {
		if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
			String token = authorizationHeader.substring(7);
			try {
				// Extract the username
				String username = JwtUtils.extractUsername(token, this.jwtConfig.getSecretKey());

				// Load user details to issue a new token
				UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

				// Retrieve the tenantId from the database
				var user = Optional.ofNullable(this.userRepository.findByEmail(username))
					               .orElseThrow(() -> new QorvaException(QorvaErrorCodes.AUTH_USER_NOT_FOUND));

				// Get the tenant status and subscription plan, in the user's own tenant scope
				var tenant = TenantScope.callAs(user.getTenantId(), () -> Optional.ofNullable(this.tenantService.findOneById(user.getTenantId()))
					                 .orElseThrow(() -> new QorvaException(QorvaErrorCodes.AUTH_USER_NOT_FOUND)));

				// Add subscription status to the JWT
				var authenticatedUserInfo = this.userMapper.map(user);
				authenticatedUserInfo.setTenant(tenant);

				// Return the new access token
				var jwt = JwtUtils.generateAndBuildToken(userDetails, this.jwtConfig, tenant);

				// return results
				return new AuthResponse(jwt, authenticatedUserInfo);
			} catch (JwtException ex) {
				throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_TOKEN_INVALID);
			}
		}
		throw QorvaErrors.unauthorized(QorvaErrorCodes.AUTH_TOKEN_INVALID);
	}
}
