# 06 · Client Credentials

[中文](../06-client-credentials.md)

マシン間呼び出しではブラウザーや人間のログインを使いません。service-client が自身の secret で認証し、`grant_type=client_credentials` で Access Token を取得します。主体はサービスアカウントです。

```http
POST /realms/auth-lab/protocol/openid-connect/token
Authorization: Basic [REDACTED_CLIENT_CREDENTIALS]
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=service%3Ainvoke
```

audience mapper が resource-api を追加します。ロールはサービスアカウントと client scope mapping の共通部分です。fullScopeAllowed を無効にして SERVICE に制限し、注文 scopes を与えません。API は `ROLE_SERVICE` と `SCOPE_service:invoke` の両方を要求します。

```sh
docker compose --profile tools run --rm --build service-client
# HTTP 200 を示すメッセージを出力。トークン値は出力しません。
```

ホストでは `.env` を読み込んで `./gradlew :service-client:bootRun` を実行します。ポート変更時は `TOKEN_URI` と `API_BASE_URL` を設定してください。CLI はフォームと Basic クライアント認証を使用し、応答本文や HTTP 例外本文を表示しません。

e2e は正しい・誤った secret とユーザーによる越権を検証します。API テストでは role と scope の欠如を別々に検証します。学習パネルのマシン専用ボタンは現在のユーザートークンを使うため、Alice と Admin のどちらでも403が期待されます。
