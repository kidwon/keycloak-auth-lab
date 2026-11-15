package lab.auth.api;

import java.time.Duration;
import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

public final class TokenPolicy {
    private TokenPolicy() { }

    public static OAuth2TokenValidator<Jwt> validator(String issuer) {
        // Keycloak Access Token 的 payload typ 为 Bearer；ID Token 为 ID。
        // aud 与 typ 同时检查，不能只检查 JWT header 的 typ=JWT。
        OAuth2TokenValidator<Jwt> accessToken = token ->
                "Bearer".equals(token.getClaimAsString("typ")) && token.getExpiresAt() != null
                && token.getSubject() != null && !token.getSubject().isBlank() && token.getIssuedAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "An expiring Keycloak access token is required", null));
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(issuer),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("resource-api")),
                accessToken);
    }
}
