package lab.auth.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "lab.client-secret=test-fixture-only")
@AutoConfigureMockMvc
class WebSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired OAuth2AuthorizationRequestResolver resolver;
    @Autowired ClientRegistrationRepository clients;
    @Autowired OAuth2AuthorizedClientService service;
    @MockitoBean OAuth2AuthorizedClientManager manager;
    @MockitoBean JwtDecoder decoder;

    @Test void confidentialClientUsesS256AndUniqueState() throws Exception {
        var servlet = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
        servlet.setServletPath("/oauth2/authorization/keycloak");
        var first = resolver.resolve(servlet);
        var second = resolver.resolve(servlet);
        String verifier = first.getAttribute("code_verifier");
        assertThat(verifier).hasSizeBetween(43, 128);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        assertThat(first.getAdditionalParameters()).containsEntry("code_challenge_method", "S256")
                .containsEntry("code_challenge", challenge);
        assertThat(first.getState()).isNotBlank().isNotEqualTo(second.getState());
        assertThat(first.getAuthorizationRequestUri()).doesNotContain("code_verifier", "test-fixture-only");
    }

    @Test void mismatchedStateRejectsCallbackAndCorrectStateConsumesRequest() throws Exception {
        var start = mvc.perform(get("/oauth2/authorization/keycloak")).andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        var repository = new HttpSessionOAuth2AuthorizationRequestRepository();
        // 从服务端会话取得本次测试的 state，不记录或显示真实会话内容。
        String location = start.getResponse().getRedirectedUrl();
        var query = org.springframework.web.util.UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        String state = java.net.URLDecoder.decode(query.getFirst("state"), StandardCharsets.UTF_8);
        mvc.perform(get("/login/oauth2/code/keycloak").session(session).param("state", "tampered-fixture")
                        .param("code", "fake-code"))
                .andExpect(redirectedUrl("/?loginError"));
        // 用 OAuth 错误回调证明匹配的请求被一次性消费，无需访问 token endpoint。
        mvc.perform(get("/login/oauth2/code/keycloak").session(session).param("state", state)
                        .param("error", "access_denied"))
                .andExpect(redirectedUrl("/?loginError"));
        var check = new MockHttpServletRequest();
        check.setSession(session);
        check.setParameter("state", state);
        assertThat(repository.loadAuthorizationRequest(check)).isNull();
        mvc.perform(get("/login/oauth2/code/keycloak").session(session).param("state", state)
                        .param("code", "replayed-fixture")).andExpect(redirectedUrl("/?loginError"));
    }

    @Test void claimsPageRequiresLoginAndFormsRequireCsrf() throws Exception {
        mvc.perform(get("/learn")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/call").with(oidcLogin()).param("action", "me")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(oidcLogin())).andExpect(status().isForbidden());
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test void arbitraryProxyTargetsAreRejected() throws Exception {
        mvc.perform(post("/call").with(oidcLogin()).with(csrf()).param("action", "http://evil.invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test void safeClaimsExcludeReusableCredentialsAndExtraPersonalData() {
        assertThat(SafeClaims.select(Map.of("sub", "fixture", "access_token", "secret", "refresh_token", "secret",
                "email", "private@example.invalid", "code_verifier", "secret")))
                .containsOnlyKeys("sub");
    }

    @Test void japaneseSelectionPersistsAndChineseCanBeRestored() throws Exception {
        var selected = mvc.perform(get("/").param("lang", "ja"))
                .andExpect(status().isOk()).andExpect(view().name("ja/index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lang=\"ja\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Keycloak でログイン")))
                .andReturn();
        var session = (MockHttpSession) selected.getRequest().getSession(false);
        mvc.perform(get("/").session(session)).andExpect(view().name("ja/index"));
        mvc.perform(get("/learn").session(session)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/call").session(session).with(oidcLogin()).with(csrf()).param("action", "me"))
                .andExpect(view().name("ja/result"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("呼び出しに失敗しました")));
        mvc.perform(get("/").session(session).param("lang", "zh_CN"))
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("使用 Keycloak 登录")));
    }

    @Test void learningPageRendersSafeClaimsAndLogoutClearsServerStorage() throws Exception {
        var registration = clients.findByRegistrationId("keycloak");
        var now = java.time.Instant.now();
        var client = new OAuth2AuthorizedClient(registration, "user",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fixture-raw-access", now, now.plusSeconds(120)),
                new OAuth2RefreshToken("fixture-raw-refresh", now));
        when(manager.authorize(any())).thenReturn(client);
        when(decoder.decode("fixture-raw-access")).thenReturn(Jwt.withTokenValue("fixture-raw-access")
                .header("alg", "RS256").subject("user").claim("scope", "orders:read")
                .claim("private_data", "fixture-private").build());
        var login = oidcLogin().clientRegistration(registration)
                .idToken(token -> token.claim("preferred_username", "alice")
                        .claim("private_data", "fixture-private-id"));
        mvc.perform(get("/learn").with(login)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("orders:read")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixture-raw"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixture-private"))));
        service.saveAuthorizedClient(client, new org.springframework.security.authentication.TestingAuthenticationToken("user", null));
        mvc.perform(post("/logout").with(login).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(service.<OAuth2AuthorizedClient>loadAuthorizedClient("keycloak", "user")).isNull();
    }
}
