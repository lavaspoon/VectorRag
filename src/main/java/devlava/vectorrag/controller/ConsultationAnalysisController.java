package devlava.vectorrag.controller;

import devlava.vectorrag.dto.ConsultationAnalysisResult;
import devlava.vectorrag.entity.SttData;
import devlava.vectorrag.service.OptimizedConsultationAnalysisBatch;
import devlava.vectorrag.service.ConsultationAnalysisService;
import devlava.vectorrag.service.AnalysisMetricsService;
import devlava.vectorrag.repository.SttDataRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consultation-analysis")
@Slf4j
public class ConsultationAnalysisController {

    @Autowired
    private ConsultationAnalysisService analysisService;

    @Autowired
    private OptimizedConsultationAnalysisBatch analysisBatch;

    @Autowired
    private AnalysisMetricsService metricsService;

    @Autowired
    private SttDataRepository sttDataRepository;

    /**
     * 분석 대기 중인 상담 목록 조회
     */
    @GetMapping("/pending")
    public ResponseEntity<List<SttData>> getPendingConsultations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<SttData> pendingData = sttDataRepository.findUnanalyzedData(
                PageRequest.of(page, size)
        );

        return ResponseEntity.ok(pendingData);
    }

    /**
     * 분석 상태 통계 조회
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAnalysisStatus() {
        long pendingCount = sttDataRepository.countPendingAnalysis();
        long completedCount = sttDataRepository.countCompletedAnalysis();
        long failedCount = sttDataRepository.countFailedAnalysis();
        long totalCount = sttDataRepository.count();

        Map<String, Object> status = Map.of(
                "totalCount", totalCount,
                "completedCount", completedCount,
                "pendingCount", pendingCount,
                "failedCount", failedCount,
                "completionRate", totalCount > 0 ? (double) completedCount / totalCount * 100 : 0,
                "isProcessing", analysisBatch.isCurrentlyProcessing()
        );

        return ResponseEntity.ok(status);
    }

    /**
     * 성능 메트릭 조회
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(metricsService.getMetrics());
    }

    /**
     * 특정 상담 번호 수동 분석 실행
     */
    @PostMapping("/analyze/{consultationNumber}")
    public ResponseEntity<Map<String, String>> analyzeSpecificConsultation(
            @PathVariable String consultationNumber) {

        try {
            analysisBatch.processSpecificConsultation(consultationNumber);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "분석이 시작되었습니다.",
                    "consultationNumber", consultationNumber
            ));
        } catch (Exception e) {
            log.error("Manual analysis failed for consultation: {}", consultationNumber, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "consultationNumber", consultationNumber
            ));
        }
    }

    /**
     * 배치 분석 수동 실행
     */
    @PostMapping("/batch/run")
    public ResponseEntity<Map<String, String>> runBatchAnalysis() {
        try {
            if (analysisBatch.isCurrentlyProcessing()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "배치 분석이 이미 실행 중입니다."
                ));
            }

            new Thread(() -> analysisBatch.processUnanalyzedConsultationsSequentially()).start();

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "배치 분석이 시작되었습니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 배치 분석 중단
     */
    @PostMapping("/batch/stop")
    public ResponseEntity<Map<String, String>> stopBatchAnalysis() {
        try {
            analysisBatch.stopProcessing();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "배치 분석 중단 요청이 전송되었습니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 특정 상담 내용 즉시 분석 (테스트용)
     */
    @PostMapping("/test-analyze")
    public ResponseEntity<ConsultationAnalysisResult> testAnalyze(
            @RequestBody Map<String, String> request) {

        try {
            String consultationContent = request.get("consultationContent");
            if (consultationContent == null || consultationContent.trim().isEmpty()) {
                throw new IllegalArgumentException("consultationContent is required");
            }

            ConsultationAnalysisResult result = analysisService.analyzeWithRAG(consultationContent);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Test analysis failed", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 메트릭 초기화
     */
    @PostMapping("/metrics/reset")
    public ResponseEntity<Map<String, String>> resetMetrics() {
        metricsService.resetMetrics();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "메트릭이 초기화되었습니다."
        ));
    }
}
