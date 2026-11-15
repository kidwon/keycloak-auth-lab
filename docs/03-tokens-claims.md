# 03 · Token 与 Claims

| 类型 | 消费者 | 本项目处理 |
|---|---|---|
| ID Token | web-client | Spring 验证 OIDC 身份；仅显示允许 claims |
| Access Token | resource-api | Authorization Bearer；aud 必须含 resource-api |
| Refresh Token | Keycloak token endpoint | 服务端内存保存、轮换；不发送到浏览器 |

`iss` 是签发者；`sub` 是签发者命名空间中的主体标识。身份主键为 `iss + sub`；UUID 只是 sub 的一种取值，不能以用户名作为长期身份键。`aud` 表示预期接收者，`exp` 为到期时间，`iat` 为签发时间，`nbf` 为开始可用时间。时间值通常是 Unix 秒数，界面可能显示对应的 ISO 时间。

JWT header / payload 可解码，不保密。不要把“成功解码”当成“通过验证”。API 从配置的 JWKS 地址读取公钥，允许受信任 RSA 签名，校验 issuer、时间及 audience；缓存与按 kid 获取新公钥支持正常轮换。额外要求 Keycloak payload `typ=Bearer`；仅检查 header `typ=JWT` 无法区分 ID 与 Access Token。

realm 的 audience mapper **只**将 resource-api 写入 Access Token。测试还构造带 resource-api audience 的 ID 类型 fixture，证明类型策略仍然拒绝它。网页端的 Access Token 展示也先验签与验证 issuer/audience，再执行 claims allowlist；邮箱、其他个人资料与任意额外 claims 不透传。

Access Token 有效期为 120 秒。网页 manager 在到期前 60 秒内或过期后需要令牌时发起 refresh grant，并保存新令牌。失效 refresh grant 清理授权客户端；用户需要重新登录。访问学习页能看到本次是否刷新。

注销通过带 CSRF 的 POST 清理本地会话与授权客户端，再执行 Keycloak RP-initiated logout。协议可能通过 `id_token_hint` 向 Keycloak 传递 ID Token，这是规范内的服务端注销跳转；不要记录跳转 URL。JWT 在生命周期与时钟偏差内仍可能被 API 接受，注销不是即时全局撤销。
