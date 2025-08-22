package ai.qorva.core.service;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.AuthResponse;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import ai.qorva.core.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.JwtException;
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

	@Autowired
	public AuthenticationService(
		QorvaUserDetailsService userDetailsService,
		UserRepository userRepository,
		AuthenticationManager authenticationManager,
		JwtConfig jwtConfig, UserMapper userMapper, TenantService tenantService) {
		this.userDetailsService = userDetailsService;
		this.userRepository = userRepository;
		this.authenticationManager = authenticationManager;
		this.jwtConfig = jwtConfig;
		this.userMapper = userMapper;
		this.tenantService = tenantService;
	}

	public AuthResponse authenticate(UserDTO userDTO) throws QorvaException {
		try {
			// Authenticate user
			this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(userDTO.getEmail(), userDTO.getRawPassword()));

			// Get the authenticated user's details
			UserDetails userDetails = this.userDetailsService.loadUserByUsername(userDTO.getEmail());

			// Retrieve the tenantId from the database
			var user = Optional.ofNullable(this.userRepository.findByEmail(userDTO.getEmail()))
				               .orElseThrow(() -> new QorvaException("User not found"));

			// Get the tenant status from the database and the subscription plan
			var tenant = Optional.ofNullable(this.tenantService.findOneById(user.getTenantId()))
				                 .orElseThrow(() -> new QorvaException("Tenant not found"));

			// Generate a JWT including tenantId
			var jwt = JwtUtils.generateAndBuildToken(userDetails, jwtConfig, tenant);

			// Add subscription status to the JWT
			var authenticatedUserInfo = this.userMapper.map(user);
			authenticatedUserInfo.setSubscriptionStatus(tenant.getSubscriptionInfo().getSubscriptionStatus());

			// Build AuthResponse
			return new AuthResponse(jwt, authenticatedUserInfo);
		} catch (Exception e) {
			throw new QorvaException(
				"Authentication failed with message: " + e.getMessage(),
				HttpStatus.UNAUTHORIZED.value(),
				HttpStatus.UNAUTHORIZED
			);
		}
	}

	public Boolean isTokenValid(String authorizationHeader) throws QorvaException {
		if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
			String token = authorizationHeader.substring(7);
			if (Boolean.TRUE.equals(JwtUtils.isTokenExpired(token, jwtConfig.getSecretKey()))) {
				throw new QorvaException("Token has expired", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
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
					               .orElseThrow(() -> new QorvaException("User not found"));

				// Get the tenant status from the database and the subscription plan
				var tenant = Optional.ofNullable(this.tenantService.findOneById(user.getTenantId()))
					                 .orElseThrow(() -> new QorvaException("Tenant not found"));

				// Add subscription status to the JWT
				var authenticatedUserInfo = this.userMapper.map(user);
				authenticatedUserInfo.setSubscriptionStatus(tenant.getSubscriptionInfo().getSubscriptionStatus());

				// Return the new access token
				var jwt = JwtUtils.generateAndBuildToken(userDetails, this.jwtConfig, tenant);

				// return results
				return new AuthResponse(jwt, authenticatedUserInfo);
			} catch (JwtException ex) {
				throw new QorvaException(ex.getMessage(), HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
			}
		}
		throw new QorvaException("Invalid token", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
	}
}
