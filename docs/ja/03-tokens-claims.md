# 03 · トークンと Claims

[中文](../03-tokens-claims.md)

| 種類 | 受信者 | 本実装での扱い |
|---|---|---|
| ID Token | web-client | OIDC 認証の結果。検証後に許可された claims を表示 |
| Access Token | resource-api | Bearer で送信。aud は resource-api を含む必要あり |
| Refresh Token | Keycloak token endpoint | サーバー内のみで保管・ローテーション |

`iss` は発行者、`sub` は発行者の範囲内の主体識別子です。主体のキーは `iss + sub` です。UUID は sub の値の一例にすぎず、ユーザー名を永続的な識別子として使用しません。`aud` は受信者、`exp` は有効期限、`iat` は発行時刻、`nbf` は利用開始時刻です。通常の時刻表現は Unix 秒で、画面では ISO 形式になる場合があります。

JWT の payload は読めますが、それだけでは信頼できません。API は JWKS の公開鍵で署名を検証し、issuer、時刻、audience と Keycloak payload の `typ=Bearer` を確認します。header の `typ=JWT` だけでは ID/Access Token を区別できません。Nimbus は公開鍵をキャッシュし、未知の kid が現れた場合に鍵を再取得します。

audience mapper は Access Token のみに resource-api を追加します。テストでは、resource-api の audience を持つ ID 型 fixture も拒否することを確認します。Web での表示も署名・issuer・audience の確認後に許可リストを適用し、メールや任意の個人属性を透過しません。

Access Token の有効期間は120秒です。Web manager は期限の60秒前以降、または期限切れ後に必要に応じて refresh grant を使い、新しいトークンを保存します。invalid_grant の場合は認可クライアントの保管情報を消し、再ログインを要求します。

CSRF 付き POST の logout でローカルセッションと保管情報を削除し、Keycloak の RP-initiated logout に進みます。プロトコルの `id_token_hint` に ID Token を含める場合があるため、リダイレクト URL を記録しないでください。logout は発行済み JWT の即時失効を保証しません。
