package lab.auth.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class ServiceClientApplication {
    public static void main(String[] args) { SpringApplication.run(ServiceClientApplication.class, args); }

    @Bean
    CommandLineRunner invoke(@Value("${lab.token-uri}") String tokenUri,
            @Value("${lab.api-base-url}") String apiUrl,
            @Value("${lab.client-secret}") String secret) {
        return args -> {
            var body = new LinkedMultiValueMap<String, String>();
            body.add("grant_type", "client_credentials");
            body.add("scope", "service:invoke");
            RestClient http = RestClient.create();
            try {
                Map<?, ?> token = http.post().uri(tokenUri)
                        .headers(headers -> headers.setBasicAuth("service-client", secret))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body)
                        .retrieve().body(Map.class);
                if (token == null || !(token.get("access_token") instanceof String accessToken)) {
                    throw new IllegalStateException("Missing access token");
                }
                var response = http.get().uri(apiUrl + "/api/internal/status")
                        .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().toBodilessEntity();
                System.out.println("机器身份 service-client：GET /api/internal/status -> " + response.getStatusCode().value());
            } catch (org.springframework.web.client.RestClientException exception) {
                // 不传播可能含响应体或凭据的 HTTP 异常。
                throw new IllegalStateException("机器调用失败，请检查服务、客户端凭据、scope 与角色配置。");
            }
        };
    }
}
