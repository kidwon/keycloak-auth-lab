# 08 · トラブルシューティング

[中文](../08-troubleshooting.md)

| 症状 | 確認・対処 |
|---|---|
| Gradle や依存を取得できない | DNS・プロキシ・Maven Central への接続を確認。TLS 検証は維持 |
| Gradle が動かない | JDK 21、JAVA_HOME、./gradlew --version を確認 |
| Compose 変数がない | .env.example を .env にコピー。既存ファイルは上書きしない |
| Docker permission denied | Docker Desktop の起動と daemon へのアクセス権を確認 |
| invalid_redirect_uri | WEB_ORIGIN + /login/oauth2/code/keycloak と完全一致させる |
| issuer mismatch | 公開 issuer と JWT の iss、realm、ポートを一致させる |
| コンテナーから localhost に接続できない | keycloak:8080 と resource-api:8082 を使用 |
| 正しい署名でも401 | aud、payload typ=Bearer、有効期限、ホスト時刻を確認 |
| 注文 / ADMIN が403 | scope、明示ロール変換、iss + sub の所有権を確認 |
| ユーザーのマシン API 呼び出しが403 | 期待どおり。SERVICE と service:invoke の両方が必要 |
| POST が403 | Web は CSRF 必須。API は Cookie でなく Bearer を使用 |
| CORS エラー | API 呼び出しは Java サーバー側。任意 origin を許可しない |
| ログインループ / state が消える | localhost と127.0.0.1を混在させず、Cookie を維持。古い回送を再利用しない |
| exp / nbf エラー | 時刻を同期。API の許容差は30秒。検証を緩めない |
| refresh に失敗 | 再起動、SSO 期限、ローテーションを確認。再ログインする |
| realm やパスワード変更が反映されない | import は既存 realm を上書きしない。管理画面か明示リセットを使用 |
| ポート競合 | .env とホスト用 URL を同期し、realm を更新 |
| 起動待ちがタイムアウト | docker compose ps で未正常コンポーネントを特定し、smoke を実行 |
| 日本語表示が戻る | 言語は Web セッションに保存。logout / 再起動後は ?lang=ja を再指定 |

```sh
docker compose ps
./scripts/smoke.sh
docker compose logs --tail 100 resource-api web-client
```

通常ログのみ確認し、token / HTTP 本文のログを有効にしません。`.env` を自動読み込みするのは Compose です。IDE・Gradle・Python には README の手順で明示的に渡します。シェルで読み込む環境ファイルは信頼できるものだけを使用してください。

障害報告に JWT、Cookie、認可コード、verifier、secret、id_token_hint 付き URL、完全な HAR を貼らないでください。
