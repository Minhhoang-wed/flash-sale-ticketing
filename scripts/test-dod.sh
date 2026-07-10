#!/usr/bin/env bash
# DoD Ngày 1: đặt vé tuần tự 101 lần → lần 101 phải bị từ chối (409)
BASE_URL="${BASE_URL:-http://localhost:8080}"
EVENT_ID="${EVENT_ID:-1}"

echo "== Event trước khi test =="
curl -s "$BASE_URL/api/events/$EVENT_ID"

ok=0; rejected=0
for i in $(seq 1 101); do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "X-User-Id: user-$i" \
    "$BASE_URL/api/events/$EVENT_ID/reserve")
  if [ "$code" = "201" ]; then ok=$((ok+1)); else rejected=$((rejected+1)); fi
  echo "Request $i -> HTTP $code"
done

echo ""
echo "== Kết quả: $ok thành công, $rejected bị từ chối =="
echo "== Event sau khi test =="
curl -s "$BASE_URL/api/events/$EVENT_ID"

if [ "$ok" = "100" ] && [ "$rejected" = "1" ]; then
  echo "✅ DoD PASSED: đúng 100 vé bán ra, request 101 bị từ chối"
else
  echo "❌ DoD FAILED"
fi
