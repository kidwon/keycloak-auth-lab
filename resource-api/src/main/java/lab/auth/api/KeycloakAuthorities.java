package lab.auth.api;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class KeycloakAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopes.convert(jwt));
        addRoles(authorities, jwt.getClaim("realm_access"));
        Object clients = jwt.getClaim("resource_access");
        if (clients instanceof Map<?, ?> map) {
            addRoles(authorities, map.get("resource-api"));
        }
        return authorities;
    }

    private void addRoles(Set<GrantedAuthority> authorities, Object access) {
        if (access instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            roles.stream().filter(String.class::isInstance).map(String.class::cast)
                    .filter(role -> Set.of("USER", "ADMIN", "SERVICE").contains(role))
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).forEach(authorities::add);
        }
    }
}
