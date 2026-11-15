package lab.auth.web;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RefreshTokenTest {
    @Test void expiredAccessTokenIsRefreshedAndSavedServerSide() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                .clientId("fixture-client").clientSecret("fixture-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback").authorizationUri("http://localhost/auth")
                .tokenUri("http://localhost/token").build();
        var clients = new InMemoryClientRegistrationRepository(registration);
        OAuth2AuthorizedClientRepository repository = mock(OAuth2AuthorizedClientRepository.class);
        @SuppressWarnings("unchecked")
        OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> tokenClient = mock(OAuth2AccessTokenResponseClient.class);
        when(tokenClient.getTokenResponse(any())).thenReturn(OAuth2AccessTokenResponse.withToken("fixture-new-access")
                .tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(120).refreshToken("fixture-rotated-refresh").build());
        var provider = new RefreshTokenOAuth2AuthorizedClientProvider();
        provider.setAccessTokenResponseClient(tokenClient);
        var manager = new DefaultOAuth2AuthorizedClientManager(clients, repository);
        manager.setAuthorizedClientProvider(provider);
        var principal = new TestingAuthenticationToken("fixture-user", null, "ROLE_USER");
        var expired = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fixture-expired-access",
                Instant.now().minusSeconds(300), Instant.now().minusSeconds(120), Set.of("openid"));
        var existing = new OAuth2AuthorizedClient(registration, principal.getName(), expired,
                new OAuth2RefreshToken("fixture-refresh", Instant.now().minusSeconds(300)));
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var authorize = OAuth2AuthorizeRequest.withAuthorizedClient(existing).principal(principal)
                .attribute(jakarta.servlet.http.HttpServletRequest.class.getName(), request)
                .attribute(jakarta.servlet.http.HttpServletResponse.class.getName(), response).build();
        var refreshed = manager.authorize(authorize);
        assertThat(refreshed.getAccessToken().getTokenValue()).isEqualTo("fixture-new-access");
        assertThat(refreshed.getRefreshToken().getTokenValue()).isEqualTo("fixture-rotated-refresh");
        verify(repository).saveAuthorizedClient(refreshed, principal, request, response);
        when(tokenClient.getTokenResponse(any())).thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")));
        org.junit.jupiter.api.Assertions.assertThrows(OAuth2AuthorizationException.class, () -> manager.authorize(authorize));
        verify(repository).removeAuthorizedClient("keycloak", principal, request, response);
    }
}
