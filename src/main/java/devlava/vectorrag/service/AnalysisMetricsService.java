package devlava.vectorrag.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AnalysisMetricsService {

    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger currentlyProcessing = new AtomicInteger(0);

    public void recordProcessingStart() {
        currentlyProcessing.incrementAndGet();
    }

    public void recordProcessingComplete(long processingTimeMs, boolean success) {
        currentlyProcessing.decrementAndGet();
        totalProcessingTime.addAndGet(processingTimeMs);

        if (success) {
            totalProcessed.incrementAndGet();
        } else {
            totalFailed.incrementAndGet();
        }
    }

    public Map<String, Object> getMetrics() {
        int processed = totalProcessed.get();
        int failed = totalFailed.get();
        long avgTime = processed > 0 ? totalProcessingTime.get() / processed : 0;

        return Map.of(
                "totalProcessed", processed,
                "totalFailed", failed,
                "successRate", processed + failed > 0 ? (double) processed / (processed + failed) * 100 : 0,
                "averageProcessingTimeMs", avgTime,
                "currentlyProcessing", currentlyProcessing.get()
        );
    }

    public void resetMetrics() {
        totalProcessed.set(0);
        totalFailed.set(0);
        totalProcessingTime.set(0);
        currentlyProcessing.set(0);
    }
}