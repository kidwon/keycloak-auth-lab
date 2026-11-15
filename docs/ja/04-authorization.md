# 04 · Scope・ロール・所有権

[中文](../04-authorization.md)

認証は主体を確認し、認可は許可される操作を判断します。scope は `SCOPE_`、Keycloak の realm roles と resource-api client roles は明示的な converter で `ROLE_USER`、`ROLE_ADMIN`、`ROLE_SERVICE` に変換します。他の client の同名ロールは API 権限として扱いません。

| 操作 | 結果 |
|---|---|
| 匿名 GET /api/me | 401 |
| Alice GET /api/admin | 403 |
| Admin GET /api/admin | 200 |
| orders:read を持つ Alice が注文1を読む | 200 |
| 同じ Alice が注文2を読む | 403 |
| orders:read を持つ Admin が注文1を読む | 200 |
| 有効なトークンでも必要な scope がない | 403 |
| orders:write で POST /api/orders | 201 |
| ユーザートークンでマシン専用 API | 403 |

一覧は本人の注文のみ返し、Admin は全件参照できます。単一注文も read scope と `iss + sub` による所有権、または ADMIN を要求します。存在しない注文は404、存在しても他人の注文なら403とする実験方針です。本番では存在性を隠すため404に統一する設計もあります。Admin も scope は必要です。

Web は read/write scopes を要求します。不足する scope、他 client の ADMIN、SERVICE または service:invoke の欠如は署名済み fixture で検証します。通常ユーザーには SERVICE を付与しません。

Web のボタンは CSRF 付き POST です。サーバーから API へは Bearer を送ります。API は Cookie 認証を受け付けず、明示的な Bearer リクエストのみ CSRF 対象外です。この例外をセッション認証の API に適用しないでください。フォームの操作名は固定許可リストで検証し、任意 URL の代理呼び出しを許可しません。
