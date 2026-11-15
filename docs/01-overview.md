# 01 · 概念与架构

OAuth 2.0 定义授权：客户端如何取得访问资源的权限。OIDC 在其上添加身份认证，使用 `openid` scope、ID Token 和 userinfo。JWT 是有结构的令牌格式，并不是登录协议；签名不等于加密。

Keycloak 是身份提供方与授权服务器，管理 realm、用户、clients、角色、scopes 和签名密钥。Spring Security 在网页端承担 OAuth2 Client，在 API 端承担 Resource Server。

| 组件 | 职责 | 身份与存储 |
|---|---|---|
| web-client | 登录、换码、刷新、服务端 API 调用 | Cookie 会话；令牌在内存服务中 |
| resource-api | JWT 与授权校验 | 无状态，仅接受 Authorization Bearer |
| service-client | Client Credentials | 机器服务账户，无人类登录 |
| Keycloak | 签发令牌与 SSO | PostgreSQL 持久化 realm 与会话 |

按顺序阅读后续文档。先用 Alice 体验自己的订单，再尝试订单 2 和 ADMIN 端点，最后用 Admin 重复操作。通过状态码解释每一步：认证与授权分开判断。

公开数据用于无认证 smoke：

```sh
curl -i http://localhost:8082/api/public  # 200
curl -i http://localhost:8082/api/me      # 401
```

优先解释协议与访问决策，不添加 SPA 或不必要服务。机器客户端是一次性 CLI，主应用数据为内存演示订单。
