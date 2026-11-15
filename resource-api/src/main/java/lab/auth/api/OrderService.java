package lab.auth.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class OrderService {
    public record Order(long id, String ownerIssuer, String ownerSubject, String description) { }
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(2);

    public OrderService(@Value("${lab.issuer}") String issuer) {
        orders.put(1L, new Order(1L, issuer, "11111111-1111-4111-8111-111111111111", "Alice 的演示订单"));
        orders.put(2L, new Order(2L, issuer, "22222222-2222-4222-8222-222222222222", "Admin 的演示订单"));
    }

    public List<Order> list(Jwt jwt, Authentication auth) {
        return orders.values().stream().filter(order -> canRead(order, jwt, auth))
                .sorted(java.util.Comparator.comparingLong(Order::id)).toList();
    }

    public Order get(long id, Jwt jwt, Authentication auth) {
        Order order = orders.get(id);
        if (order == null) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        if (!canRead(order, jwt, auth)) { throw new AccessDeniedException("Order belongs to another subject"); }
        return order;
    }

    public Order create(Jwt jwt) {
        long id = nextId.incrementAndGet();
        Order order = new Order(id, jwt.getIssuer().toString(), jwt.getSubject(), "新建演示订单");
        orders.put(id, order);
        return order;
    }

    private boolean canRead(Order order, Jwt jwt, Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                || (order.ownerIssuer().equals(jwt.getIssuer().toString()) && order.ownerSubject().equals(jwt.getSubject()));
    }
}
