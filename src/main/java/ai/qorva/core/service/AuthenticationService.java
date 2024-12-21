package ai.qorva.core.service;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.JwtDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
	private final QorvaUserDetailsService userDetailsService;
	private final UserRepository userRepository;
	private final AuthenticationManager authenticationManager;
	private final JwtConfig jwtConfig;

	@Autowired
	public AuthenticationService(
		QorvaUserDetailsService userDetailsService,
		UserRepository userRepository,
		AuthenticationManager authenticationManager,
		JwtConfig jwtConfig) {
		this.userDetailsService = userDetailsService;
		this.userRepository = userRepository;
		this.authenticationManager = authenticationManager;
		this.jwtConfig = jwtConfig;
	}

	public JwtDTO authenticate(UserDTO userDTO) throws QorvaException {
		try {
			// Authenticate user
			this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(userDTO.getEmail(), userDTO.getRawPassword()));

			// Get the authenticated user's details
			UserDetails userDetails = this.userDetailsService.loadUserByUsername(userDTO.getEmail());

			// Retrieve the companyId from the database
			var user = this.userRepository
				.findOneByEmail(userDTO.getEmail())
				.orElseThrow(() -> new QorvaException("User not found"));

			// Generate a JWT including companyId
			return JwtUtils.generateAndBuildToken(userDetails, jwtConfig, user.getCompanyId());
		} catch (Exception e) {
			throw new QorvaException("Authentication failed", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}
	}

	public Boolean isTokenValid(String authorizationHeader) throws QorvaException {
		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			String token = authorizationHeader.substring(7);
			if (Boolean.TRUE.equals(JwtUtils.isTokenExpired(token, jwtConfig.getSecretKey()))) {
				throw new QorvaException("Token has expired", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
			}
			return true;
		}
		return false;
	}

	public JwtDTO refreshToken(String authorizationHeader) throws QorvaException {
		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			String token = authorizationHeader.substring(7);
			try {
				// Extract the username
				String username = JwtUtils.extractUsername(token, this.jwtConfig.getSecretKey());

				// Load user details to issue a new token
				UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

				// Retrieve the companyId from the database
				var user = this.userRepository
					.findOneByEmail(username)
					.orElseThrow(() -> new QorvaException("User not found"));

				// Return the new access token
				return JwtUtils.generateAndBuildToken(userDetails, this.jwtConfig, user.getCompanyId());

			} catch (JwtException ex) {
				throw new QorvaException(ex.getMessage(), HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
			}
		}
		throw new QorvaException("Invalid token", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
	}
}
