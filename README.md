# Keycloak Auth Lab

中文 · [日本語](README.ja.md)

用于本地学习 OAuth 2.0、OIDC、Keycloak 与 Spring Security 的可观察实验工程。采用 **Gradle 多模块、Java 21、Spring Boot 3.5.16、Keycloak 26.8.2、PostgreSQL 17.6**。学习页和文档使用中文，附日语面试问答。需求见 [PRD](keycloak-auth-lab-PRD.md)。

## 快速启动

需要 Docker Engine/Desktop 与 Compose v2，以及可访问镜像仓库、Gradle 与 Maven Central 的网络。容器构建不要求宿主安装 Java。

```sh
cp .env.example .env
# 可修改 .env 中的本地演示密码；不要放入真实凭据。
docker compose up -d --build --wait --wait-timeout 240
./scripts/smoke.sh
```

首次构建与 Keycloak 初始化需要时间；`--wait` 等待数据库、Keycloak 与两个 Spring 应用健康，smoke 再独立检查公开端点与 401 行为。访问 <http://localhost:8080>，点击 Keycloak 登录。使用 `alice` 或 `admin`，密码分别取自 `.env` 的 `ALICE_PASSWORD`、`ADMIN_PASSWORD`。Keycloak 管理控制台 <http://localhost:8081/admin> 使用 `KEYCLOAK_ADMIN` 与 `KEYCLOAK_ADMIN_PASSWORD`，与 realm 用户 `admin` 是不同账户。

网页右上方可切换中文 / 日本語，日语入口为 <http://localhost:8080/?lang=ja>。语言保存在 Web 会话中，注销后需重新选择；API 原始演示数据、CLI 输出和 Keycloak 自身页面不随网页语言切换。

登录后可分别查看 ID/Access Token 的允许 claims，执行 API 实验、自动刷新与注销。默认端口仅绑定 `127.0.0.1`：网页 8080、Keycloak 8081、API 8082；数据库不向宿主暴露端口。

```sh
# 机器身份：获取 Client Credentials 令牌并调用机器专属端点
docker compose --profile tools run --rm --build service-client
# 运行真实授权码、错误 verifier、权限、刷新与注销实验（Python 3.10+）
set -a
. ./.env
set +a
python3 scripts/e2e.py
```

e2e 只创建独立演示会话，凭据与令牌保留在进程内存，不输出 HTTP 响应体、授权码或令牌。它会创建一条演示订单，内存订单随 API 重启重置。

## 结构与架构

```text
web-client/       OIDC confidential client、Thymeleaf 中文学习页、服务端令牌存储
resource-api/     JWT 校验、scope/角色映射、内存订单与归属授权
service-client/   一次性机器调用 CLI（无 HTTP 监听端口）
keycloak/        可重复导入的 realm、用户、clients、scopes 与 audience mapper
docs/            八篇学习说明与验收记录
scripts/         无凭据 smoke 与独立真实协议实验
```

浏览器 → web-client → Keycloak → 回调 → 服务端换码 → resource-api。资源 API 使用 JWKS 本地验证签名，不逐请求调用 introspection；Nimbus 的远程 JWKS 缓存支持未知 `kid` 时重新获取密钥。

## 本地构建、测试与 IDE 调试

使用 **JDK 21**；设置 `JAVA_HOME` 后运行：

```sh
./gradlew --version
./gradlew clean build
./gradlew :resource-api:test :web-client:test
# HTML 测试报告：各模块 build/reports/tests/test/index.html
```

JUnit 5 + Spring Security Test 使用进程内 RSA 测试密钥，不依赖 Keycloak/Docker。刷新测试使用隔离的 OAuth 响应 fixture；真实 Keycloak 的错误 PKCE verifier、换码和注销由 `scripts/e2e.py` 验证。CI 分别运行单元/安全集成测试及 Compose 端到端实验，任何失败都会使任务失败。未设置百分比覆盖率门槛，以 [验收映射](docs/09-verification.md) 为准。

IDE 中导入根 `settings.gradle`，Gradle JVM 选择 JDK 21。只启动基础设施并加载变量：

```sh
docker compose up -d --wait postgres keycloak
set -a
. ./.env
set +a
# 分别在加载相同环境的两个终端运行
./gradlew :resource-api:bootRun
./gradlew :web-client:bootRun
# 另一个终端可运行机器 CLI
./gradlew :service-client:bootRun
```

也可在 IDE 运行对应 `*Application.main`，为网页提供 `WEB_CLIENT_SECRET`，为机器 CLI 提供 `SERVICE_CLIENT_SECRET`；IDE 不会自动读取 `.env`。健康检查为各应用 `/actuator/health`。本地默认 URL 已在配置中给出。

## 网络与配置

公开 issuer 固定为 `http://localhost:8081/realms/auth-lab`。浏览器授权、ID/Access Token 的 `iss` 和校验器使用同一个公开值。Compose 内的换码、userinfo、JWKS 使用 `http://keycloak:8080/...`，不会把容器内 `localhost` 当作 Keycloak。网页通过显式配置的标准 OIDC endpoints 连接；发现元数据可在 `/.well-known/openid-configuration` 查看，API 用显式 JWKS 地址解耦启动与网络。

若端口冲突，修改 `.env` 的 `KEYCLOAK_PORT`、`WEB_PORT`、`API_PORT`。Compose 会同步公开 URL；宿主运行或 e2e 时还要设置匹配的 `ISSUER_URI`、`KEYCLOAK_BACKCHANNEL`、`WEB_ORIGIN`、`API_BASE_URL`、`JWK_SET_URI`、`TOKEN_URI`（只设置相应程序需要的项）。**修改端口、realm 或密码后，已有 realm 不会自动覆盖**，使用下面的全量重置或通过管理台明确修改；重新登录。

## API 与预期结果

| 请求 | 条件 | 结果 |
|---|---|---|
| GET /api/public | 无令牌 | 200 |
| GET /api/me | 有效 Access Token | 200；无效/缺失为 401 |
| GET /api/orders | orders:read | 200，仅返回本人订单；ADMIN 可读全部 |
| POST /api/orders | orders:write | 201（创建成功） |
| GET /api/admin | ADMIN 角色 | 200；Alice 为 403 |
| GET /api/orders/1、/2 | owner 或 ADMIN，且 orders:read | 200；其他用户为 403；不存在为 404 |
| GET /api/internal/status | SERVICE 角色且 service:invoke | 200；普通用户为 403 |

订单归属使用 `iss + sub`，演示用户的固定 UUID 与 realm export 对齐。网页默认请求两个订单 scope；缺少 scope 的负例由签名 fixture 测试覆盖。机器账户不授予 USER/ADMIN 与订单 scopes。匿名写请求因缺少 CSRF 为 403；API 的显式 Bearer 请求不依赖 Cookie，会进入令牌与权限校验。

## 安全与维护边界

`.env` 已忽略；`.env.example` 仅含开发占位密码。Token 检查页要求登录，显示 allowlist claims，不显示 JWT 原文、refresh token、verifier 或密码。令牌保存在服务端内存；应用重启后需重新登录，不支持多副本共享。不要启用可能记录请求/响应正文的 TRACE 或 HTTP wire logging。

PKCE S256 在网页客户端与 Keycloak 两端强制启用。Spring Security 处理 request-bound state、nonce 与回调消费；API 校验签名、issuer、exp、nbf、audience 与 Keycloak payload `typ=Bearer`。所有网页 POST 保留 CSRF；无 Cookie 认证的 API 仅对显式 Bearer 请求排除 CSRF，不能将此策略复制到基于会话的接口。

本项目只允许 localhost 开发环境 HTTP；实际部署需 HTTPS、Secure Cookie、外置密钥与持久化会话等方案。Access Token 有效期 120 秒，校验允许 30 秒时钟偏差；注销不会立即撤销已签发 JWT。Refresh Token 启用轮换与禁止重用；多并发刷新需要额外协调，本单实例实验未实现。

依 PRD 固定 Spring Boot 3.x。[3.5.16 官方发布说明](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)表明它是该分支最后的社区开源发布；当前版本组合用于学习，不代表持续社区维护承诺。正式扩展前需评估 Boot 4.x 或受支持的商业补丁，并重新验证安全兼容性。

## 停止与重置

```sh
docker compose down
# 危险：删除本实验数据库卷、Keycloak 用户与配置，随后重新导入 realm
docker compose down -v
docker compose up -d --build --wait --wait-timeout 240
```

详细学习材料从 [概念总览](docs/01-overview.md) 开始。当前生成环境的实际验证范围与未验证项见 [验收记录](docs/09-verification.md)。
