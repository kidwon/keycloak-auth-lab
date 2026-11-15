# 09 · 验收映射与生成环境记录

## 实际执行结果（2026-11-13）

| 检查 | 结果 |
|---|---|
| `docker compose --env-file .env.example config --quiet` | 通过，包含环境插值与配置结构检查 |
| `python3 -m json.tool keycloak/realm-export.json` | 通过，JSON 格式有效 |
| `python3 -m py_compile scripts/e2e.py` | 通过，Python 语法有效 |
| `sh -n gradlew scripts/smoke.sh` | 通过，POSIX shell 语法有效 |
| JDK Compiler API，`--release 21` 解析 15 个 Java 文件 | 通过，仅语法解析，不含依赖解析和类型检查 |
| e2e 未加载环境变量的入口 | 正确退出并提示缺少变量；没有输出凭据 |
| Gradle build / JUnit | **未运行成功**：下载 Gradle 时 services.gradle.org 无法解析；Maven Central 同样 DNS 失败。宿主默认 JDK 26，README 要求 JDK 21 |
| 使用已安装 Gradle 的尝试 | 沙箱不允许 Gradle 文件锁监听所需 socket；未进入项目编译 |
| Docker 启动与端到端实验 | **未验证**：执行环境无法访问 Docker daemon socket |
| IDE 构建检查 | 返回无错误，但只识别根目录模块，没有 Gradle 子模块编译产物，因此不计为编译通过 |

静态检查不证明 Java 编译、镜像可拉取、realm 可导入或登录可用。仓库已配置 CI；本次没有触发远程 CI，也没有宣称其通过。恢复 JDK 21、网络与 Docker 访问后，按下方步骤完成验收。

## 需求与验证入口

| PRD | 实现位置 | 自动化 / 人工验证 |
|---|---|---|
| F01 本地启动 | compose.yaml、realm-export.json、README | Compose CI、smoke；新卷首次启动 |
| F02 Code + PKCE / state | OAuthConfig、Spring 回调过滤器 | WebSecurityTest；e2e 独立错误 verifier 与实际网页登录 |
| F03 区分令牌 | SafeClaims、中文学习页、TokenPolicy | allowlist / 模板测试；e2e ID Token API 401 |
| F04 JWT 校验 | TokenPolicy、Nimbus decoder | ApiSecurityTest 的真实 RSA 签名 fixtures |
| F05 授权与归属 | KeycloakAuthorities、OrderService、过滤器链 | ApiSecurityTest、e2e Alice/Admin |
| F06 刷新 / 注销 | manager、服务端存储、OIDC logout handler | RefreshTokenTest、WebSecurityTest、e2e grant 与真实注销 |
| F07 Client Credentials | service-client、realm 服务账户 | e2e 正确/错误凭据；API 的机器 role/scope 负例 |
| F08 学习材料 | docs/01–08、中文 UI | 文档检查与浏览器人工阅读 |

## 在可运行环境中复核

```sh
# JDK 21，正常外网
./gradlew clean build
cp .env.example .env  # 仅首次执行，不覆盖已有配置
# 构建、启动、导入全新 realm
docker compose up -d --build --wait --wait-timeout 240
./scripts/smoke.sh
set -a
. ./.env
set +a
python3 scripts/e2e.py
docker compose --profile tools run --rm --build service-client
```

首次可运行验证中需确认固定镜像 tag 可用、Keycloak realm 导入兼容性、Health 以及全部 e2e 通过。不要在未验证时把任何一项标记通过。

## 人工观察项与已知边界

日语版补充检查：三个日语模板的 HTML 语言标记与 POST 表单、两种语言的消息键一致性、九篇日语文档的本地链接均通过静态检查。新增 LocaleConfig 后，16 个 Java 文件通过 Java 21 语法解析；不代表编译或 JUnit 通过。语言选择持久化、恢复中文和日语操作提示已添加 MockMvc 测试，尚待可执行环境运行。

- 浏览器检查中文界面、窄屏布局、键盘焦点、claims 分区及表单响应；不要保存含凭据的截图。
- 登录后等待约 65 秒再访问 `/learn`，观察刷新提示与 exp 更新；真实 refresh grant 与服务端刷新存储另有各自自动测试，此等待演示尚未自动覆盖。
- 检查授权 GET 的参数名称；token POST 在服务端，使用隔离 e2e 说明字段，而非拦截正常会话。
- 更换 Keycloak 签名 key 后用新登录令牌访问 API，验证实际 JWKS 轮换；本次未实施真实轮换实验。
- 停止并重新启动应用验证登录恢复；修改公开端口后按 README 同步配置并重新导入 realm。
- 本实验不是生产 IAM；仅单实例、内存授权客户端和订单，未实现多副本刷新协调或即时 JWT 撤销。
