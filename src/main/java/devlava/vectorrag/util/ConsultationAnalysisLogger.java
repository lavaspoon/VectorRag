package devlava.vectorrag.util;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class ConsultationAnalysisLogger {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void logAnalysisStart(String consultationNumber) {
        log.info("[{}] Analysis started for consultation: {}",
                LocalDateTime.now().format(formatter), consultationNumber);
    }

    public static void logAnalysisComplete(String consultationNumber, long processingTimeMs) {
        log.info("[{}] Analysis completed for consultation: {} ({}ms)",
                LocalDateTime.now().format(formatter), consultationNumber, processingTimeMs);
    }

    public static void logAnalysisError(String consultationNumber, String error) {
        log.error("[{}] Analysis failed for consultation: {} - Error: {}",
                LocalDateTime.now().format(formatter), consultationNumber, error);
    }

    public static void logBatchStart(int batchSize) {
        log.info("[{}] Batch analysis started - Processing {} consultations",
                LocalDateTime.now().format(formatter), batchSize);
    }

    public static void logBatchComplete(int processedCount, int failedCount) {
        log.info("[{}] Batch analysis completed - Processed: {}, Failed: {}",
                LocalDateTime.now().format(formatter), processedCount, failedCount);
    }
}
