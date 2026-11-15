package lab.auth.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SafeClaims {
    private static final List<String> ALLOWED = List.of("iss", "sub", "aud", "exp", "iat", "nbf", "typ", "preferred_username", "scope");
    private SafeClaims() { }

    public static Map<String, Object> select(Map<String, Object> claims) {
        Map<String, Object> safe = new LinkedHashMap<>();
        ALLOWED.forEach(key -> {
            if (claims.containsKey(key)) { safe.put(key, claims.get(key)); }
        });
        return safe;
    }
}
