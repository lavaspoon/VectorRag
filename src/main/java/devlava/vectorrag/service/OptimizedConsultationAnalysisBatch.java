// src/main/java/devlava/service/OptimizedConsultationAnalysisBatch.java
package devlava.vectorrag.service;

import devlava.vectorrag.dto.ConsultationAnalysisResult;
import devlava.vectorrag.entity.SttData;
import devlava.vectorrag.repository.SttDataRepository;
import devlava.vectorrag.util.ConsultationAnalysisLogger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class OptimizedConsultationAnalysisBatch {

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @Autowired
    private SttDataRepository sttDataRepository;

    @Autowired
    private ConsultationAnalysisService analysisService;

    @Value("${consultation.analysis.batch-size:3}")
    private int batchSize;

    @Value("${consultation.analysis.processing-delay:3000}")
    private long processingDelay;

    @Value("${consultation.analysis.max-retry-count:2}")
    private int maxRetryCount;

    @Scheduled(fixedDelay = 300000) // 5분마다 실행
    public void processUnanalyzedConsultationsSequentially() {

        if (!isProcessing.compareAndSet(false, true)) {
            log.info("Batch analysis is already running, skipping this execution");
            return;
        }

        try {
            log.info("Starting sequential batch analysis process");
            ConsultationAnalysisLogger.logBatchStart(batchSize);

            long pendingCount = sttDataRepository.countPendingAnalysis();
            log.info("Found {} pending consultations for analysis", pendingCount);

            if (pendingCount == 0) {
                log.info("No pending consultations found");
                return;
            }

            int processedCount = 0;
            int failedCount = 0;
            int totalProcessed = 0;

            while (totalProcessed < pendingCount) {
                List<SttData> batch = sttDataRepository.findUnanalyzedData(
                        PageRequest.of(0, batchSize)
                );

                if (batch.isEmpty()) {
                    break;
                }

                log.info("Processing batch of {} consultations (Total processed: {})",
                        batch.size(), totalProcessed);

                for (SttData sttData : batch) {
                    try {
                        if (totalProcessed > 0) {
                            Thread.sleep(processingDelay);
                        }

                        boolean success = processConsultationWithRetry(sttData);
                        if (success) {
                            processedCount++;
                        } else {
                            failedCount++;
                        }
                        totalProcessed++;

                        if (totalProcessed % 10 == 0) {
                            log.info("Progress: {}/{} processed", totalProcessed, pendingCount);
                        }

                    } catch (InterruptedException e) {
                        log.warn("Batch processing interrupted", e);
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Unexpected error processing consultation: {}",
                                sttData.getConsultationNumber(), e);
                        failedCount++;
                        totalProcessed++;
                    }
                }

                if (totalProcessed < pendingCount) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            ConsultationAnalysisLogger.logBatchComplete(processedCount, failedCount);
            log.info("Sequential batch analysis completed - Processed: {}, Failed: {}",
                    processedCount, failedCount);

        } catch (Exception e) {
            log.error("Error during sequential batch analysis process", e);
        } finally {
            isProcessing.set(false);
        }
    }

    private boolean processConsultationWithRetry(SttData sttData) {
        int retryCount = 0;

        while (retryCount < maxRetryCount) {
            try {
                long startTime = System.currentTimeMillis();
                ConsultationAnalysisLogger.logAnalysisStart(sttData.getConsultationNumber());

                // 처리 중 상태로 변경
                updateConsultationStatus(sttData, "PROCESSING");

                // RAG 기반 분석 실행
                ConsultationAnalysisResult result = analysisService.analyzeWithRAG(
                        sttData.getConsultationContent()
                );

                // 결과 저장 - 각각 독립적인 트랜잭션으로 처리
                analysisService.updateAnalysisResult(sttData, result);

                long processingTime = System.currentTimeMillis() - startTime;
                ConsultationAnalysisLogger.logAnalysisComplete(
                        sttData.getConsultationNumber(), processingTime);

                log.info("Successfully processed consultation: {} ({}ms)",
                        sttData.getConsultationNumber(), processingTime);

                return true;

            } catch (Exception e) {
                retryCount++;
                log.warn("Analysis failed for consultation: {} (attempt {}/{})",
                        sttData.getConsultationNumber(), retryCount, maxRetryCount, e);

                if (retryCount < maxRetryCount) {
                    try {
                        Thread.sleep(1000 * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    ConsultationAnalysisLogger.logAnalysisError(
                            sttData.getConsultationNumber(), e.getMessage());
                    // 별도 트랜잭션으로 실패 상태 업데이트
                    analysisService.markAsFailed(sttData);
                    return false;
                }
            }
        }

        return false;
    }

    private void updateConsultationStatus(SttData sttData, String status) {
        try {
            sttData.setAnalysisStatus(status);
            sttDataRepository.save(sttData);
        } catch (Exception e) {
            log.error("Failed to update status for consultation: {}",
                    sttData.getConsultationNumber(), e);
        }
    }

    public void processSpecificConsultation(String consultationNumber) {
        log.info("Manual processing requested for consultation: {}", consultationNumber);

        SttData sttData = sttDataRepository.findById(consultationNumber)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found: " + consultationNumber));

        processConsultationWithRetry(sttData);
    }

    public boolean isCurrentlyProcessing() {
        return isProcessing.get();
    }

    public void stopProcessing() {
        log.warn("Batch processing stop requested");
        isProcessing.set(false);
    }
}