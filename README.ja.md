# Keycloak Auth Lab

[中文](README.md) · 日本語

OAuth 2.0、OIDC、Keycloak、Spring Security をローカルで学ぶ実験プロジェクトです。**Gradle マルチモジュール、Java 21、Spring Boot 3.5.16、Keycloak 26.8.2、PostgreSQL 17.6** を使用します。要件の原本は [PRD（中国語）](keycloak-auth-lab-PRD.md) です。

## 起動する

Docker Engine/Desktop と Compose v2、およびイメージレジストリー・Gradle・Maven Central に接続できるネットワークが必要です。コンテナーから起動する場合、ホスト側の Java は不要です。

```sh
cp .env.example .env
# ローカルのデモ用パスワードのみ設定してください。
docker compose up -d --build --wait --wait-timeout 240
./scripts/smoke.sh
```

初回はビルドと Keycloak の初期化に時間がかかります。`--wait` はデータベース、Keycloak、Web、API の正常起動を待ちます。<http://localhost:8080/?lang=ja> を開き、Keycloak でログインしてください。

- ユーザー：`alice` / `admin`。パスワードは `.env` の `ALICE_PASSWORD` / `ADMIN_PASSWORD`。
- 管理コンソール：<http://localhost:8081/admin>。`KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` を使用します。realm の `admin` とは別のアカウントです。
- 公開ポートは `127.0.0.1` のみ：Web 8080、Keycloak 8081、API 8082。DB はホストに公開しません。

画面上部で日本語と中国語を切り替えられます。初期言語は中国語で、選択は Web セッションに保存されます。ログアウト後は選び直してください。API の JSON データ、CLI の出力、Keycloak 自身のログイン画面はこの言語切り替えの対象外です。

## 構成

| ディレクトリー | 役割 |
|---|---|
| web-client/ | OIDC confidential client、学習画面、サーバー側トークン保管 |
| resource-api/ | JWT 検証、scope・ロール・注文の所有権確認 |
| service-client/ | Client Credentials を使う一度限りの CLI |
| keycloak/ | realm、デモユーザー、clients、scopes、audience mapper |
| docs/ja/ | 日本語の学習資料と検証記録 |
| scripts/ | smoke チェックと独立した実プロトコルの実験 |

ブラウザー → web-client → Keycloak → コールバック → サーバー側のトークン交換 → resource-api の順に処理します。API は JWKS の公開鍵で署名を検証します。リクエストごとに introspection を呼び出す構成ではありません。

## ビルド・テスト・IDE

ローカルでは **JDK 21** を選び、`JAVA_HOME` を設定してください。

```sh
./gradlew --version
./gradlew clean build
./gradlew :resource-api:test :web-client:test
```

JUnit 5 と Spring Security Test はプロセス内の RSA テスト鍵を使い、Docker に依存しません。レポートは各モジュールの `build/reports/tests/test/index.html` に生成されます。カバレッジ率の数値目標はなく、[要件と検証の対応](docs/ja/09-verification.md)を確認します。

IDE でルートの `settings.gradle` を読み込み、Gradle JVM に JDK 21 を指定します。

```sh
docker compose up -d --wait postgres keycloak
set -a
. ./.env
set +a
# 同じ環境変数を読み込んだ別々のターミナルで実行
./gradlew :resource-api:bootRun
./gradlew :web-client:bootRun
# マシン用 CLI
./gradlew :service-client:bootRun
```

IDE から `*Application.main` を実行する場合、Web に `WEB_CLIENT_SECRET`、CLI に `SERVICE_CLIENT_SECRET` を渡してください。IDE、Gradle、Python は `.env` を自動では読み込みません。ヘルスチェックは `/actuator/health` です。

## 実際の認証・認可を試す

```sh
docker compose --profile tools run --rm --build service-client
# Compose が起動済みで、Python 3.10+ を使用する場合
set -a
. ./.env
set +a
python3 scripts/e2e.py
```

e2e は専用のデモセッションを作成し、誤った verifier、ID Token の誤用、他人の注文、マシン認証、refresh、logout を検証します。認証情報やトークンはメモリー内でのみ扱い、値や HTTP 応答本文は出力しません。注文を1件作成しますが、API の再起動で初期状態に戻ります。

| API | 必要条件 / 結果 |
|---|---|
| GET /api/public | 匿名で200 |
| GET /api/me | 有効な Access Token で200、欠如・無効は401 |
| GET /api/orders | orders:read、本人の注文のみ。ADMIN は全件 |
| POST /api/orders | orders:write、作成成功は201 |
| GET /api/admin | ADMIN で200、Alice は403 |
| GET /api/orders/1、/2 | read scope と所有権または ADMIN。権限不足403、存在しない場合404 |
| GET /api/internal/status | SERVICE と service:invoke の両方で200 |

Web は注文の read/write scopes を要求します。scope 不足の負例は署名済み fixture で検証します。匿名の書き込みは CSRF 検証で403になります。API は Cookie による認証を受け付けず、明示的な Bearer リクエストのみ CSRF 検証の対象外です。Web の POST では CSRF を維持します。

## URL とコンテナーネットワーク

公開 issuer は `http://localhost:8081/realms/auth-lab` です。認可リクエストと JWT の `iss`、検証設定は同じ公開値を使います。コンテナー内の token・userinfo・JWKS は `http://keycloak:8080/...`、API は `http://resource-api:8082` を使用します。

Web は標準 OIDC endpoints を明示設定し、API は JWKS URL を明示設定します。公開 discovery は `/.well-known/openid-configuration` で確認できます。issuer をコンテナーの DNS 名に置き換えないでください。

ポート変更は `.env` の `KEYCLOAK_PORT`、`WEB_PORT`、`API_PORT` で行います。ホスト実行と e2e では必要に応じて `ISSUER_URI`、`KEYCLOAK_BACKCHANNEL`、`WEB_ORIGIN`、`API_BASE_URL`、`JWK_SET_URI`、`TOKEN_URI` も更新します。既存 realm は起動時 import で上書きされないため、パスワードやポート変更後は管理設定の更新、または明示的な初期化が必要です。

## セキュリティと制限

`.env` はコミット対象外です。実際のパスワード、JWT、認可コード、verifier、Cookie、refresh token をログや画像に残さないでください。学習画面は認証必須で、許可リストにある claims のみ表示します。TRACE や HTTP wire logging は有効にしないでください。

PKCE S256 は Web と Keycloak の両方で要求します。Spring が state・nonce とコールバックの消費を処理し、API は署名、issuer、exp、nbf、audience、payload の `typ=Bearer` を検証します。主体のキーは `iss + sub` です。

トークンと注文はサーバーメモリー内のため、再起動後は再ログインが必要です。複数レプリカや同時 refresh の調整は未実装です。Access Token は120秒、API の時刻許容差は30秒です。logout は発行済み JWT の即時失効を保証しません。

HTTP は localhost の開発専用です。本番には HTTPS、Secure Cookie、秘密情報管理、永続的なセッション等の設計が必要です。PRD に合わせて Boot 3.x を固定していますが、[3.5.16 は同系列最後の OSS リリース](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)です。正式な拡張時はサポートされる版への移行と再検証を検討してください。

## 停止と初期化

```sh
docker compose down
# 危険：この実験の DB ボリューム、Keycloak ユーザーと設定を削除
docker compose down -v
docker compose up -d --build --wait --wait-timeout 240
```

[学習資料](docs/ja/01-overview.md)と[実際の検証状況](docs/ja/09-verification.md)を参照してください。生成環境ではネットワーク・Docker・Gradle の実行制限があり、Java のビルドと実際のログインを検証済みとはしていません。
