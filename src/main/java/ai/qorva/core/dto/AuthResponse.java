package ai.qorva.core.dto;

public record AuthResponse(JwtDTO jwt, UserDTO user) {
}
