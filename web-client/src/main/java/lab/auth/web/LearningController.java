package lab.auth.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Controller
public class LearningController {
    private record Action(HttpMethod method, String path) { }
    private static final Map<String, Action> ACTIONS = Map.of(
            "me", new Action(HttpMethod.GET, "/api/me"),
            "orders", new Action(HttpMethod.GET, "/api/orders"),
            "own", new Action(HttpMethod.GET, "/api/orders/1"),
            "other", new Action(HttpMethod.GET, "/api/orders/2"),
            "create", new Action(HttpMethod.POST, "/api/orders"),
            "admin", new Action(HttpMethod.GET, "/api/admin"),
            "machine", new Action(HttpMethod.GET, "/api/internal/status"));
    private final OAuth2AuthorizedClientManager manager;
    private final OAuth2AuthorizedClientService service;
    private final JwtDecoder decoder;
    private final ObjectMapper json;
    private final RestClient api;
    private final MessageSource messages;

    public LearningController(OAuth2AuthorizedClientManager manager, OAuth2AuthorizedClientService service,
            JwtDecoder decoder, ObjectMapper json, MessageSource messages, @Value("${lab.api-base-url}") String baseUrl) {
        this.manager = manager;
        this.service = service;
        this.decoder = decoder;
        this.json = json;
        this.messages = messages;
        this.api = RestClient.builder().baseUrl(baseUrl).build();
    }

    @GetMapping("/")
    String home(Locale locale) { return view("index", locale); }

    @GetMapping("/learn")
    String learn(@AuthenticationPrincipal OidcUser user, OAuth2AuthenticationToken authentication,
            HttpServletRequest request, HttpServletResponse response, Model model, Locale locale) throws JsonProcessingException {
        model.addAttribute("idClaims", pretty(SafeClaims.select(user.getIdToken().getClaims())));
        model.addAttribute("username", user.getPreferredUsername());
        model.addAttribute("accessClaims", message("token.unavailable", locale));
        model.addAttribute("refreshStatus", message("token.storage", locale));
        try {
            var before = service.loadAuthorizedClient("keycloak", authentication.getName());
            var client = authorize(authentication, request, response);
            if (client != null) {
                model.addAttribute("accessClaims", pretty(SafeClaims.select(decoder.decode(client.getAccessToken().getTokenValue()).getClaims())));
                model.addAttribute("expiresAt", client.getAccessToken().getExpiresAt());
                boolean refreshed = before != null && !before.getAccessToken().getTokenValue().equals(client.getAccessToken().getTokenValue());
                model.addAttribute("refreshStatus", refreshed ? message("token.refreshed", locale) : message("token.current", locale));
            }
        } catch (OAuth2AuthorizationException | JwtException exception) {
            model.addAttribute("refreshStatus", message("token.failed", locale));
        }
        return view("learn", locale);
    }

    @PostMapping("/call")
    String call(@RequestParam String action, OAuth2AuthenticationToken authentication,
            HttpServletRequest request, HttpServletResponse response, Model model, Locale locale) {
        Action target = ACTIONS.get(action);
        if (target == null) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST); }
        model.addAttribute("endpoint", target.method() + " " + target.path());
        try {
            var client = authorize(authentication, request, response);
            if (client == null) { throw new IllegalStateException("Reauthentication required"); }
            var result = api.method(target.method()).uri(target.path())
                    .headers(headers -> headers.setBearerAuth(client.getAccessToken().getTokenValue()))
                    .retrieve().toEntity(String.class);
            model.addAttribute("status", result.getStatusCode().value());
            model.addAttribute("result", result.getBody());
        } catch (RestClientResponseException exception) {
            model.addAttribute("status", exception.getStatusCode().value());
            model.addAttribute("result", message("api.denied", locale));
        } catch (OAuth2AuthorizationException | RestClientException | IllegalStateException exception) {
            model.addAttribute("status", message("api.failed", locale));
            model.addAttribute("result", message("api.unavailable", locale));
        }
        return view("result", locale);
    }

    private OAuth2AuthorizedClient authorize(OAuth2AuthenticationToken authentication,
            HttpServletRequest request, HttpServletResponse response) {
        return manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("keycloak")
                .principal(authentication)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response).build());
    }

    private String message(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }

    private String view(String name, Locale locale) {
        return "ja".equals(locale.getLanguage()) ? "ja/" + name : name;
    }

    private String pretty(Object value) throws JsonProcessingException {
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }
}
