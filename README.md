# 🎟 Flash-Sale Ticketing

[![CI](https://github.com/Minhhoang-wed/flash-sale-ticketing/actions/workflows/ci.yml/badge.svg)](https://github.com/Minhhoang-wed/flash-sale-ticketing/actions/workflows/ci.yml)

> **Bài toán:** 100 vé concert mở bán lúc 20:00. 5.000 người cùng bấm "Mua" trong 3 giây đầu.
> Hệ thống phải bán **đúng 100 vé** — không bán lố một vé nào, không sập, và trả lời từng người trong vài chục ms.
>
> Project này xây hệ thống đó từ con số 0, **cố tình tạo ra lỗi oversell trước** (có bằng chứng), rồi diệt nó qua từng lớp: DB lock → Redis atomic → Lua script → message queue idempotent.

**Stack:** Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Redis 7 · RabbitMQ · Testcontainers · k6 · React + Vite · Docker Compose

---

## 🚀 Chạy bằng 1 lệnh

```bash
docker compose up --build
```

- Swagger UI: http://localhost:8080/swagger-ui.html (app tự seed 1 event 100 vé)
- RabbitMQ UI: http://localhost:15672 (guest/guest)
- Frontend demo: `cd frontend && npm install && npm run dev` → http://localhost:5173

Load test nhanh (JDK 11+): `java scripts/LoadTest.java` — 200 user đồng thời tranh 100 vé.

![Demo](docs/demo.gif)

---

## 🏗 Kiến trúc

```
                       ┌─────────────────────────────────────────────┐
 5000 request          │                    App                      │
──────/reserve──▶ Rate limiter ──▶ Lua script trên Redis             │
                       │       (check 1 vé/người + DECR stock        │
                       │        + SADD purchased — ATOMIC cả khối)   │
                       │           │                                 │
                       │     còn vé│         hết vé → 409 (DB        │
                       │           ▼          KHÔNG bị chạm)         │
                       │   publish RabbitMQ ──▶ reservation.queue    │
                       │           │                  │              │
                       │      202 + reservationId     ▼              │
                       │                        Consumer (idempotent)│
                       │                              │ INSERT       │
                       └──────────────────────────────┼──────────────┘
                                                      ▼
        Client ──poll /orders/by-reservation/{id}──▶ PostgreSQL (sổ cái)

 Vòng đời vé:  RESERVED ──pay trước hạn──▶ PAID
                   └──quá hạn (job 30s)──▶ EXPIRED ──trả vé về kho Redis──▶ bán tiếp
```

Nguyên tắc phân vai: **Redis là người gác cửa** (chặn số đông trong micro-giây), **RabbitMQ là băng chuyền** (tách pha ghi nặng ra nền), **PostgreSQL là sổ cái** (nguồn sự thật cuối cùng — kho Redis dựng lại từ DB khi restart).

---

## 🛡 Ba lớp chống oversell

| Lớp | Cơ chế | Chống lại gì |
|---|---|---|
| **1. Redis atomic** | Lua script: check-đã-mua + check-stock + DECR + SADD chạy như MỘT lệnh (Redis single-threaded). Mỗi request nhận một con số duy nhất — ai nhận số âm chắc chắn trượt. | Race condition giữa hàng nghìn request đồng thời — khe hở check-then-act bị đóng hoàn toàn ở tầng đầu tiên. |
| **2. DB conditional update** | Mọi chuyển trạng thái đơn là `UPDATE ... WHERE status='RESERVED' [AND chưa quá hạn]` — check-and-update trong 1 câu SQL, row lock cho đúng một bên thắng. | Race pay-vs-expire: không tồn tại đơn vừa PAID vừa EXPIRED, không hoàn kho vé đã bán. |
| **3. Idempotent consumer** | RabbitMQ giao at-least-once → có thể giao trùng. UNIQUE constraint trên `orders.reservation_id` là chốt chặn cuối — message trùng bị bỏ qua êm. | Message duplicate không tạo đơn trùng, kể cả nhiều consumer chạy song song. |

Ngoài ra: rate limit 5 req/s/user (429), retry 3 lần + DLQ cho message độc, compensation `INCR` khi publish thất bại, TTL giữ chỗ 10 phút + job trả vé.

---

## 📊 Bằng chứng

### Before/After: tự tạo oversell rồi diệt nó

Bản naive (read-check-write): 200 request đồng thời → bán lố vé.
Sau khi sửa: đúng 100 vé, chạy 3 lần liên tiếp đều 0 oversell.

| Before (naive) | After (pessimistic lock) |
|---|---|
| ![Before](docs/before-naive.png) | ![After](docs/after-pessimistic.png) |

### Số liệu k6 (ramp 200 → 1000 VU, 50s)

| Cấu hình | Throughput | p95 | Vé bán | Oversell |
|---|---|---|---|---|
| DB pessimistic lock | _điền số_ req/s | _điền số_ ms | 100 | **0** |
| Redis + Lua + RabbitMQ | _điền số_ req/s | _điền số_ ms | 100 | **0** |

> Cả hai cấu hình đều ĐÚNG — cái Redis mua được là tốc độ, không phải tính đúng.
> Đổi chiến lược qua env: `RESERVE_STRATEGY=naive | pessimistic | optimistic | redis`

---

## 🔌 API

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/events/{id}` | Event + vé còn lại (cache-aside, stock live từ Redis) |
| POST | `/api/events/{id}/reserve` | Giữ chỗ (header `X-User-Id`) → 202 + reservationId |
| GET | `/api/orders/by-reservation/{id}` | Poll trạng thái đơn (404 = đang xử lý) |
| POST | `/api/orders/{id}/pay` | RESERVED → PAID (fail nếu quá hạn giữ chỗ) |
| POST | `/api/debug/reset` | Reset demo (kho, đơn, queue, metrics) |
| GET | `/api/debug/metrics` | Chiến lược, tổng đơn, optimistic retries, stock, DLQ |

## 🧪 Test

```bash
./mvnw verify        # unit + integration (Testcontainers: Postgres/Redis/RabbitMQ THẬT, chỉ cần Docker)
java scripts/LoadTest.java       # 200 user đồng thời
java scripts/SameUserTest.java   # 1 user spam 10 request → đúng 1 vé
k6 run scripts/k6-reserve.js     # load test ramp 1000 VU
```

---

## 🎓 Điều tôi học được

**1. Race condition không có hình dạng cố định — nó đổi áo theo tầng.**
Cùng một lỗi check-then-act xuất hiện 3 lần trong project này: read-check-write trên SQL (oversell Ngày 2), GET-rồi-SET trên Redis nếu tách lệnh, và pay-vs-expire trên trạng thái đơn (Ngày 5). Học một lần, nhận ra nó ở mọi nơi.

**2. Câu chuyện nâng cấp DECR → Lua.**
Ngày 3, atomic của tôi là một lệnh `DECR` — đủ khi logic chỉ là "trừ vé". Ngày 6 thêm rule "mỗi người 1 vé", logic thành 4 bước; viết bằng 4 lệnh Redis riêng thì atomic *từng lệnh* nhưng hở *cả khối* — bug oversell cũ quay lại nguyên hình. Lua script gói cả khối vào một đơn vị thực thi. Bài học: **ranh giới atomic phải bao trùm toàn bộ khối check-then-act** — row lock, lệnh đơn, hay script là tùy ngữ cảnh, nguyên tắc không đổi.

**3. Race pay-vs-expire: status chính là version.**
Không cần cột version riêng — `UPDATE ... WHERE status='RESERVED'` là optimistic lock trá hình. Bên thua nhận 0 rows affected và phải xử lý điều đó một cách tử tế; job expire chỉ hoàn kho khi nó là bên thắng.

**4. At-least-once là hợp đồng, idempotency là trách nhiệm của mình.**
Broker chỉ hứa "không mất message", không hứa "không trùng". VNPay IPN (project trước của tôi) cũng vậy. Chốt chặn đúng chỗ là DB constraint — thứ duy nhất không thể lách kể cả khi có N consumer.

**5. p95 nói thật, average nói dối.** Hệ thống average 50ms vẫn có thể bắt 5% người dùng chờ 3 giây — và 5% của 5000 người là 250 khách hàng giận dữ.

---

## 📁 Ghi chú kỹ thuật chi tiết

Toàn bộ quan sát, số đo và trả lời câu hỏi thiết kế nằm trong [NOTES.md](NOTES.md) — viết theo từng ngày phát triển (10 ngày).
