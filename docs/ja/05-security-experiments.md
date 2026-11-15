# 05 · セキュリティの負例実験

[中文](../05-security-experiments.md)

独立したテストセッション、プロセス内の鍵、デモユーザーのみを使います。通常セッションのトークンを書き換えず、HAR、Cookie、verifier、認可コード、認証応答を保存しません。

```sh
./gradlew :resource-api:test
./gradlew :web-client:test
# Compose を起動し、README の手順で .env を読み込んだ後
python3 scripts/e2e.py
```

| 負例 | 検証 | 期待結果 |
|---|---|---|
| state 改変・コールバック再利用 | MockMvc と Spring のセッションリポジトリー | ログイン失敗へのリダイレクト |
| 誤った PKCE verifier | 独立セッションと実 Keycloak | 400 invalid_grant |
| JWT payload 改変・別の署名鍵 | RSA fixture と Nimbus、API フィルターチェーン | 401 |
| issuer / audience 不一致、期限切れ、未来 nbf | 署名済み fixture | 401 |
| ID Token を API に送る | fixture と実 Keycloak | 401 |
| scope / ADMIN 不足、別 client の ADMIN | API フィルターチェーン | 403 |
| Alice が他人の注文を読む | fixture と実認証 | 403 |
| ユーザーがマシン API を呼ぶ | fixture と実 Keycloak | 403 |
| マシン secret が誤り | 実 token endpoint | 401 |
| Web POST の CSRF 欠如 | MockMvc | 403 |
| refresh invalid_grant | 独立した OAuth 応答 fixture | 保管情報を削除 |

署名、issuer、audience、CSRF、TLS 検証を無効にして実験を通さないでください。JDK・ネットワーク・Docker が使えない場合は「未実行」と記録し、静的チェックを実行成功の代わりにしません。
