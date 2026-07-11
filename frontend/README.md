# FlashTix frontend

Frontend React 18 + Vite + Tailwind CSS cho hệ thống Flash Sale Ticketing.

## Chạy local

Backend cần chạy tại `http://localhost:8080` trước. Từ thư mục `frontend`:

```bash
npm install
npm run dev
```

Mở `http://localhost:5173`. Các request `/api/*` được Vite proxy sang backend nên không cần bật CORS.

## Kiểm tra

```bash
npm test
npm run build
```

Để demo luồng hết hạn nhanh, chạy backend với `RESERVATION_TTL_SECONDS=60`. Kho vé thực tế có thể được hoàn lại sau tối đa một chu kỳ quét `EXPIRE_SCAN_MS` (mặc định 30 giây).

## Luồng demo gợi ý

1. Bấm **Giành vé ngay** và chờ hệ thống chuyển từ hàng đợi sang giữ chỗ.
2. Thanh toán trước khi đồng hồ về `00:00` để nhận vé điện tử.
3. Dùng **Đổi user** để mô phỏng người mua khác.
4. Mở **Debug**, chọn **Spam 10 request** để quan sát `202 / 409 / 429` hoặc **Reset demo** để đưa kho về trạng thái ban đầu.
