#!/usr/bin/env bash
# 每日逻辑备份：mysqldump 单事务一致性快照 + gzip，保留最近 N 天。
# 用法（crontab 示例，每天 03:30）：
#   30 3 * * * DB_PASSWORD=*** /opt/mathematics/scripts/backup-mysql.sh >> /var/log/mathematics-backup.log 2>&1
# 恢复：gunzip -c mathematics-20261005-033000.sql.gz | mysql -h HOST -u USER -p mathematics
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-mathematics}"
DB_USER="${DB_USER:-mathematics}"
: "${DB_PASSWORD:?需要设置 DB_PASSWORD}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/mathematics}"
KEEP_DAYS="${KEEP_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
target="$BACKUP_DIR/${DB_NAME}-$(date +%Y%m%d-%H%M%S).sql.gz"
tmp="$target.partial"

# 密码走环境变量而不是 -p 参数，免得出现在 ps 里
MYSQL_PWD="$DB_PASSWORD" mysqldump \
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
  --single-transaction --quick --routines --triggers --no-tablespaces \
  --default-character-set=utf8mb4 --set-gtid-purged=OFF \
  "$DB_NAME" | gzip -9 > "$tmp"

# 半截文件不能冒充一份完整备份
gzip -t "$tmp"
mv "$tmp" "$target"
echo "$(date '+%F %T') backup ok: $target ($(du -h "$target" | cut -f1))"

find "$BACKUP_DIR" -name "${DB_NAME}-*.sql.gz" -mtime +"$KEEP_DAYS" -delete
