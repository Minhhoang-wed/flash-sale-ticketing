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
 * Test Ngày 6: CÙNG 1 user bắn 10 request đồng thời vào /reserve.
 * Kỳ vọng: đúng 1 request 202; còn lại bị chặn bởi rate limit (429)
 * hoặc rule 1 vé/người (409).
 *
 * Chạy: java scripts/SameUserTest.java
 */
public class SameUserTest {

    public static void main(String[] args) throws Exception {
        String base = System.getProperty("base", "http://localhost:8080");
        String eventId = System.getProperty("event", "1");
        String userId = System.getProperty("user", "spammer-1");
        int requests = Integer.parseInt(System.getProperty("requests", "10"));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();

        // Reset để user này chưa từng mua
        client.send(HttpRequest.newBuilder(URI.create(base + "/api/debug/reset"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(requests);
        AtomicInteger accepted = new AtomicInteger();   // 202
        AtomicInteger conflict = new AtomicInteger();   // 409 (đã mua / hết vé)
        AtomicInteger rateLimited = new AtomicInteger();// 429
        AtomicInteger other = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        for (int i = 0; i < requests; i++) {
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
                    switch (code) {
                        case 201, 202 -> accepted.incrementAndGet();
                        case 409 -> conflict.incrementAndGet();
                        case 429 -> rateLimited.incrementAndGet();
                        default -> other.incrementAndGet();
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }
        startGate.countDown();
        doneGate.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        System.out.println("===== " + requests + " request đồng thời, cùng user '" + userId + "' =====");
        System.out.println("202 Accepted (giữ chỗ OK)   : " + accepted.get());
        System.out.println("409 Conflict (đã mua/hết vé): " + conflict.get());
        System.out.println("429 Rate limited            : " + rateLimited.get());
        System.out.println("Khác                        : " + other.get());

        if (accepted.get() == 1) {
            System.out.println("✅ CHUẨN: đúng 1 vé cho 1 user, spam bị chặn (409 + 429).");
        } else if (accepted.get() == 0) {
            System.out.println("⚠️ Không request nào lọt — rate limit chặn hết? Thử giảm requests hoặc tăng RATE_LIMIT_PER_SECOND.");
        } else {
            System.out.println("❌ SAI: " + accepted.get() + " request cùng user đều giữ được chỗ — rule 1 vé/người thủng!");
        }
    }
}
