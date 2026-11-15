package lab.auth.api;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(@Value("${lab.issuer}") String issuer,
                          @Value("${lab.jwk-set-uri}") String jwks) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(TokenPolicy.validator(issuer));
        return decoder;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakAuthorities());
        RequestMatcher bearerRequest = request -> {
            String header = request.getHeader("Authorization");
            return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
        };
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 仅忽略显式 Bearer 请求的 CSRF：此 API 不接受浏览器 Cookie 身份。
                // 无 Bearer 的写请求仍受 CSRF 检查，网页客户端始终保留 CSRF。
                .csrf(csrf -> csrf.ignoringRequestMatchers(bearerRequest))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/api/public").permitAll()
                        .requestMatchers("/api/internal/status").access((authentication, context) -> {
                            var authorities = authentication.get().getAuthorities();
                            boolean allowed = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"))
                                    && authorities.stream().anyMatch(a -> a.getAuthority().equals("SCOPE_service:invoke"));
                            return new org.springframework.security.authorization.AuthorizationDecision(allowed);
                        })
                        .requestMatchers("/api/admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/orders", "/api/orders/**").hasAuthority("SCOPE_orders:read")
                        .requestMatchers(HttpMethod.POST, "/api/orders").hasAuthority("SCOPE_orders:write")
                        .requestMatchers("/api/me").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
