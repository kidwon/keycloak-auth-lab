package lab.auth.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain webSecurity(HttpSecurity http, ClientRegistrationRepository clients,
            OAuth2AuthorizationRequestResolver resolver, OAuth2AuthorizedClientRepository repository,
            OAuth2AuthorizedClientService service, @Value("${lab.web-origin}") String origin) throws Exception {
        var logout = new OidcClientInitiatedLogoutSuccessHandler(clients);
        logout.setPostLogoutRedirectUri(origin + "/");
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/style.css", "/error", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                        .authorizedClientRepository(repository)
                        .defaultSuccessUrl("/learn", true)
                        .failureUrl("/?loginError"))
                .logout(config -> config.addLogoutHandler((request, response, authentication) -> {
                    if (authentication != null) {
                        service.removeAuthorizedClient("keycloak", authentication.getName());
                    }
                }).logoutSuccessHandler(logout))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'self'")));
        return http.build();
    }
}
