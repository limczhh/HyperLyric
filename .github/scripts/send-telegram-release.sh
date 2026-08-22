#!/usr/bin/env bash

set -euo pipefail

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN is required}"
: "${TELEGRAM_CHAT_ID:?TELEGRAM_CHAT_ID is required}"
: "${RELEASE_TAG:?RELEASE_TAG is required}"
: "${RELEASE_URL:?RELEASE_URL is required}"
: "${RELEASE_NOTES_ZH_FILE:?RELEASE_NOTES_ZH_FILE is required}"

ONLINE_APK="${ONLINE_APK:-}"
OFFLINE_APK="${OFFLINE_APK:-}"
PLUGIN_FILES="${PLUGIN_FILES:-}"

if [[ -n "$ONLINE_APK" || -n "$OFFLINE_APK" ]]; then
  : "${ONLINE_APK:?ONLINE_APK is required when sending APKs}"
  : "${OFFLINE_APK:?OFFLINE_APK is required when sending APKs}"
fi

declare -a FILES=()
if [[ -n "$ONLINE_APK" ]]; then
  FILES+=("$ONLINE_APK" "$OFFLINE_APK")
fi

if [[ -n "$PLUGIN_FILES" ]]; then
  while IFS= read -r plugin_file; do
    [[ -z "$plugin_file" ]] && continue
    FILES+=("$plugin_file")
  done <<< "$PLUGIN_FILES"
fi

if (( ${#FILES[@]} == 0 )); then
  echo '::error::没有可发送的 APK 或插件文件'
  exit 1
fi

if (( ${#FILES[@]} > 10 )); then
  echo '::error::Telegram 单个媒体组最多发送 10 个文件，请拆分发布附件'
  exit 1
fi

for file in "${FILES[@]}" "$RELEASE_NOTES_ZH_FILE"; do
  if [[ ! -f "$file" ]]; then
    echo "::error::文件不存在: $file"
    exit 1
  fi
done

API_BASE="https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}"

telegram_request() {
  local method="$1"
  shift
  local response

  response="$(curl --fail-with-body -sS -X POST "$API_BASE/$method" "$@")"
  if ! jq -e '.ok == true' >/dev/null <<<"$response"; then
    echo "::error::Telegram API $method 失败: $response"
    exit 1
  fi
}

RELEASE_NOTES_ZH="$(<"$RELEASE_NOTES_ZH_FILE")"
ANNOUNCEMENT_HEADER="${ANNOUNCEMENT_HEADER:-}"
ANNOUNCEMENT_LINK_LABEL="${ANNOUNCEMENT_LINK_LABEL:-下载地址}"
ANNOUNCEMENT_NOTES_LABEL="${ANNOUNCEMENT_NOTES_LABEL:-更新日志}"
ANNOUNCEMENT_PLAIN_BODY="${ANNOUNCEMENT_PLAIN_BODY:-false}"

if [[ "$ANNOUNCEMENT_PLAIN_BODY" == "true" && -n "$ANNOUNCEMENT_HEADER" ]]; then
  CAPTION="$(printf '%s\n\n%s' "$ANNOUNCEMENT_HEADER" "$RELEASE_NOTES_ZH")"
elif [[ "$ANNOUNCEMENT_PLAIN_BODY" == "true" ]]; then
  CAPTION="$RELEASE_NOTES_ZH"
elif [[ -n "$ANNOUNCEMENT_HEADER" ]]; then
  CAPTION="$(printf '%s\n\n%s：%s\n\n%s：\n%s' \
    "$ANNOUNCEMENT_HEADER" "$ANNOUNCEMENT_LINK_LABEL" "$RELEASE_URL" \
    "$ANNOUNCEMENT_NOTES_LABEL" "$RELEASE_NOTES_ZH")"
else
  CAPTION="$(printf '%s：%s\n\n%s：\n%s' \
    "$ANNOUNCEMENT_LINK_LABEL" "$RELEASE_URL" \
    "$ANNOUNCEMENT_NOTES_LABEL" "$RELEASE_NOTES_ZH")"
fi

if (( ${#CAPTION} > 1024 )); then
  echo "::error::中文更新日志过长，Telegram 媒体 Caption 超过 1024 个字符；请精简更新日志"
  exit 1
fi

if (( ${#FILES[@]} == 1 )); then
  telegram_request sendDocument \
    --form-string "chat_id=$TELEGRAM_CHAT_ID" \
    --form-string "caption=$CAPTION" \
    -F "document=@${FILES[0]}"
else
  MEDIA_JSON='[]'
  declare -a FORM_ARGS=()

  for index in "${!FILES[@]}"; do
    ATTACH_NAME="file${index}"
    if (( index == 0 )); then
      MEDIA_JSON="$(jq -c \
        --arg media "attach://$ATTACH_NAME" \
        --arg caption "$CAPTION" \
        '. + [{"type":"document","media":$media,"caption":$caption}]' \
        <<< "$MEDIA_JSON")"
    else
      MEDIA_JSON="$(jq -c \
        --arg media "attach://$ATTACH_NAME" \
        '. + [{"type":"document","media":$media}]' \
        <<< "$MEDIA_JSON")"
    fi
    FORM_ARGS+=(-F "${ATTACH_NAME}=@${FILES[$index]}")
  done

  telegram_request sendMediaGroup \
    --form-string "chat_id=$TELEGRAM_CHAT_ID" \
    --form-string "media=$MEDIA_JSON" \
    "${FORM_ARGS[@]}"
fi

echo "Telegram release announcement sent for $RELEASE_TAG (${#FILES[@]} file(s))"
