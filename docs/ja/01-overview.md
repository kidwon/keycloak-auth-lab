# 01 · 概念と構成

[日本語 README](../../README.ja.md) · [中文](../01-overview.md)

OAuth 2.0 は、クライアントにリソースへのアクセス権限を委譲する仕組みです。OIDC は `openid` scope、ID Token、userinfo によって認証を追加します。JWT はトークンの形式で、ログインプロトコルそのものではありません。署名は暗号化とは異なります。

Keycloak は認可サーバー兼 IdP として realm、ユーザー、clients、ロール、scopes、署名鍵を管理します。Spring Security は Web 側で OAuth2 Client、API 側で Resource Server として動作します。

| コンポーネント | 責務 | 保管・認証 |
|---|---|---|
| web-client | ログイン、交換、更新、API 呼び出し | Cookie セッション、トークンはサーバーメモリー |
| resource-api | JWT と認可の検証 | ステートレス、Bearer のみ |
| service-client | Client Credentials | マシン専用、対話ログインなし |
| Keycloak | トークン発行と SSO | realm とセッションを PostgreSQL に永続化 |

まず Alice で自分の注文を読み、注文2と ADMIN エンドポイントを試します。その後 Admin で同じ操作を行い、認証と認可の違いを説明してください。

```sh
curl -i http://localhost:8082/api/public  # 200
curl -i http://localhost:8082/api/me      # 401
```

不要な SPA やサービスを増やさず、プロトコルとアクセス判定の理解を優先します。マシンクライアントは CLI、注文はメモリー内のデモデータです。
