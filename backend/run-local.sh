#!/usr/bin/env bash
# 本地开发启动脚本：自动读取同目录下的 .env（每人自己建一份，不进 git，
# 见 .env.example）再起 Spring Boot，省得每次手动 export DB_PASSWORD。
#
# 首次使用：
#   cp backend/.env.example backend/.env
#   # 把 DB_PASSWORD 换成你自己本机 MySQL 的密码
#   ./backend/run-local.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
else
  echo "提示：没找到 backend/.env，先 cp .env.example .env 再按自己本机情况填。" >&2
fi

exec mvn -o spring-boot:run
