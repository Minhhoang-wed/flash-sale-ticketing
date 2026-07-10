# Flash Sale Ticketing

Hệ thống bán vé flash-sale — project học concurrency. **Ngày 1: skeleton CRUD, chưa xử lý concurrency.**

## Stack

Spring Boot 3.3 (Java 21) · PostgreSQL 16 · Docker Compose · springdoc-openapi

## Chạy

```bash
docker compose up --build
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- App tự seed 1 event 100 vé khi khởi động (DataSeeder).

## API

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/events/{id}` | Thông tin sự kiện + vé còn lại |
| POST | `/api/events/{id}/reserve` | Giữ chỗ 1 vé (header `X-User-Id` bắt buộc) |
| POST | `/api/orders/{id}/pay` | RESERVED → PAID |

Ví dụ:

```bash
curl http://localhost:8080/api/events/1
curl -X POST -H "X-User-Id: ryan" http://localhost:8080/api/events/1/reserve
curl -X POST http://localhost:8080/api/orders/1/pay
```

## Test DoD

```bash
./scripts/test-dod.sh          # Linux/macOS/Git Bash
./scripts/test-dod.ps1         # Windows PowerShell
```

Đặt vé tuần tự 101 lần → 100 lần đầu HTTP 201, lần 101 HTTP 409 (sold out).

## Chiến lược reserve (đổi qua env `RESERVE_STRATEGY`)

| Giá trị | Cơ chế | Ngày |
|---|---|---|
| `naive` | read-check-write, CÓ race condition (demo) | 1 |
| `pessimistic` | `SELECT ... FOR UPDATE` | 2 |
| `optimistic` | version + retry 3 lần | 2 |
| `redis` (mặc định) | Redis `DECR` atomic + compensation | 3 |

Load test: `java scripts/LoadTest.java` (app phải đang chạy). Chi tiết quan sát: `NOTES.md`.

## ⚠️ Lỗi tiềm ẩn có chủ đích (câu hỏi cuối ngày)

`TicketService.reserve()` làm **read → check → write** không atomic:

1. Đọc `remainingTickets`
2. Nếu > 0 thì trừ 1 và save

Với request tuần tự thì ổn. Nhưng 2 request **đồng thời** cùng đọc `remainingTickets = 1` → cả hai đều pass bước check → cả hai đều trừ → **oversell** (bán quá số vé). Đây là race condition kinh điển kiểu *lost update / check-then-act*. `@Transactional` KHÔNG tự cứu được vì isolation level mặc định (READ COMMITTED) vẫn cho phép 2 transaction cùng đọc giá trị cũ.

**Ngày 2:** chứng minh bằng load test đồng thời, sau đó sửa (pessimistic lock / optimistic lock / atomic UPDATE).
