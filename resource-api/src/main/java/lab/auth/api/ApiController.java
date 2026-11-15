package lab.auth.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ApiController {
    private final OrderService orders;
    public ApiController(OrderService orders) { this.orders = orders; }

    @GetMapping("/api/public")
    Map<String, String> publicInfo() { return Map.of("message", "公开端点，无需令牌"); }

    @GetMapping("/api/me")
    Map<String, Object> me(@AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return Map.of("iss", jwt.getIssuer().toString(), "sub", jwt.getSubject(),
                "aud", jwt.getAudience(), "authorities", auth.getAuthorities().stream().map(Object::toString).toList());
    }

    @GetMapping("/api/orders")
    List<OrderService.Order> list(@AuthenticationPrincipal Jwt jwt, Authentication auth) { return orders.list(jwt, auth); }

    @GetMapping("/api/orders/{id}")
    OrderService.Order get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return orders.get(id, jwt, auth);
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderService.Order create(@AuthenticationPrincipal Jwt jwt) { return orders.create(jwt); }

    @GetMapping("/api/admin")
    Map<String, String> admin() { return Map.of("message", "ADMIN 权限验证成功"); }

    @GetMapping("/api/internal/status")
    Map<String, String> machine() { return Map.of("message", "机器客户端授权成功", "status", "ok"); }
}
