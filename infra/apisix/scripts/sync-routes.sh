#!/bin/sh
# 将 conf.d 中声明式资源幂等写入 APISIX Admin API（本地 docker compose 使用）。
# 依赖：与 infra/apisix/config.yaml 中 deployment.admin.admin_key 一致。

set -eu

APISIX_ADMIN="${APISIX_ADMIN:-http://apisix:9180}"
ADMIN_KEY="${ADMIN_KEY:-crediflow-local-apisix-admin-key-demo}"

wait_admin() {
  i=0
  while [ "$i" -lt 60 ]; do
    code=$(curl -sS -o /dev/null -w "%{http_code}" -H "X-API-KEY: ${ADMIN_KEY}" "${APISIX_ADMIN}/apisix/admin/routes" || echo "000")
    if [ "$code" = "200" ]; then
      return 0
    fi
    echo "[apisix-init] waiting for Admin API (${code})..."
    sleep 2
    i=$((i + 1))
  done
  echo "[apisix-init] Admin API not ready after timeout"
  exit 1
}

put_json() {
  rel_path="$1"
  file="$2"
  echo "[apisix-init] PUT ${rel_path} <- ${file}"
  code=$(curl -sS -o /tmp/apisix-put-body.txt -w "%{http_code}" \
    -X PUT "${APISIX_ADMIN}/apisix/admin/${rel_path}" \
    -H "X-API-KEY: ${ADMIN_KEY}" \
    -H "Content-Type: application/json" \
    --data-binary "@${file}")
  if [ "$code" -ge 400 ]; then
    echo "[apisix-init] PUT failed HTTP ${code}"
    cat /tmp/apisix-put-body.txt
    exit 1
  fi
}

wait_admin

# 顺序：Consumer 先于依赖 jwt-auth 的路由
put_json "consumers/crediflow-jwt-demo" "/conf.d/consumer-crediflow-jwt-demo.json"
put_json "routes/local-app-user-register" "/conf.d/route-app-user-register.json"
put_json "routes/local-app-user-login" "/conf.d/route-app-user-login.json"
put_json "routes/local-app-user-auth" "/conf.d/route-app-user-auth.json"
put_json "routes/local-app-catchall" "/conf.d/route-app-catchall.json"
put_json "routes/local-admin-catchall" "/conf.d/route-admin-catchall.json"

echo "[apisix-init] sync completed OK"
