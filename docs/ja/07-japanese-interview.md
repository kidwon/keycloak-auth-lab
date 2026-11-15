# 07 · 日本語面接 Q&A

[元の資料](../07-japanese-interview.md)

**OAuth 2.0 と OIDC の違いは？**  
OAuth 2.0 はリソースへのアクセス権限を委譲する仕組みです。OIDC はその上に認証を追加し、ID Token でログイン結果をクライアントに伝えます。

**JWT と Keycloak と Spring Security の関係は？**  
JWT はトークン形式です。Keycloak は認可サーバー兼 IdP です。Spring Security はクライアント処理と API 側の検証・認可を担当します。

**Authorization Code + PKCE の流れは？**  
認可リクエストに challenge を付け、コールバックの state を確認し、サーバーが code と verifier を送ってトークンを取得します。S256 は verifier の SHA-256 を Base64URL で符号化した値です。

**state と nonce と PKCE の役割は？**  
state はリクエストとブラウザーセッションを対応付けます。nonce は ID Token と認証リクエストを結び付けます。PKCE は認可コードの横取りに対抗し、コードと開始者を結び付けます。

**ID / Access / Refresh Token の違いは？**  
ID Token はクライアント向けの認証結果、Access Token は API 向けの認可情報、Refresh Token は新しい Access Token の取得に使う機密情報です。

**iss、sub、aud は何ですか？**  
iss は発行者、sub は発行者の範囲内の主体識別子、aud は想定する受信者です。主体の識別には iss と sub の組み合わせを使います。sub は必ず UUID とは限りません。

**JWT 検証では何を確認しますか？**  
信頼できる鍵による署名、issuer、audience、exp と nbf、必要なトークン種別を確認します。単にデコードできるだけでは信用できません。JWKS の鍵更新にも対応します。

**scope と role と所有権の違いは？**  
scope は操作範囲、role は役割です。所有権の確認では対象オブジェクトを現在の主体が操作できるかを判断します。この実装では管理者も必要な scope を持つ必要があります。

**401 と 403 の違いは？**  
401 は認証情報がない、または無効です。403 は通常、認証後に必要な権限が不足している場合です。CSRF 検証失敗でも 403 になります。

**Refresh と Logout の注意点は？**  
Refresh Token はサーバー側に保持し、ローテーションを行います。ログアウトではローカルセッションと IdP セッションを終了しますが、発行済み JWT は直ちに無効になるとは限りません。

**Client Credentials は誰の認証ですか？**  
人間ではなく機械クライアントの認証です。専用サービスアカウントを使い、必要最小限の audience、scope、role を付与します。

**この環境を本番に使えますか？**  
学習用です。本番には HTTPS、秘密情報管理、運用可能なセッションとトークン保管、更新サポート、監査と監視などを別途設計する必要があります。
