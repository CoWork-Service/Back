#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

ENV_FILE="$BACKEND_DIR/.env"
COMPOSE_DB_FILE="$BACKEND_DIR/docker-compose.yml"
COMPOSE_APP_FILE="$BACKEND_DIR/docker-compose.app.yml"

ASSUME_YES=false
RESET_DB=true
RESET_S3=true
STOP_APP=false
S3_PREFIX_OVERRIDE=""

usage() {
  cat <<'EOF'
Usage: scripts/reset-db-and-s3.sh [options]

Drops and recreates the MySQL database, then deletes uploaded files from S3.
Configuration is loaded from Backend/.env by default.

Options:
  --yes                 Skip interactive confirmation.
  --env-file PATH       Load environment variables from PATH.
  --skip-db             Do not reset MySQL.
  --skip-s3             Do not delete S3 objects.
  --s3-prefix PREFIX    Override AWS_S3_KEY_PREFIX for this run.
  --stop-app            Stop the app compose service before resetting.
  -h, --help            Show this help.

Examples:
  scripts/reset-db-and-s3.sh
  scripts/reset-db-and-s3.sh --yes --stop-app
  scripts/reset-db-and-s3.sh --skip-db --s3-prefix staging/uploads
EOF
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

safe_identifier() {
  local name="$1"
  local value="$2"
  [[ "$value" =~ ^[A-Za-z0-9_]+$ ]] || die "$name must contain only letters, numbers, and underscores: $value"
}

sql_string() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "'%s'" "$value"
}

normalize_prefix() {
  local prefix="${1:-}"
  prefix="${prefix#/}"
  prefix="${prefix%/}"
  if [[ -n "$prefix" ]]; then
    printf '%s/' "$prefix"
  fi
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --yes)
        ASSUME_YES=true
        shift
        ;;
      --env-file)
        [[ $# -ge 2 ]] || die "--env-file requires a path"
        ENV_FILE="$2"
        shift 2
        ;;
      --skip-db)
        RESET_DB=false
        shift
        ;;
      --skip-s3)
        RESET_S3=false
        shift
        ;;
      --s3-prefix)
        [[ $# -ge 2 ]] || die "--s3-prefix requires a value"
        S3_PREFIX_OVERRIDE="$2"
        shift 2
        ;;
      --stop-app)
        STOP_APP=true
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        die "Unknown option: $1"
        ;;
    esac
  done
}

load_env() {
  [[ -f "$ENV_FILE" ]] || die "Env file not found: $ENV_FILE"

  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  MYSQL_USERNAME="${MYSQL_USERNAME:-${MYSQL_USER:-}}"
  MYSQL_DATABASE="${MYSQL_DATABASE:-}"
  MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
  MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"

  AWS_REGION="${AWS_REGION:-ap-northeast-2}"
  AWS_DEFAULT_REGION="$AWS_REGION"
  AWS_S3_BUCKET="${AWS_S3_BUCKET:-}"
  AWS_S3_KEY_PREFIX="${S3_PREFIX_OVERRIDE:-${AWS_S3_KEY_PREFIX:-}}"

  export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-}"
  export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-}"
  export AWS_SESSION_TOKEN="${AWS_SESSION_TOKEN:-}"
  export AWS_REGION
  export AWS_DEFAULT_REGION
}

confirm_targets() {
  local db_target="skipped"
  local s3_target="skipped"
  local s3_prefix

  if [[ "$RESET_DB" == true ]]; then
    db_target="${MYSQL_DATABASE:-<empty>} on compose service mysql"
  fi

  if [[ "$RESET_S3" == true ]]; then
    s3_prefix="$(normalize_prefix "$AWS_S3_KEY_PREFIX")"
    if [[ -n "${AWS_S3_BUCKET:-}" ]]; then
      s3_target="s3://$AWS_S3_BUCKET/${s3_prefix}"
    else
      s3_target="<AWS_S3_BUCKET is empty>"
    fi
  fi

  cat <<EOF
This will permanently reset:
  DB: $db_target
  S3: $s3_target

Env file: $ENV_FILE
EOF

  if [[ "$ASSUME_YES" == true ]]; then
    return 0
  fi

  printf 'Type RESET to continue: '
  local answer
  if ! read -r answer; then
    die "Confirmation failed. Re-run with --yes for non-interactive use."
  fi
  [[ "$answer" == "RESET" ]] || die "Aborted."
}

stop_app() {
  [[ "$STOP_APP" == true ]] || return 0
  [[ -f "$COMPOSE_APP_FILE" ]] || return 0

  echo "Stopping app service..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_DB_FILE" -f "$COMPOSE_APP_FILE" stop app || true
}

reset_mysql() {
  [[ "$RESET_DB" == true ]] || return 0

  require_cmd docker
  [[ -f "$COMPOSE_DB_FILE" ]] || die "Compose file not found: $COMPOSE_DB_FILE"
  [[ -n "$MYSQL_DATABASE" ]] || die "MYSQL_DATABASE is required"
  [[ -n "$MYSQL_USERNAME" ]] || die "MYSQL_USERNAME or MYSQL_USER is required"
  [[ -n "$MYSQL_PASSWORD" ]] || die "MYSQL_PASSWORD is required"
  [[ -n "$MYSQL_ROOT_PASSWORD" ]] || die "MYSQL_ROOT_PASSWORD is required"

  safe_identifier MYSQL_DATABASE "$MYSQL_DATABASE"
  safe_identifier MYSQL_USERNAME "$MYSQL_USERNAME"

  local compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_DB_FILE")
  local user_password
  user_password="$(sql_string "$MYSQL_PASSWORD")"

  echo "Starting mysql service if needed..."
  "${compose[@]}" up -d mysql

  echo "Waiting for mysql..."
  "${compose[@]}" exec -T mysql sh -c 'for i in $(seq 1 60); do mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent && exit 0; sleep 1; done; exit 1'

  echo "Dropping and recreating database: $MYSQL_DATABASE"
  "${compose[@]}" exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql mysql -uroot <<SQL
DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`;
CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$MYSQL_USERNAME'@'%' IDENTIFIED BY $user_password;
ALTER USER '$MYSQL_USERNAME'@'%' IDENTIFIED BY $user_password;
GRANT ALL PRIVILEGES ON \`$MYSQL_DATABASE\`.* TO '$MYSQL_USERNAME'@'%';
FLUSH PRIVILEGES;
SQL
}

reset_s3() {
  [[ "$RESET_S3" == true ]] || return 0

  [[ -n "$AWS_S3_BUCKET" ]] || die "AWS_S3_BUCKET is required"
  [[ -n "$AWS_ACCESS_KEY_ID" ]] || die "AWS_ACCESS_KEY_ID is required"
  [[ -n "$AWS_SECRET_ACCESS_KEY" ]] || die "AWS_SECRET_ACCESS_KEY is required"

  local s3_prefix
  s3_prefix="$(normalize_prefix "$AWS_S3_KEY_PREFIX")"
  local target="s3://$AWS_S3_BUCKET/$s3_prefix"

  echo "Deleting S3 objects under: $target"
  if command -v aws >/dev/null 2>&1; then
    aws s3 rm "$target" --recursive --region "$AWS_REGION"
    return
  fi

  require_cmd docker
  docker run --rm \
    -e AWS_ACCESS_KEY_ID \
    -e AWS_SECRET_ACCESS_KEY \
    -e AWS_SESSION_TOKEN \
    -e AWS_DEFAULT_REGION \
    amazon/aws-cli s3 rm "$target" --recursive --region "$AWS_REGION"
}

main() {
  parse_args "$@"
  load_env
  confirm_targets
  stop_app
  reset_mysql
  reset_s3
  echo "Reset complete."
}

main "$@"
