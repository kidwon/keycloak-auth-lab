# 09 · 要件対応と検証記録

[中国語の検証記録](../09-verification.md)

## 生成時の検証状況（2026-11-13）

| チェック | 結果 |
|---|---|
| Compose の config --quiet | 成功、構造と環境変数展開を確認 |
| realm-export.json の JSON 構文 | 成功 |
| e2e.py の Python 構文 | 成功 |
| gradlew / smoke.sh の shell 構文 | 成功 |
| Java 21 の構文解析 | 成功。依存解決・型検査は含まない |
| e2e の環境変数不足 | 認証情報を出さずに終了 |
| Gradle / JUnit | 未実行成功。DNS と沙箱の socket 制限によりビルドに到達できず |
| Docker と実際の認証 | daemon の socket にアクセスできず、未検証 |
| IDE build | エラーなしだが子モジュールを認識せず、コンパイル成功とは数えない |

静的チェックはコンパイル、イメージ取得、realm import、ログインの成功を保証しません。CI は設定済みですが、この生成作業からリモート CI は実行していません。日本語ページについても、実際のブラウザー表示と JUnit は実行環境が必要です。

日本語版の追加時には、3つのテンプレートの言語属性と POST フォーム、日中メッセージキーの一致、9文書のローカルリンクを静的に確認しました。LocaleConfig 追加後の16個の Java ファイルも構文解析を通過しました。言語選択の保持、中国語への切り替え、日本語の操作結果には MockMvc テストを追加しましたが、JUnit の実行は未確認です。

## 要件との対応

| 要件 | 実装 | 検証入口 |
|---|---|---|
| F01 起動 | Compose、realm、README | Compose CI、smoke、新規 volume |
| F02 Code + PKCE / state | OAuthConfig、Spring filter | WebSecurityTest、e2e |
| F03 Token 区別 | SafeClaims、学習画面、TokenPolicy | allowlist とテンプレートテスト、ID Token 401 |
| F04 JWT | Nimbus、TokenPolicy | RSA 署名 fixture と API chain |
| F05 認可 | converter、OrderService | ApiSecurityTest、Alice/Admin e2e |
| F06 refresh / logout | manager、サーバー保管、OIDC handler | RefreshTokenTest、WebSecurityTest、e2e |
| F07 マシン認証 | service-client、サービスアカウント | e2e、role/scope 負例 |
| F08 学習資料 | docs、日中の UI | 文書と実ブラウザーの確認 |

## 再検証

```sh
# JDK 21 と正常なネットワークを使用
./gradlew clean build
# 初回のみ実行。既存 .env は維持
cp .env.example .env
docker compose up -d --build --wait --wait-timeout 240
./scripts/smoke.sh
set -a
. ./.env
set +a
python3 scripts/e2e.py
docker compose --profile tools run --rm --build service-client
```

固定イメージ tag の取得、realm import、全 healthcheck と e2e を実際に確認してから完了としてください。

## 手動観察と制限

- `/?lang=ja` と `/?lang=zh_CN` で切り替え、ログイン後の学習画面・操作結果・再ログインを確認します。
- 狭い画面、キーボードのフォーカス、claims の区分を確認します。認証情報を含む画像は保存しません。
- 約65秒待って `/learn` に戻り、自動更新の表示と exp を確認します。この待機デモは自動化していません。
- 実際の Keycloak 鍵更新と JWKS 再取得は未検証です。新しい鍵で発行したトークンを使って確認します。
- 再起動と公開ポート変更後の動作を確認します。
- 単一インスタンス用で、複数レプリカ・同時 refresh の調整や JWT の即時失効は未実装です。
