#/bin/bash

current_hour=$(date +%H)
current_minute=$(date +%M)

# 检查是否在9:30之前 (小时 < 9 或 (小时 == 9 且 分钟 < 30))
is_before_930=false
if [ "$current_hour" -lt 9 ] || { [ "$current_hour" -eq 9 ] && [ "$current_minute" -lt 30 ]; }; then
    is_before_930=true
fi

if [[ ! "$CNB_EVENT" =~ ^api_trigger ]] || [ "$is_before_930" = true ]; then
    curl -X 'POST' \
        "https://api.cnb.cool/${CNB_REPO_SLUG_LOWERCASE}/-/build/start" \
        -H 'accept: application/json' \
        -H "Authorization: ${CNB_TOKEN}" \
        -H 'Content-Type: application/json' \
        -d "{
                \"env\": {},
                \"event\": \"api_trigger_daily\",
                \"branch\": \"main\",
                \"sync\": false
            }"
fi