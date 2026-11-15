# 04 · Scope、角色与对象归属

认证回答“谁发起请求”，授权回答“能做什么”。scope 通过 Spring 的 `SCOPE_` authority 表示；Keycloak realm roles 与 resource-api client roles 由显式 converter 映射为 `ROLE_USER`、`ROLE_ADMIN`、`ROLE_SERVICE`。其他 client 的同名角色不提升 API 权限。

| 身份 / 操作 | 预期 |
|---|---|
| 匿名 GET /api/me | 401 |
| Alice GET /api/admin | 403 |
| Admin GET /api/admin | 200 |
| 带 orders:read 的 Alice GET /api/orders/1 | 200 |
| 同上 GET /api/orders/2 | 403 |
| 带 orders:read 的 Admin GET /api/orders/1 | 200 |
| 有效令牌但缺 orders:read 的订单请求 | 403 |
| 带 orders:write 的 POST /api/orders | 201 |
| 普通用户调用机器端点 | 403 |

`GET /api/orders` 只列出本人订单；Admin 可列出全部。单条订单要求 read scope 并检查 `iss + sub`；不存在返回 404，存在但不属于调用者返回 403。这是本实验明确采用的对象存在性策略，真实业务可统一为 404。Admin 免除归属限制，但不自动免除 scope。

网页默认请求两个订单 scopes。没有 scope 的签名 fixture、同名其他客户端角色、缺失机器角色/scope 都由 API 测试覆盖。realm 中机器账户只授予 SERVICE，普通用户没有 SERVICE。

网页中的 API 按钮是带 CSRF 的 POST；网页服务器发送 Bearer 凭据到无状态 API。API 不接受 Cookie 认证；仅显式 Bearer 请求排除 CSRF，其余请求保留检查。不要给 Cookie 会话接口套用此例外。

使用学习面板执行可复现的普通用户和管理员操作，无需复制 JWT 到终端。请求和响应字段用固定端点白名单限定，客户端不能通过表单任意代理 URL。
