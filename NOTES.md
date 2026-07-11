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

---

# NOTES — Ngày 4: RabbitMQ + Idempotency

## Kiến trúc

```
Client ──POST /reserve──> App ──DECR──> Redis
                            │ (còn vé)
                            ├─ publish ReservationMessage ──> reservation.exchange
                            │                                      │ (reservation.created)
                            └─ 202 + reservationId            reservation.queue
                                                                    │
Client ──GET /orders/by-reservation/{id}──> DB <── consumer @RabbitListener
                                                    (tạo Order RESERVED)
```

- API chỉ làm 2 việc nhẹ: DECR + publish → trả 202 trong vài chục ms.
- Ghi đơn (INSERT) chạy nền ở consumer. Consumer tắt → message chờ trong queue,
  bật lên → xử lý tiếp (không mất đơn).
- Client poll `GET /api/orders/by-reservation/{reservationId}` (404 = đang xử lý).

## Demo DoD

1. `CONSUMER_ENABLED=false` + restart app → reserve vẫn 202, message đọng trong
   queue (xem http://localhost:15672, guest/guest, tab Queues).
2. Bật lại consumer (`CONSUMER_ENABLED=true` + restart) → queue tụt về 0, đơn xuất hiện trong DB.
3. Idempotency: web UI → Queues → reservation.queue → Publish message, dán cùng 1
   JSON 2 lần (properties: content_type = application/json):
   `{"reservationId":"test-dup-1","userId":"u1","eventId":1,"reservedAt":"2026-07-10T00:00:00Z"}`
   → DB chỉ có 1 đơn, log consumer in "Duplicate message skipped".

## Trả lời câu hỏi cuối ngày

**Message bị giao 2 lần thì sao? At-least-once là gì?**
RabbitMQ mặc định đảm bảo at-least-once: message chắc chắn được giao ÍT NHẤT
1 lần, nhưng có thể NHIỀU hơn — ví dụ consumer xử lý xong nhưng chết trước khi
ack → broker không biết đã xử lý → giao lại cho consumer khác. Exactly-once
qua network là bất khả thi trong thực tế (chi phí cực đắt), nên nguyên tắc là:
**broker đảm bảo at-least-once, consumer tự đảm bảo idempotent** — xử lý N lần
cho kết quả y như 1 lần.

**Em xử lý idempotent thế nào?**
Khóa tự nhiên của nghiệp vụ là `reservationId` (UUID sinh 1 lần tại API).
Chốt chặn: UNIQUE constraint trên `orders.reservation_id` — tầng DB, không thể
lách kể cả 2 consumer chạy song song. Consumer check-exists trước cho nhẹ,
nhưng cái quyết định là constraint: bắt `DataIntegrityViolationException` →
log + bỏ qua êm (không ném lại — ném lại là message bị requeue, lặp vô hạn).

**Liên hệ VNPay IPN (AutoWash Pro):** IPN cũng là at-least-once — VNPay retry
callback khi chưa nhận 200. Xử lý y hệt: check `vnp_TxnRef` đã xử lý chưa,
rồi mới cộng tiền/đổi trạng thái. Một pattern, hai bối cảnh — điểm ăn tiền
khi phỏng vấn.

---

# NOTES — Ngày 5: Vòng đời vé đầy đủ (unhappy path)

## Vòng đời

```
RESERVED ──pay (trước hạn)──> PAID
    │
    └──quá hạn (job quét 30s)──> EXPIRED ──INCR──> vé về kho, người khác mua được
```

- RESERVED có hạn `reservation-ttl-seconds` (mặc định 600s; demo đặt 60s).
- `expiresAt` chốt ngay lúc tạo đơn = reservedAt + TTL.

## Race giữa pay và expire — bài concurrency thứ 2

Nếu viết kiểu đọc-check-ghi (đọc order → thấy RESERVED → set PAID/EXPIRED → save)
thì pay và job expire có thể CÙNG thấy RESERVED → một đơn vừa PAID vừa EXPIRED
(bên save sau đè bên trước), hoặc tệ hơn: đơn PAID nhưng vé vẫn bị INCR hoàn kho
→ tổng vé > 100. Đây là đúng bài oversell Ngày 2 khoác áo khác.

Giải pháp: **check-and-update trong MỘT câu UPDATE có điều kiện** (optimistic
style trên cột status — không cần cột version vì status chính là "version"):

- pay:    `UPDATE orders SET status='PAID'    WHERE id=? AND status='RESERVED' AND expires_at > now()`
- expire: `UPDATE orders SET status='EXPIRED' WHERE id=? AND status='RESERVED'`

DB row lock đảm bảo 2 câu UPDATE trên cùng 1 row chạy TUẦN TỰ. Ai chạy trước
thì thắng; kẻ đến sau thấy status đã đổi → điều kiện WHERE fail → 0 row.
Job CHỈ INCR trả vé khi update trả 1 → không bao giờ hoàn kho vé đã bán.
Pay có thêm điều kiện `expires_at > now()` → không thanh toán được đơn đã
quá hạn nhưng job chưa kịp quét (job chỉ chạy mỗi 30s).

## Trả lời câu hỏi cuối ngày (câu PV số 7)

"User giữ chỗ mà không thanh toán → đơn RESERVED có hạn 10 phút. Job @Scheduled
quét mỗi 30s, đơn quá hạn bị chuyển EXPIRED bằng UPDATE có điều kiện, và chỉ khi
update thành công mới INCR trả vé về Redis — vé quay lại kho cho người khác mua.
Race giữa pay và expire: cả hai đều là conditional UPDATE trên status, DB row
lock cho đúng một bên thắng, bên thua nhận 0 rows affected và xử lý theo —
nên không tồn tại case vừa PAID vừa EXPIRED, cũng không có chuyện hoàn kho
vé đã bán."

---

# NOTES — Ngày 6: Lua script + Rate limiting

## Câu chuyện "nâng cấp DECR → Lua" (kể khi phỏng vấn)

Ngày 3, atomic của mình là MỘT LỆNH: DECR. Đủ dùng khi logic chỉ có "trừ vé".
Ngày 6 thêm rule "mỗi người 1 vé" → logic thành: check đã-mua → check stock →
trừ vé → ghi nhận người mua. Bốn bước, nếu viết bằng 4 lệnh Redis riêng thì
atomic TỪNG LỆNH nhưng không atomic CẢ KHỐI — khe hở giữa các lệnh lại mở ra,
đúng bản chất lỗi oversell Ngày 2 quay về, chỉ khác tầng:

```
SISMEMBER purchased user   → 0 (chưa mua)     [request A]
SISMEMBER purchased user   → 0 (chưa mua)     [request B — chen vào!]
DECR stock                                     [A]
DECR stock                                     [B] → user mua được 2 vé
SADD purchased user                            [A]
SADD purchased user                            [B]
```

Lua script giải quyết: Redis thực thi TOÀN BỘ script như một lệnh duy nhất
(single-threaded, không lệnh nào của client khác chen vào giữa) → check-then-act
nằm trọn trong server. Bài học xuyên suốt project: **ranh giới atomic phải bao
trùm toàn bộ khối check-then-act** — bằng row lock (Ngày 2), bằng lệnh đơn
(Ngày 3), hay bằng Lua (Ngày 6) khi logic dài hơn một lệnh.

## Rate limiting

`INCR req:{userId}:{epochGiây}` + `EXPIRE 1` — fixed window 1 giây.
Quá N (mặc định 5) → 429. Interceptor chỉ gắn vào `/api/events/*/reserve`.

Tự thú vị: INCR + EXPIRE cũng là 2 lệnh — nếu app crash giữa chừng, key sống
mãi không TTL. Khe hở này vô hại ở đây (key theo giây, rò rỉ vài key là cùng)
nhưng production chuẩn sẽ... gộp bằng Lua. Đúng vòng lặp bài học của ngày.

## Trả lời câu hỏi cuối ngày

**Vì sao check-đã-mua rồi mới DECR bằng 2 lệnh riêng là sai?**
Vì giữa SISMEMBER và DECR có khe hở: 2 request của cùng user cùng qua bước
check (cùng thấy "chưa mua") rồi cùng DECR → 1 user 2 vé. Check-then-act
tách rời = race condition, bất kể tầng nào (SQL, Redis, hay RAM).

**Lua giải quyết thế nào?**
Gói cả khối vào 1 script; Redis chạy script nguyên tử — không interleaving.
Kết quả trả về (-2/-1/số còn lại) cho app biết chính xác chuyện gì xảy ra
mà không cần đọc lại. Lưu ý trade-off: script phải NGẮN (Redis single-threaded,
script dài = chặn cả server) và không nên có side-effect ngoài Redis.

---

# NOTES — Ngày 7: Retry + DLQ

## Cấu hình

- **Retry in-memory** (Spring, không phải broker): 3 lần, backoff 1s → 2s.
  Message chưa bị trả về broker trong lúc retry — consumer giữ và thử lại.
- **Hết retry** → `default-requeue-rejected: false` → basic.reject(requeue=false)
  → queue chính có `x-dead-letter-exchange` → broker chuyển message sang
  `reservation.dlx` → nằm gọn trong `reservation.dlq`.
- Demo: reserve với `X-User-Id: poison` → consumer cố tình throw → xem log thấy
  3 attempt cách nhau 1s/2s → message hiện trong DLQ (web UI + `/api/debug/metrics`
  trường `dlqMessages`).

## ⚠️ Vận hành: đổi args của queue đã tồn tại

Queue `reservation.queue` cũ (Ngày 4) không có DLX args. RabbitMQ KHÔNG cho
redeclare queue với args khác → `PRECONDITION_FAILED`. Fix: xóa container
rabbitmq (docker compose down) để queue được tạo mới. Bài học thật về vận hành:
thay đổi topology queue trong production phải có migration plan.

## Trả lời câu hỏi cuối ngày

**DLQ để làm gì?**
Là "bệnh viện" cho message hỏng: message fail lặp lại (bug parse, data không
hợp lệ, dependency chết lâu) được cách ly khỏi queue chính, giữ nguyên vẹn
payload + header (kèm lý do chết trong x-death) để người vận hành xem, sửa,
và replay lại sau khi fix bug.

**Không có DLQ thì chuyện gì xảy ra?** Hai kịch bản, đều tệ:
1. `requeue = true` (mặc định): message độc quay lại ĐẦU queue → consumer xử lý
   fail → requeue → fail... vòng lặp vô hạn chiếm CPU, message tốt phía sau bị
   **head-of-line blocking** — một message hỏng làm tê liệt cả pipeline.
2. `requeue = false` mà không có DLX: broker DROP message — mất dữ liệu im lặng,
   khách giữ chỗ xong không bao giờ có đơn, không dấu vết để điều tra.

DLQ là đường thoát thứ ba: không kẹt, không mất — trạng thái "chờ người xử lý".
