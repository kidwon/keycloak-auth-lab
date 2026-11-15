# 06 · Client Credentials

服务间调用不需要浏览器或人类用户。service-client 使用自己的 secret 向 token endpoint 认证，`grant_type=client_credentials` 获取 Access Token。主体是服务账户，不能把它描述成人类用户身份。

```http
POST /realms/auth-lab/protocol/openid-connect/token
Authorization: Basic [REDACTED_CLIENT_CREDENTIALS]
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=service%3Ainvoke
```

令牌 audience mapper 包含 resource-api；角色来自服务账户与 client scope mapping 的交集。realm 关闭 service-client 的 fullScopeAllowed，只允许 SERVICE，默认 scopes 不含订单权限。API 同时要求 `ROLE_SERVICE` 和 `SCOPE_service:invoke`。

推荐执行不会输出令牌的 CLI：

```sh
docker compose --profile tools run --rm --build service-client
# 预期：机器身份 service-client：GET /api/internal/status -> 200
```

宿主执行时先加载 `.env`，运行 `./gradlew :service-client:bootRun`。如修改 Keycloak/API 端口，设置 `TOKEN_URI` 与 `API_BASE_URL`。CLI 使用表单请求和 Basic 客户端认证，不打印原始响应或异常正文。

`python3 scripts/e2e.py` 验证正确凭据、错误凭据和用户身份越权；安全测试分别验证缺 SERVICE 与缺 scope 的情况。学习面板的“机器专属端点”用当前**用户**令牌发起请求，因此 Alice/Admin 均应得到 403。
