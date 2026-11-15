package lab.auth.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiSecurityTest.Keys.class)
class ApiSecurityTest {
    static final String ISSUER = "http://localhost:8081/realms/auth-lab";
    static final String ALICE = "11111111-1111-4111-8111-111111111111";
    static final KeyPair KEY = keyPair();
    @Autowired MockMvc mvc;

    @TestConfiguration
    static class Keys {
        @Bean @Primary
        JwtDecoder fixtureDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
            decoder.setJwtValidator(TokenPolicy.validator(ISSUER));
            return decoder;
        }
    }

    @Test void publicIsAnonymousAndProtectedRequiresToken() throws Exception {
        mvc.perform(get("/api/public")).andExpect(status().isOk());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/status")).andExpect(status().isUnauthorized());
    }

    @Test void validTokenAndScopePermitAccess() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", bearer(Map.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sub").value(ALICE));
        mvc.perform(get("/api/orders").header("Authorization", bearer(Map.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[?(@.id == 2)]").isEmpty());
    }

    @Test void missingScopeAndRoleAreForbidden() throws Exception {
        mvc.perform(get("/api/orders").header("Authorization", bearer(Map.of("scope", "profile"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin").header("Authorization", bearer(Map.of())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").header("Authorization", bearer(Map.of())))
                .andExpect(status().isForbidden());
    }

    @Test void scopedWriteNeedsBearerAndDoesNotUseSessionCookies() throws Exception {
        mvc.perform(post("/api/orders")).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").header("Authorization", bearer(Map.of("scope", "orders:write"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerSubject").value(ALICE));
    }

    @Test void ownershipAndAdminAreEnforced() throws Exception {
        mvc.perform(get("/api/orders/1").header("Authorization", bearer(Map.of()))).andExpect(status().isOk());
        mvc.perform(get("/api/orders/2").header("Authorization", bearer(Map.of()))).andExpect(status().isForbidden());
        String admin = bearer(Map.of("realm_access", Map.of("roles", List.of("ADMIN"))));
        mvc.perform(get("/api/orders/2").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/orders/9999").header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test void resourceClientRolesAreMappedButOtherClientRolesAreIgnored() throws Exception {
        mvc.perform(get("/api/admin").header("Authorization", bearer(Map.of("resource_access",
                Map.of("resource-api", Map.of("roles", List.of("ADMIN"))))))).andExpect(status().isOk());
        mvc.perform(get("/api/admin").header("Authorization", bearer(Map.of("resource_access",
                Map.of("untrusted-client", Map.of("roles", List.of("ADMIN"))))))).andExpect(status().isForbidden());
    }

    @Test void machineNeedsBothRoleAndScope() throws Exception {
        mvc.perform(get("/api/internal/status").header("Authorization", bearer(Map.of())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/internal/status").header("Authorization", bearer(Map.of("scope", "service:invoke"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/internal/status").header("Authorization", bearer(Map.of("realm_access", Map.of("roles", List.of("SERVICE"))))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/internal/status").header("Authorization", bearer(Map.of("scope", "service:invoke",
                "realm_access", Map.of("roles", List.of("SERVICE")))))).andExpect(status().isOk());
    }

    @Test void invalidClaimsAndIdTokensAreUnauthorized() throws Exception {
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("iss", "https://wrong.example.invalid"), Map.of("aud", List.of("web-client")),
                Map.of("typ", "ID"), Map.of("exp", Date.from(Instant.now().minusSeconds(120))),
                Map.of("nbf", Date.from(Instant.now().plusSeconds(120))))) {
            mvc.perform(get("/api/me").header("Authorization", bearer(invalid))).andExpect(status().isUnauthorized());
        }
    }

    @Test void wrongSignatureAndAlteredPayloadAreUnauthorized() throws Exception {
        String other = sign(Map.of(), keyPair());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + other)).andExpect(status().isUnauthorized());
        String[] parts = sign(Map.of(), KEY).split("\\.");
        String altered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"attacker\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + altered)).andExpect(status().isUnauthorized());
    }

    static String bearer(Map<String, Object> changes) throws JOSEException { return "Bearer " + sign(changes, KEY); }

    static String sign(Map<String, Object> changes, KeyPair key) throws JOSEException {
        var builder = new JWTClaimsSet.Builder().issuer(ISSUER).subject(ALICE).audience("resource-api")
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(120)))
                .claim("typ", "Bearer").claim("scope", "orders:read")
                .claim("realm_access", Map.of("roles", List.of("USER")));
        changes.forEach(builder::claim);
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), builder.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate()));
        return jwt.serialize();
    }

    static KeyPair keyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }
}
