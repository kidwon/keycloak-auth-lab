package lab.auth.web;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class OAuthConfig {
    @Bean
    ClientRegistrationRepository clients(@Value("${lab.issuer}") String issuer,
            @Value("${lab.backchannel}") String backchannel,
            @Value("${lab.web-origin}") String origin,
            @Value("${lab.client-secret}") String secret) {
        // 浏览器使用公开 issuer，容器内 token/JWKS 请求走内部地址；iss 始终保持公开值。
        ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                .clientId("web-client").clientSecret(secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(origin + "/login/oauth2/code/keycloak")
                .scope("openid", "profile", "orders:read", "orders:write")
                .issuerUri(issuer)
                .authorizationUri(issuer + "/protocol/openid-connect/auth")
                .tokenUri(backchannel + "/protocol/openid-connect/token")
                .jwkSetUri(backchannel + "/protocol/openid-connect/certs")
                .userInfoUri(backchannel + "/protocol/openid-connect/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", issuer + "/protocol/openid-connect/logout"))
                .clientName("Keycloak Auth Lab").build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    OAuth2AuthorizationRequestResolver authorizationRequests(ClientRegistrationRepository clients) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        // confidential client 也必须显式开启 PKCE，不能依赖 public client 的默认行为。
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    @Bean
    OAuth2AuthorizedClientService authorizedClients(ClientRegistrationRepository clients) {
        return new InMemoryOAuth2AuthorizedClientService(clients);
    }

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository(OAuth2AuthorizedClientService service) {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(service);
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clients,
            OAuth2AuthorizedClientRepository repository) {
        var manager = new DefaultOAuth2AuthorizedClientManager(clients, repository);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode().refreshToken().build());
        return manager;
    }

    @Bean
    JwtDecoder accessTokenDecoder(@Value("${lab.issuer}") String issuer,
            @Value("${lab.backchannel}") String backchannel) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(backchannel + "/protocol/openid-connect/certs").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<java.util.List<String>>("aud", aud -> aud != null && aud.contains("resource-api"))));
        return decoder;
    }
}
