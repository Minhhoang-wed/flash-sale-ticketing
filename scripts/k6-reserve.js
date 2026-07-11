// Ngày 8 — k6 load test: ramp 200 -> 1000 VU bắn /reserve
//
// Chạy (Docker, không cần cài k6):
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run - < scripts/k6-reserve.js
// Hoặc cài k6 (winget install k6.k6) rồi:
//   k6 run scripts/k6-reserve.js
//
// So sánh 2 cấu hình (điểm nhấn CV):
//   1) docker compose down; $env:RESERVE_STRATEGY="pessimistic"; docker compose up -d  -> chạy k6, ghi số
//   2) docker compose down; Remove-Item Env:RESERVE_STRATEGY; docker compose up -d     -> chạy k6, ghi số

import http from 'k6/http';
import { Counter } from 'k6/metrics';

const reservedOk = new Counter('reserved_ok');     // 201/202
const soldOut = new Counter('sold_out_409');
const rateLimited = new Counter('rate_limited_429');

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    stages: [
        { duration: '10s', target: 200 },   // ramp-up
        { duration: '30s', target: 1000 },  // đỉnh tải
        { duration: '10s', target: 0 },     // hạ nhiệt
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],     // lỗi mạng/5xx < 1% (409/429 KHÔNG tính là fail)
        http_req_duration: ['p(95)<500'],   // p95 dưới 500ms
    },
};

export function setup() {
    http.post(`${BASE}/api/debug/reset`);
}

export default function () {
    // Mỗi request 1 user khác nhau (rule 1 vé/người + rate limit theo user)
    const userId = `k6-${__VU}-${__ITER}`;
    const res = http.post(`${BASE}/api/events/1/reserve`, null, {
        headers: { 'X-User-Id': userId },
        tags: { name: 'reserve' },
    });
    if (res.status === 201 || res.status === 202) reservedOk.add(1);
    else if (res.status === 409) soldOut.add(1);
    else if (res.status === 429) rateLimited.add(1);
}

export function teardown() {
    const metrics = http.get(`${BASE}/api/debug/metrics`);
    console.log(`\n===== Backend metrics sau test =====\n${metrics.body}`);
}
