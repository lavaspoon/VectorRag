// src/main/java/devlava/service/VectorStoreInitializer.java
package devlava.vectorrag.service;

import devlava.vectorrag.entity.SttData;
import devlava.vectorrag.repository.SttDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VectorStoreInitializer implements CommandLineRunner {

    @Autowired
    private SttDataRepository sttDataRepository;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking vector store initialization...");
        initializeVectorStoreWithDuplicateCheck();
    }

    public void initializeVectorStoreWithDuplicateCheck() {
        try {
            // 1. 분석 완료된 데이터 조회
            List<SttData> analyzedData = sttDataRepository.findAnalyzedData();
            log.info("Found {} analyzed consultations in tb_stt_data", analyzedData.size());

            if (analyzedData.isEmpty()) {
                log.info("No analyzed data found for vector store initialization");
                return;
            }

            // 2. vector_store 테이블에서 기존 consultation_number들 조회
            Set<String> existingConsultationNumbers = getExistingConsultationNumbers();
            log.info("Found {} existing documents in vector_store", existingConsultationNumbers.size());

            // 3. 새로 추가할 데이터만 필터링
            List<SttData> newDataToAdd = analyzedData.stream()
                    .filter(data -> !existingConsultationNumbers.contains(data.getConsultationNumber()))
                    .collect(Collectors.toList());

            if (newDataToAdd.isEmpty()) {
                log.info("All analyzed data already exists in vector store. No duplicates added.");
                return;
            }

            log.info("Adding {} new documents to vector store (avoiding {} duplicates)",
                    newDataToAdd.size(), analyzedData.size() - newDataToAdd.size());

            // 4. 새 데이터만 vector store에 추가
            List<Document> documents = newDataToAdd.stream()
                    .map(this::createDocument)
                    .collect(Collectors.toList());

            vectorStore.add(documents);

            log.info("Successfully added {} new documents to vector store", documents.size());

        } catch (Exception e) {
            log.error("Failed to initialize vector store", e);
        }
    }

    /**
     * vector_store 테이블에서 기존 consultation_number들 조회
     */
    private Set<String> getExistingConsultationNumbers() {
        try {
            String sql = """
                SELECT DISTINCT metadata->>'consultationNumber' as consultation_number 
                FROM vector_store 
                WHERE metadata->>'consultationNumber' IS NOT NULL
                """;

            List<String> existingNumbers = jdbcTemplate.queryForList(sql, String.class);
            log.debug("Existing consultation numbers in vector_store: {}", existingNumbers);
            return Set.copyOf(existingNumbers);

        } catch (Exception e) {
            log.warn("Failed to query existing consultation numbers from vector_store table: {}", e.getMessage());
            log.info("Assuming vector_store is empty");
            return Set.of();
        }
    }

    private Document createDocument(SttData sttData) {
        try {
            Map<String, String> analysisResult = Map.of(
                    "mainInquiry", sttData.getResponse1() != null ? sttData.getResponse1() : "",
                    "hasNudge", sttData.getResponse2() != null ? sttData.getResponse2() : "N",
                    "nudgeType", sttData.getResponse3() != null ? sttData.getResponse3() : "N",
                    "nudgeContent", sttData.getResponse4() != null ? sttData.getResponse4() : "N",
                    "customerResponse", sttData.getResponse5() != null ? sttData.getResponse5() : "N",
                    "inappropriateNudge", sttData.getResponse6() != null ? sttData.getResponse6() : "N",
                    "inappropriateReason", sttData.getResponse7() != null ? sttData.getResponse7() : "N"
            );

            String analysisResultJson = objectMapper.writeValueAsString(analysisResult);

            Map<String, Object> metadata = Map.of(
                    "consultationNumber", sttData.getConsultationNumber(),
                    "consultant", sttData.getConsultant(),
                    "analysisResult", analysisResultJson,
                    "consultationTime", sttData.getConsultationTime().toString()
            );

            return new Document(sttData.getConsultationContent(), metadata);

        } catch (Exception e) {
            log.error("Error creating document for consultation: {}", sttData.getConsultationNumber(), e);
            Map<String, Object> metadata = Map.of(
                    "consultationNumber", sttData.getConsultationNumber(),
                    "consultant", sttData.getConsultant()
            );
            return new Document(sttData.getConsultationContent(), metadata);
        }
    }

    /**
     * 새로운 분석 결과를 vector store에 추가 (중복 체크 포함)
     */
    public void addNewAnalyzedData(SttData sttData) {
        try {
            Set<String> existing = getExistingConsultationNumbers();
            if (existing.contains(sttData.getConsultationNumber())) {
                log.debug("Consultation {} already exists in vector store, skipping",
                        sttData.getConsultationNumber());
                return;
            }

            Document document = createDocument(sttData);
            vectorStore.add(List.of(document));
            log.info("Added new analyzed data to vector store: {}", sttData.getConsultationNumber());
        } catch (Exception e) {
            log.error("Failed to add new analyzed data to vector store: {}",
                    sttData.getConsultationNumber(), e);
        }
    }

    /**
     * 개발용: vector_store 완전 초기화
     */
    public void clearAndReinitialize() {
        try {
            log.warn("Clearing all data from vector_store table");
            jdbcTemplate.execute("DELETE FROM vector_store");

            List<SttData> analyzedData = sttDataRepository.findAnalyzedData();
            if (!analyzedData.isEmpty()) {
                List<Document> documents = analyzedData.stream()
                        .map(this::createDocument)
                        .collect(Collectors.toList());

                vectorStore.add(documents);
                log.info("Reinitialized vector_store with {} documents", documents.size());
            }
        } catch (Exception e) {
            log.error("Failed to clear and reinitialize vector_store", e);
        }
    }
}