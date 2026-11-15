# 08 · 故障排查

| 现象 | 检查与修复 |
|---|---|
| 无法下载 Gradle / dependencies | 检查 DNS、代理与 Maven Central 网络；不要关闭 TLS 验证 |
| Gradle 无法运行 | 使用 JDK 21，检查 JAVA_HOME 与 `./gradlew --version` |
| Compose 变量缺失 | `cp .env.example .env`；不要提交 .env |
| Docker permission denied | 确认 Docker Desktop 已运行且当前执行环境能访问 daemon |
| invalid_redirect_uri | realm 的回调必须精确等于 WEB_ORIGIN + /login/oauth2/code/keycloak |
| issuer mismatch | 公开 issuer 与 JWT 的 iss 完全一致，包括端口和 realm；不能改为容器 DNS |
| 容器连接 localhost 失败 | backchannel 用 keycloak:8080，API 用 resource-api:8082；localhost 在容器内表示自己 |
| valid signature 仍 401 | 检查 aud 含 resource-api、payload typ 为 Bearer、令牌时间与本机时钟 |
| 订单或 ADMIN 403 | 检查 scope、显式角色映射、owner 的 iss + sub；ADMIN 也需要订单 scope |
| 普通用户访问机器端点 403 | 这是预期；机器身份必须同时具备 SERVICE 与 service:invoke |
| POST 403 | 网页表单需 CSRF；API 使用 Bearer，不使用网页会话 Cookie |
| CORS 报错 | 网页通过 Java 服务端调用 API，本实验不需要宽泛的浏览器 CORS；不要开启任意 origins |
| 登录循环 / state 丢失 | 全程使用 localhost，不混用 127.0.0.1；保留 Cookie；不要复用旧回调或重启后的会话 |
| token 已过期 / nbf 尚未到 | 校准时钟；API 允许 30 秒偏差；通过服务端刷新，不延长校验容忍度 |
| 刷新失败 | 应用可能重启、SSO 到期或 refresh token 被轮换；注销后重新登录 |
| 改密码、realm 文件后无效果 | 启动导入不会覆盖现有 realm；修改管理配置或执行明确的破坏性重置 |
| 端口冲突 | 修改 .env 端口；宿主应用/e2e 的 URL 也需同步；realm 需重新导入 |
| 启动等待超时 | 用 docker compose ps 定位未健康组件；Keycloak 与两个 Spring 应用均有独立 healthcheck，再执行 scripts/smoke.sh |

```sh
docker compose ps
./scripts/smoke.sh
# 谨慎查看默认级别日志，不启用 token / HTTP 正文日志
docker compose logs --tail 100 resource-api web-client
```

`.env` 只被 Compose 自动加载，IDE/Gradle/Python 不会自动读取。按 README 中 `set -a; . ./.env; set +a` 的分行示例加载。`.env` 保持可信的简单 KEY=value 格式，不执行来源不明的环境文件。

不要在故障报告中粘贴 JWT、Cookie、authorization code、verifier、客户端 secret、含 id_token_hint 的注销 URL 或完整 HAR。
