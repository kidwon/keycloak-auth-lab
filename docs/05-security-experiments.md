# 05 · 安全负例实验

只使用独立测试会话、进程内密钥和演示账户，不编辑真实会话的令牌，不提交 HAR、Cookie、verifier、授权码或响应凭据。

```sh
# 不依赖 Docker：真正签名的 JWT 通过完整 API 过滤器链
./gradlew :resource-api:test
# resolver、state、CSRF、claims allowlist、刷新成功与失败
./gradlew :web-client:test
# 依赖已经启动的 Compose；先按 README 加载 .env
python3 scripts/e2e.py
```

| 负例 | 验证方式 | 期望 |
|---|---|---|
| 被篡改 state / 授权回调重放 | MockMvc + Spring 会话请求仓库 | 登录失败重定向 |
| 错误 PKCE verifier | 独立会话 + 真实 Keycloak token endpoint | 400 invalid_grant |
| 修改 JWT payload / 非受信任密钥 | 测试 RSA fixture + Nimbus + MockMvc | 401 |
| 错 issuer / audience / 过期 / 未来 nbf | 签名 fixture | 401 |
| ID Token 当作 bearer | 签名 fixture 与真实 Keycloak | 401 |
| 缺 scope / 缺 ADMIN / 其他 client 的 ADMIN | 完整 API 过滤器链 | 403 |
| Alice 访问其他用户订单 | fixture 与真实登录 | 403 |
| 用户调用机器端点 | fixture 与真实 Keycloak | 403 |
| 错机器 secret | 真实 token endpoint | 401 |
| 网页 POST 无 CSRF | MockMvc | 403 |
| refresh invalid_grant | 隔离 OAuth 响应 fixture | 清理授权客户端 |

不要关闭签名、issuer、audience、CSRF 或 TLS 验证来让负例通过。缺少网络、JDK 或 Docker 时应记录“未运行”，不能把静态检查当作验收成功。
