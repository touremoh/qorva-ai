package ai.qorva.core.utils;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.JwtDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@UtilityClass
public class JwtUtils {

	public String extractUsername(String token, String jwtSecret) {
		return extractClaim(token, Claims::getSubject, jwtSecret);
	}

	public Date extractExpiration(String token, String jwtSecret) {
		return extractClaim(token, Claims::getExpiration, jwtSecret);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver, String jwtSecret) {
		final Claims claims = extractAllClaims(token, jwtSecret);
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token, String jwtSecret) {
		return Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token).getBody();
	}

	public Boolean isTokenExpired(String token, String jwtSecret) {
		return extractExpiration(token, jwtSecret).before(new Date());
	}

	public String generateToken(UserDetails userDetails, JwtConfig jwtConfig) {
		Map<String, Object> claims = new HashMap<>();
		return createToken(claims, userDetails.getUsername(), jwtConfig);
	}

	private String createToken(Map<String, Object> claims, String subject, JwtConfig jwtConfig) {
		return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(new Date(System.currentTimeMillis()))
			.setExpiration(new Date(System.currentTimeMillis() + jwtConfig.getTimeToLiveInMillis()))
			.signWith(SignatureAlgorithm.HS256, jwtConfig.getSecretKey()).compact();
	}

	public Boolean isTokenValid(String token, UserDetails userDetails, String jwtSecret) {
		final String username = extractUsername(token, jwtSecret);
		return (username.equals(userDetails.getUsername()) && !isTokenExpired(token, jwtSecret));
	}

	public JwtDTO generateAndBuildToken(UserDetails userDetails, JwtConfig jwtConfig) {
		return JwtDTO.builder().accessToken(JwtUtils.generateToken(userDetails, jwtConfig)).build();
	}
}
