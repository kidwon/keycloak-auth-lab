#!/usr/bin/env python3
"""只使用演示用户创建独立会话；凭据、授权码和令牌仅存在于进程内存。"""
import base64
import hashlib
import http.cookiejar
import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser

WEB = os.getenv("WEB_ORIGIN", "http://localhost:8080")
ISSUER = os.getenv("ISSUER_URI", "http://localhost:8081/realms/auth-lab")
API = os.getenv("API_BASE_URL", "http://localhost:8082")
CALLBACK = WEB + "/login/oauth2/code/keycloak"
TOKEN = ISSUER + "/protocol/openid-connect/token"


class Form(HTMLParser):
    def __init__(self, html, form_id):
        super().__init__()
        self.form_id = form_id
        self.active = False
        self.action = None
        self.fields = {}
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "form":
            self.active = values.get("id") == self.form_id
            if self.active:
                self.action = values.get("action")
        if self.active and tag == "input" and values.get("name"):
            self.fields[values["name"]] = values.get("value", "")

    def handle_endtag(self, tag):
        if tag == "form":
            self.active = False


class Redirects(urllib.request.HTTPRedirectHandler):
    def __init__(self, capture_callback=False):
        self.capture_callback = capture_callback
        self.callback = None
        self.locations = []

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        self.locations.append(newurl)
        if self.capture_callback and newurl.startswith(CALLBACK + "?"):
            self.callback = newurl
            return None
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class Browser:
    def __init__(self, capture_callback=False):
        self.redirects = Redirects(capture_callback)
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), self.redirects)

    def request(self, url, form=None, headers=None, method=None):
        data = urllib.parse.urlencode(form).encode() if form is not None else None
        request = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
        try:
            with self.opener.open(request, timeout=20) as response:
                return response.status, response.read().decode(), response.geturl()
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode(), error.geturl()

    def login(self, page, username, password):
        form = Form(page, "kc-form-login")
        check(form.action is not None, "Keycloak 演示登录表单存在")
        form.fields.update(username=username, password=password)
        return self.request(form.action, form.fields)


def check(condition, label):
    if not condition:
        raise RuntimeError(label)
    print("PASS " + label)


def basic(client, secret):
    return {"Authorization": "Basic " + base64.b64encode((client + ":" + secret).encode()).decode()}


def authorize(username, password, bad_verifier=False):
    browser = Browser(capture_callback=True)
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    state = secrets.token_urlsafe(32)
    query = urllib.parse.urlencode(dict(client_id="web-client", redirect_uri=CALLBACK,
        response_type="code", scope="openid profile orders:read orders:write", state=state,
        nonce=secrets.token_urlsafe(32), code_challenge=challenge, code_challenge_method="S256"))
    status, page, _ = browser.request(ISSUER + "/protocol/openid-connect/auth?" + query)
    check(status == 200, "独立 PKCE 测试会话开始")
    browser.login(page, username, password)
    callback = browser.redirects.callback
    check(callback is not None, "收到独立测试授权回调")
    params = urllib.parse.parse_qs(urllib.parse.urlsplit(callback).query)
    check(params.get("state") == [state] and "code" in params, "授权响应 state 匹配")
    status, response, _ = Browser().request(TOKEN, dict(grant_type="authorization_code",
        code=params["code"][0], redirect_uri=CALLBACK,
        code_verifier=secrets.token_urlsafe(48) if bad_verifier else verifier),
        basic("web-client", os.environ["WEB_CLIENT_SECRET"]))
    if bad_verifier:
        check(status == 400 and json.loads(response).get("error") == "invalid_grant", "错误 PKCE verifier 被真实 Keycloak 拒绝")
        return None
    check(status == 200, "正确 S256 PKCE 换码成功")
    return json.loads(response)


def api(path, token=None, method="GET"):
    headers = {"Authorization": "Bearer " + token} if token else {}
    return Browser().request(API + path, headers=headers, method=method)[0]


def main():
    for name in ("ALICE_PASSWORD", "ADMIN_PASSWORD", "WEB_CLIENT_SECRET", "SERVICE_CLIENT_SECRET"):
        if not os.getenv(name):
            raise RuntimeError("缺少环境变量 " + name + "，请按 README 加载 .env")
    check(api("/api/public") == 200, "公开 API 200")
    check(api("/api/me") == 401, "缺失令牌 401")
    authorize("alice", os.environ["ALICE_PASSWORD"], bad_verifier=True)
    alice = authorize("alice", os.environ["ALICE_PASSWORD"])
    token = alice["access_token"]
    check(api("/api/me", token) == 200, "Access Token 调用成功")
    check(api("/api/me", alice["id_token"]) == 401, "ID Token 不能调用 API")
    check(api("/api/orders/1", token) == 200, "Alice 可读自己的订单")
    check(api("/api/orders/2", token) == 403, "Alice 不可读其他人的订单")
    check(api("/api/orders", token, "POST") == 201, "orders:write 可创建订单")
    check(api("/api/admin", token) == 403, "普通用户不可访问 ADMIN 端点")
    check(api("/api/internal/status", token) == 403, "用户令牌不可调用机器端点")
    admin = authorize("admin", os.environ["ADMIN_PASSWORD"])
    check(api("/api/admin", admin["access_token"]) == 200, "Admin 可访问 ADMIN 端点")
    check(api("/api/orders/1", admin["access_token"]) == 200, "Admin 可访问其他人的订单")
    status, response, _ = Browser().request(TOKEN, dict(grant_type="refresh_token", refresh_token=alice["refresh_token"]),
        basic("web-client", os.environ["WEB_CLIENT_SECRET"]))
    check(status == 200 and "access_token" in json.loads(response), "真实 refresh_token grant 成功")
    status, _, _ = Browser().request(TOKEN, dict(grant_type="client_credentials"), basic("service-client", "wrong-fixture"))
    check(status == 401, "错误机器凭据被拒绝")
    status, response, _ = Browser().request(TOKEN, dict(grant_type="client_credentials", scope="service:invoke"),
        basic("service-client", os.environ["SERVICE_CLIENT_SECRET"]))
    check(status == 200, "机器客户端取得令牌")
    check(api("/api/internal/status", json.loads(response)["access_token"]) == 200, "机器令牌调用成功")
    browser = Browser()
    status, page, _ = browser.request(WEB + "/oauth2/authorization/keycloak")
    check(status == 200, "网页开始真实 OIDC 登录")
    auth_urls = [url for url in browser.redirects.locations if "/protocol/openid-connect/auth?" in url]
    auth_params = urllib.parse.parse_qs(urllib.parse.urlsplit(auth_urls[0]).query)
    check(auth_params.get("code_challenge_method") == ["S256"] and "state" in auth_params, "网页请求实际带 S256 PKCE 和 state")
    status, page, url = browser.login(page, "alice", os.environ["ALICE_PASSWORD"])
    check(status == 200 and url.endswith("/learn") and "ID Token" in page, "网页服务端换码并渲染中文学习页")
    check(all(value not in page for value in (token, alice["refresh_token"], os.environ["WEB_CLIENT_SECRET"])), "页面不含测试令牌或客户端密钥")
    # 页面中唯一的最后一个表单是 Thymeleaf 自动注入 CSRF 的注销表单。
    class LogoutForm(HTMLParser):
        def __init__(self):
            super().__init__(); self.active = False; self.fields = {}
        def handle_starttag(self, tag, attrs):
            values = dict(attrs)
            if tag == "form": self.active = values.get("action") == "/logout"
            if tag == "input" and self.active and values.get("name"):
                self.fields[values["name"]] = values.get("value", "")
        def handle_endtag(self, tag):
            if tag == "form": self.active = False
    form = LogoutForm(); form.feed(page)
    check("_csrf" in form.fields, "注销表单包含 CSRF")
    status, _, url = browser.request(WEB + "/logout", form.fields)
    check(status == 200 and url.rstrip("/") == WEB, "RP 发起注销返回首页")
    before = len(browser.redirects.locations)
    browser.request(WEB + "/learn")
    check(any("/protocol/openid-connect/auth?" in url for url in browser.redirects.locations[before:])
          or any("/login" in url for url in browser.redirects.locations[before:]), "注销后受保护页面要求重新认证")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # 不输出 HTTP 异常、URL、响应体或 traceback，避免泄露凭据。
        label = str(error) if isinstance(error, RuntimeError) else type(error).__name__
        print("FAIL " + label, file=sys.stderr)
        sys.exit(1)
