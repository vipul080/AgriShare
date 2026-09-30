#!/bin/sh
# Nightly database backup, keeps the last 14 days.
# Install (on the server):  crontab -e   and add
#   30 2 * * * /home/ubuntu/AgriShare/deploy/backup.sh >> /home/ubuntu/backup.log 2>&1
set -eu
cd "$(dirname "$0")/.."
mkdir -p backups
file="backups/agrishare-$(date +%Y-%m-%d).sql.gz"
docker compose -f docker-compose.prod.yml --env-file .env exec -T db \
    pg_dump -U agrishare --no-owner agrishare | gzip > "$file"
find backups -name 'agrishare-*.sql.gz' -mtime +14 -delete
echo "$(date) backup written: $file ($(du -h "$file" | cut -f1))"
