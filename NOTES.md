# NOTES — Ngày 2: Race condition & DB lock

## Setup thí nghiệm

- 1 event, 100 vé. Load test: 200 request đồng thời vào `POST /api/events/1/reserve`
  (ExecutorService 200 thread + CountDownLatch thả cùng lúc — xem `scripts/LoadTest.java`).
- Đổi chiến lược qua env: `RESERVE_STRATEGY=naive | pessimistic | optimistic`.
- Reset giữa các lần chạy: `POST /api/debug/reset` (LoadTest tự gọi).

## Kết quả đo được (điền số thực tế khi chạy)

| Chiến lược | 201 Created | 409 | Oversell | Thời gian | Retry (optimistic) |
|---|---|---|---|---|---|
| naive | ___ (kỳ vọng >100) | ___ | CÓ — ___ vé thừa | ___ ms | — |
| pessimistic | 100 | 100 | 0 | ___ ms | — |
| optimistic | ___ | ___ | 0 | ___ ms | ___ lần |

Ảnh chụp: `docs/before-naive.png`, `docs/after-pessimistic.png`

## Vì sao naive oversell?

`reserve()` naive làm 3 bước không atomic: (1) SELECT remaining, (2) check > 0,
(3) UPDATE remaining - 1. Ở isolation mặc định READ COMMITTED, 2 transaction có thể
cùng SELECT thấy remaining = 1, cùng pass check, cùng UPDATE → **lost update**,
bán 2 vé cho 1 chỗ. `@Transactional` không cứu: transaction đảm bảo atomic
commit/rollback, KHÔNG đảm bảo 2 transaction không giẫm chân nhau khi cùng đọc.

## Cách 1 — Pessimistic lock (SELECT ... FOR UPDATE)

- `@Lock(PESSIMISTIC_WRITE)` trên query → Postgres khóa row event.
- Transaction thứ 2 chạm vào row phải **chờ** tx thứ 1 commit → read-check-write
  được serialize → không thể oversell.
- Trade-off: 200 request xếp hàng trên 1 row → throughput giảm, latency đuôi tăng.
  Nhưng ĐÚNG tuyệt đối.

## Cách 2 — Optimistic lock (version + retry)

- Không khóa khi đọc. Lúc ghi: `UPDATE ... WHERE version = :đã_đọc` — nếu 0 row
  bị update nghĩa là ai đó đã ghi trước → retry (tối đa 3 lần).
- Làm thủ công (không dùng `@Version` của JPA) để giữ nguyên naive path cho demo.
- Quan sát: 200 thread tranh 1 row → tỉ lệ conflict cực cao, retry bão
  (`optimisticRetries` trong `/api/debug/metrics`), nhiều request fail oan dù còn vé.

## Trả lời câu hỏi cuối ngày

**Pessimistic**: giả định "chắc chắn có tranh chấp" → khóa trước, làm sau.
Chi phí: chờ lock, giảm song song. **Optimistic**: giả định "hiếm khi tranh chấp"
→ làm trước, lúc ghi mới kiểm tra, sai thì làm lại. Chi phí: retry.

Flash-sale = hàng nghìn write đổ vào **cùng 1 row** trong vài giây — tranh chấp
không phải "hiếm" mà là "chắc chắn và cực đại". Optimistic khi đó thoái hóa thành
retry storm: tốn CPU/DB round-trip cho các lần fail, user bị báo lỗi oan.
Pessimistic biến cuộc đua thành hàng đợi có trật tự — chậm hơn mỗi request
nhưng tổng thể ổn định và đúng. Chọn **pessimistic ở tầng DB** vì DB là nguồn
sự thật cuối cùng về stock; lock ở app (synchronized) vô dụng khi chạy nhiều
instance. (Ngày sau: đẩy việc chặn số đông ra Redis, DB chỉ còn xử lý số ít.)

---

# NOTES — Ngày 3: Redis atomic counter + cache-aside

## Kiến trúc mới của pha reserve

```
Client ──POST /reserve──> App ──DECR stock:event:1──> Redis
                            │
                            ├─ DECR >= 0 → INSERT order vào Postgres (201)
                            └─ DECR <  0 → INCR cộng bù → 409 Sold out
                                           (DB KHÔNG bị chạm)
```

- DB không còn trừ vé ở pha reserve — chỉ còn là nơi ghi đơn.
- Compensation: tạo đơn fail → INCR trả vé (try-catch quanh createOrder).
- Nạp kho khi start: `stock = totalTickets - số đơn RESERVED/PAID` (không set
  thẳng totalTickets, tránh oversell khi restart giữa đợt sale).

## Cache-aside cho GET /api/events/{id}

1. GET `event:{id}` trên Redis → hit thì trả luôn (không chạm DB).
2. Miss → query DB → `SET event:{id} <json> EX 60` → trả về.
3. Số vé còn lại luôn đọc live từ `stock:event:{id}` (counter, không cache TTL).
4. Invalidation: sửa event thì DELETE key (xem `evictEventCache`).

Kiểm chứng DoD: bật log SQL (đã bật), gọi GET 2 lần — lần 1 thấy `select ...`,
lần 2 KHÔNG thấy query nào trong log app.

## Kết quả load test (điền số thực tế)

| Chiến lược | 201 | 409 | Oversell | Thời gian |
|---|---|---|---|---|
| redis | 100 | 100 | 0 | ___ ms (kỳ vọng nhanh hơn pessimistic rõ rệt) |

## Trả lời câu hỏi cuối ngày

**Vì sao DECR chống được oversell mà GET-rồi-SET thì không?**
GET-rồi-SET là 2 lệnh riêng biệt — giữa chúng có "khe hở" để client khác chen vào:
2 client cùng GET được 1, cùng tính 1-1=0, cùng SET 0 → bán 2 vé, mất 1 update.
Đúng bài lost update của Ngày 2, chỉ đổi từ SQL sang Redis. DECR là MỘT lệnh
duy nhất, Redis single-threaded thực thi trọn vẹn từng lệnh theo hàng đợi →
read-modify-write nằm TRONG server, không có khe hở nào cho client thứ hai.
Giá trị trả về của DECR cho mỗi client một con số DUY NHẤT (99, 98, ..., 0, -1)
→ ai nhận số âm là chắc chắn hết vé, không cần check-then-act.

**Nếu Redis chết thì sao?**
- Reserve fail toàn bộ (connection error) → sale dừng. Fail-stop, KHÔNG oversell
  — thà không bán được còn hơn bán lố. App nên trả 503 thay vì 500.
- Dữ liệu mất khi restart? Stock key nằm trong RAM. Giải pháp: (1) Redis
  persistence AOF/RDB, (2) quan trọng hơn — nguồn sự thật để dựng lại kho là
  Postgres: `stock = total - đơn đã ghi` (chính là syncFromDb). Redis chỉ là
  counter tạm thời "ở cửa", DB mới là sổ cái.
- Production thật: Redis Sentinel/Cluster để failover; nhưng câu trả lời phỏng
  vấn quan trọng nhất là: **hệ thống phải fail về phía an toàn (không oversell)
  và có cách rebuild state từ nguồn sự thật.**
