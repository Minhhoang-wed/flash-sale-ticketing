import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load test Ngày 2: bắn N request đồng thời vào /reserve.
 *
 * Chạy (cần JDK 11+, không cần build):
 *   java scripts/LoadTest.java
 *   java -Dthreads=200 -Dbase=http://localhost:8080 -Devent=1 scripts/LoadTest.java
 *
 * CountDownLatch startGate giữ TẤT CẢ thread lại rồi thả cùng một lúc
 * → tối đa hóa độ đồng thời, dễ lộ race condition nhất.
 */
public class LoadTest {

    public static void main(String[] args) throws Exception {
        String base = System.getProperty("base", "http://localhost:8080");
        int threads = Integer.parseInt(System.getProperty("threads", "200"));
        String eventId = System.getProperty("event", "1");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 0. Reset stock + orders + metrics
        HttpResponse<String> resetRes = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/debug/reset"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        System.out.println("Reset: HTTP " + resetRes.statusCode() + " " + resetRes.body());

        // 1. Chuẩn bị N thread, tất cả chờ ở startGate
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger created = new AtomicInteger();   // 201
        AtomicInteger conflict = new AtomicInteger();  // 409
        AtomicInteger error = new AtomicInteger();     // khác

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 1; i <= threads; i++) {
            final String userId = "user-" + i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    HttpRequest req = HttpRequest.newBuilder(
                                    URI.create(base + "/api/events/" + eventId + "/reserve"))
                            .header("X-User-Id", userId)
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                    int code = client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
                    if (code == 201) created.incrementAndGet();
                    else if (code == 409) conflict.incrementAndGet();
                    else error.incrementAndGet();
                } catch (Exception e) {
                    error.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // 2. Thả tất cả cùng lúc và đo thời gian
        long t0 = System.nanoTime();
        startGate.countDown();
        doneGate.await(120, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdownNow();

        // 3. Kết quả
        String eventBody = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/events/" + eventId)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
        String metricsBody = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/debug/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();

        System.out.println();
        System.out.println("===== KẾT QUẢ (" + threads + " request đồng thời, " + elapsedMs + " ms) =====");
        System.out.println("201 Created (giữ chỗ OK) : " + created.get());
        System.out.println("409 Conflict (hết vé)    : " + conflict.get());
        System.out.println("Lỗi khác                 : " + error.get());
        System.out.println("Event sau test           : " + eventBody);
        System.out.println("Metrics                  : " + metricsBody);
        System.out.println();

        int totalTickets = 100;
        if (created.get() > totalTickets) {
            System.out.println("❌ OVERSELL! Bán " + created.get() + "/" + totalTickets
                    + " vé (thừa " + (created.get() - totalTickets) + ") — race condition đã lộ mặt.");
        } else if (created.get() == totalTickets) {
            System.out.println("✅ CHUẨN: đúng " + totalTickets + " vé bán ra, 0 oversell.");
        } else {
            System.out.println("⚠️ Bán được " + created.get() + "/" + totalTickets
                    + " vé — ít hơn kỳ vọng (xem lỗi/contention ở trên).");
        }
    }
}
