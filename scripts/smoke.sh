#!/bin/sh
set -eu
web_origin=${WEB_ORIGIN:-http://localhost:8080}
api_origin=${API_BASE_URL:-http://localhost:8082}
issuer=${ISSUER_URI:-http://localhost:8081/realms/auth-lab}
expect_status() {
    actual=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' --max-time 10 "$2")
    test "$actual" = "$1" || { echo "FAIL expected $1 got $actual" >&2; exit 1; }
    echo "PASS HTTP $actual"
}
expect_status 200 "$web_origin/actuator/health"
expect_status 200 "$api_origin/actuator/health"
expect_status 200 "$issuer/.well-known/openid-configuration"
expect_status 200 "$api_origin/api/public"
expect_status 401 "$api_origin/api/me"
