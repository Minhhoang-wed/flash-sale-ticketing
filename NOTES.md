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
