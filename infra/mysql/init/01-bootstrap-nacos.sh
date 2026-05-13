#!/bin/bash
# MySQL 容器首次启动钩子，由官方镜像的 docker-entrypoint.sh 在 datadir 为空时执行。
#
# 作用：
#   1. 建立 Nacos 配置中心使用的独立库 nacos_config（utf8mb4）；
#   2. 创建专用账号 nacos / nacos，仅授予 nacos_config 库权限；
#   3. 导入官方 mysql-schema.sql（在镜像内挂载到 /docker-entrypoint-initdb.d/02-nacos-schema.sql）。
#
# 注意：业务库 crediflow 由 MYSQL_DATABASE 环境变量自动创建，不在此处处理。
set -euo pipefail

NACOS_DB="${NACOS_DB:-nacos_config}"
NACOS_USER="${NACOS_USER:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${NACOS_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${NACOS_USER}'@'%' IDENTIFIED BY '${NACOS_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${NACOS_DB}\`.* TO '${NACOS_USER}'@'%';
FLUSH PRIVILEGES;
SQL

echo "[init] nacos_config database & user prepared, importing Nacos schema..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${NACOS_DB}" < /docker-entrypoint-initdb.d/02-nacos-schema.sql
echo "[init] Nacos schema import complete."
