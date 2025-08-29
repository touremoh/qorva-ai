package ai.qorva.core.service;

import ai.qorva.core.dto.JwtDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.JwtMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;

@Slf4j
@Service
public class OAuth2TokenService {

    private final WebClient webClient;
	private final JwtDTO accessToken;
    private final JwtMapper jm;

    @Value("${spring.security.oauth2.client.registration.office365.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.office365.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.office365.token-uri}")
    private String tokenUri;

    public OAuth2TokenService(JwtMapper jm) {
		this.jm = jm;
        this.webClient = WebClient.builder().build();
        this.accessToken = JwtDTO.builder().build();
    }

    public String getAccessToken() throws QorvaException {
        try {
            if (tokenNotExpired()) {
                return this.accessToken.getAccessToken();
            }
            return refreshToken();
        } catch (Exception e) {
            log.error("Error retrieving or refreshing OAuth2 token", e);
            throw new QorvaException("Failed to retrieve OAuth2 token", e, HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean tokenNotExpired() {
        return Instant.now().getEpochSecond() <= this.accessToken.getExpiresIn();
    }

    private String refreshToken() throws QorvaException {
        try {
            String rawResponse = webClient.post()
                .uri(tokenUri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("client_id=" + clientId
                    + "&client_secret=" + clientSecret
                    + "&scope=" + "https://graph.microsoft.com/.default"
                    + "&grant_type=client_credentials")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            log.info("Raw token response: {}", rawResponse);

            // map new token
            var newAccessToken = this.jm.map(rawResponse);

            // save it to the
            this.jm.merge(this.accessToken, newAccessToken);

            return this.accessToken.getAccessToken();
        } catch (WebClientResponseException ex) {
            log.error("Failed to refresh OAuth2 access token: {}", ex.getResponseBodyAsString());
            throw new QorvaException(
                "Failed to refresh OAuth2 access token",
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to parse OAuth2 access token: {}", e.getMessage());
            throw new QorvaException(
                "Failed to parse OAuth2 access token",
                e,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
