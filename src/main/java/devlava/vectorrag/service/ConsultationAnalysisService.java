// src/main/java/devlava/service/ConsultationAnalysisService.java
package devlava.vectorrag.service;

import devlava.vectorrag.dto.ConsultationAnalysisResult;
import devlava.vectorrag.entity.SttData;
import devlava.vectorrag.entity.ConsultationVector;
import devlava.vectorrag.repository.SttDataRepository;
import devlava.vectorrag.repository.ConsultationVectorRepository;
import devlava.vectorrag.util.ConsultationAnalysisLogger;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ConsultationAnalysisService {

    @Autowired
    private SttDataRepository sttDataRepository;

    @Autowired
    private ConsultationVectorRepository consultationVectorRepository;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Value("${spring.ai.ollama.chat.options.model:llama3.1:8b}")
    private String chatModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatClient chatClient;

    private void initializeChatClient() {
        if (chatClient == null) {
            chatClient = chatClientBuilder
                    .defaultOptions(OllamaOptions.create()
                            .withModel(chatModel)
                            .withTemperature(0.1))
                    .build();
        }
    }

    // ConsultationAnalysisService.java의 analyzeWithRAG 메서드 수정
    public ConsultationAnalysisResult analyzeWithRAG(String consultationContent) {
        try {
            initializeChatClient();

            log.info("=== RAG 분석 시작 ===");
            log.info("상담 내용 길이: {}", consultationContent.length());

            // 입력 텍스트 전처리
            String cleanedContent = preprocessConsultationContent(consultationContent);
            log.debug("전처리된 내용 길이: {}", cleanedContent.length());

            List<Document> similarDocuments = findSimilarConsultationsWithCache(cleanedContent);
            log.info("유사 문서 개수: {}", similarDocuments.size());

            String context = buildOptimizedContext(similarDocuments);
            String optimizedPrompt = createOptimizedPrompt(cleanedContent, context);

            // AI 호출을 try-catch로 감싸서 안전하게 처리
            String response = callAIWithSafetyWrapper(optimizedPrompt);

            return parseAndValidateResult(response);

        } catch (Exception e) {
            log.error("RAG 분석 중 오류 발생 - 상담 내용: {}",
                    consultationContent.substring(0, Math.min(50, consultationContent.length())), e);
            return createDefaultResult();
        }
    }

    /**
     * 입력 텍스트 전처리 - 특수문자 및 인코딩 문제 해결
     */
    private String preprocessConsultationContent(String content) {
        if (content == null) {
            return "";
        }

        // 특수문자 및 제어문자 제거
        String cleaned = content
                .replaceAll("[\\x00-\\x1F\\x7F]", " ")  // 제어문자 제거
                .replaceAll("\\s+", " ")               // 연속 공백 하나로
                .trim();

        // 너무 긴 텍스트는 잘라내기 (토큰 한계 고려)
        if (cleaned.length() > 2000) {
            cleaned = cleaned.substring(0, 2000) + "...";
            log.debug("긴 텍스트를 2000자로 제한했습니다");
        }

        return cleaned;
    }

    /**
     * AI 호출을 안전하게 처리
     */
    private String callAIWithSafetyWrapper(String prompt) {
        int maxRetries = 3;
        long baseDelay = 1000; // 1초

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("AI 호출 시도 {}/{}", attempt, maxRetries);

                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                if (response != null && !response.trim().isEmpty()) {
                    log.debug("AI 응답 성공 (길이: {})", response.length());
                    return response;
                } else {
                    throw new RuntimeException("AI 응답이 비어있음");
                }

            } catch (Exception e) {
                log.warn("AI 호출 시도 {}/{} 실패: {}", attempt, maxRetries, e.getMessage());

                if (attempt == maxRetries) {
                    throw new RuntimeException("AI 호출이 " + maxRetries + "번 모두 실패했습니다", e);
                }

                // 지수 백오프로 재시도 간격 증가
                try {
                    Thread.sleep(baseDelay * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("AI 호출 중 인터럽트 발생", ie);
                }
            }
        }

        throw new RuntimeException("AI 호출 재시도 한계 초과");
    }

    @Cacheable(value = "similarConsultations", key = "#consultationContent.hashCode()")
    public List<Document> findSimilarConsultationsWithCache(String consultationContent) {
        try {
            SearchRequest searchRequest = SearchRequest.query(consultationContent)
                    .withTopK(3)
                    .withSimilarityThreshold(0.75);

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            log.debug("Found {} similar consultations", documents.size());

            return documents;

        } catch (Exception e) {
            log.warn("Error searching similar consultations, using empty context", e);
            return List.of();
        }
    }

    private String buildOptimizedContext(List<Document> similarDocuments) {
        if (similarDocuments.isEmpty()) {
            return "참고 사례 없음";
        }

        StringBuilder contextBuilder = new StringBuilder("참고사례:\n");

        for (int i = 0; i < Math.min(similarDocuments.size(), 3); i++) {
            Document doc = similarDocuments.get(i);
            Map<String, Object> metadata = doc.getMetadata();

            String content = doc.getContent();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }

            contextBuilder.append(String.format("%d) %s", i + 1, content));

            if (metadata.containsKey("analysisResult")) {
                contextBuilder.append(" -> ").append(metadata.get("analysisResult"));
            }
            contextBuilder.append("\n");
        }

        return contextBuilder.toString();
    }

    private String createOptimizedPrompt(String consultationContent, String context) {
        return String.format("""
        통신사 상담에서 상담사의 넛지 활동을 분석해주세요.
        
        %s
        
        === 상담 내용 ===
        %s
        
        === 넛지 유형 ===
        1. 생활패턴연결: 취미/습관 파악하여 서비스 연결
        2. 사회적증거: "다른 고객들도", "인기 상품" 등
        3. 손실회피: "손해보고 계세요", "놓치실 수 있어요"
        4. 개인화추천: 고객 상황에 맞는 맞춤 제안
        5. 결합혜택: 여러 서비스 묶어서 할인 강조
        6. 한정혜택: 기간 한정, 특별 프로모션
        
        === 부적절한 넛지 ===
        - 강압적 어조
        - 개인정보 남용  
        - 허위 정보
        - 불필요한 강요
        
        아래 JSON 형태로만 답변하세요:
        {
            "mainInquiry": "고객 문의 요약",
            "hasNudge": "Y 또는 N",
            "nudgeType": "위 6가지 중 하나 또는 N",
            "nudgeContent": "상담사 멘트 인용 또는 N", 
            "customerResponse": "Y 또는 N",
            "inappropriateNudge": "Y 또는 N",
            "inappropriateReason": "이유 또는 N"
        }
        """, context, consultationContent);
    }
//    private String createOptimizedPrompt(String consultationContent, String context) {
//        return String.format("""
//        당신은 통신사 상담에서 상담사의 넛지(Nudge) 활동을 분석하는 고객경험 전문가입니다.
//
//        === 참고 사례 분석 결과 ===
//        %s
//
//        === 분석할 상담 대화 ===
//        %s
//
//        === 통신사 넛지(Nudge) 분석 기준 ===
//
//        **넛지 정의**: 고객의 선택을 강요하지 않으면서도 특정 통신 서비스나 요금제로 유도하는 부드러운 개입
//
//        **통신사 넛지 유형별 세부 기준**:
//
//        1. **생활패턴연결**: 고객의 라이프스타일, 취미, 사용패턴을 파악하여 관련 서비스 자연스럽게 연결
//           - 예: "영화 자주 보시나요?" → OTT 결합상품 제안
//           - 예: "게임 하세요?" → 게이밍 요금제 추천
//           - 특징: 개인 취향/습관 파악 → 맞춤 서비스 연결
//
//        2. **사회적증거**: 다른 고객들의 선택이나 인기도를 근거로 제시하여 동조 심리 활용
//           - 예: "같은 연령대 70%가 선택", "가장 인기 많은 요금제", "베스트셀러 상품"
//           - 특징: 통계, 순위, 인기도 언급
//
//        3. **손실회피**: 현재 요금제의 손해나 놓칠 혜택을 언급하여 변경 유도
//           - 예: "지금 요금제로는 손해보고 계세요", "이런 혜택 놓치고 계시네요"
//           - 특징: 현재 상황의 불이익, 기회 상실 강조
//
//        4. **개인화추천**: 고객의 사용량, 패턴, 가족구성에 맞는 맞춤형 서비스 제안
//           - 예: "데이터 많이 쓰시는 분께", "가족 단위로", "직장인분들께 인기"
//           - 특징: 고객 세그먼트별 특화 상품 제안
//
//        5. **결합혜택**: 여러 서비스를 묶어서 더 큰 혜택이 있음을 강조
//           - 예: "인터넷이랑 같이 하시면 더 저렴해요", "결합할인 받으세요"
//           - 특징: 번들링, 패키지 상품의 경제적 이점 강조
//
//        6. **한정혜택**: 기간 한정이나 특별 혜택으로 긴급성 조성 (단, 부드럽게)
//           - 예: "이번 달 가입 고객만", "특별 프로모션 중이에요"
//           - 특징: 시간 제한, 특별 혜택 강조 (강압적이지 않은 선에서)
//
//        **통신사 부적절한 넛지 판단 기준**:
//        - 강압적 어조나 협박성 멘트 ("안 바꾸시면 계속 손해", "지금 안 하면 후회")
//        - 사용량 데이터 부적절 활용 ("고객님 이런 사이트 많이 보시네요")
//        - 허위/과장 정보 ("다른 통신사는 더 비싸", "오늘만 특가")
//        - 불필요한 서비스 강요 (단순 요금 문의에 여러 부가서비스 계속 권유)
//        - 해지 방해나 불편함 조성
//        - 고령자/미성년자 대상 복잡한 상품 무리 권유
//
//        **통신사 특수 상황**:
//        - 요금제 변경, 해지 상담: 고객 이탈 방지를 위한 적절한 대안 제시는 넛지
//        - 단순 문의 (요금 조회, A/S): 추가 서비스 제안은 넛지로 분류
//        - 가족 결합, 인터넷 결합: 경제적 이점 제시는 긍정적 넛지
//
//        === 분석 지침 ===
//        위 상담 대화를 통신사 상황에 맞게 분석하여 다음 JSON 형태로만 답변하세요:
//
//        {
//            "mainInquiry": "고객의 핵심 문의나 상담 목적을 한 문장으로 정확히 요약",
//            "hasNudge": "넛지 기법이 사용되었으면 Y, 단순 업무처리나 정보제공만 있으면 N",
//            "nudgeType": "위 6가지 유형 중 해당하는 것 (생활패턴연결/사회적증거/손실회피/개인화추천/결합혜택/한정혜택), 없으면 N",
//            "nudgeContent": "넛지로 판단한 상담사의 구체적 멘트를 정확히 인용, 없으면 N",
//            "customerResponse": "고객이 넛지에 긍정적 반응(관심표현, 질문, 동의)했으면 Y, 거부/무관심/무반응이면 N",
//            "inappropriateNudge": "위 부적절 기준에 해당하는 넛지가 있었으면 Y, 적절했거나 넛지가 없으면 N",
//            "inappropriateReason": "부적절하다면 구체적 이유 (강압적/데이터남용/허위정보/불필요강요/해지방해/고령자부적합), 적절하면 N"
//        }
//
//        === 통신사 상담 분석 시 주의사항 ===
//
//        1. **넛지 vs 일반 상담 구분**:
//           - 넛지: 고객 상황/성향 파악 → 전략적 서비스 제안
//           - 일반상담: 요금 안내, 기술 지원, 단순 정보 제공
//
//        2. **통신사 특수성 고려**:
//           - 요금제 최적화 제안 = 긍정적 넛지
//           - 불필요한 부가서비스 권유 = 부정적 넛지 가능성
//           - 해지 방어 상담 = 적절한 대안 제시는 정당한 비즈니스 활동
//
//        3. **고객 반응 정확히 판단**:
//           - 긍정적: "그럼 바꿔주세요", "얼마나 저렴해져요?", "자세히 알려주세요"
//           - 부정적: "지금 거 괜찮아요", "생각해볼게요", "그냥 이대로", 무대답
//
//        4. **서비스 유형별 판단**:
//           - 요금제 변경: 사용 패턴 분석 후 제안 = 적절한 넛지
//           - 부가서비스: 필요성 없는 권유 = 부적절할 수 있음
//           - 결합상품: 실제 절약 효과 있으면 적절한 넛지
//
//        === 통신사 넛지 예시 분석 ===
//
//        **적절한 넛지 사례**:
//        고객: "요금이 너무 나와요"
//        상담사: "데이터 사용량 확인해보니 월 20GB 쓰시네요. 무제한 요금제가 오히려 3만원 저렴할 것 같은데요?"
//        → 넛지유형: 개인화추천, 고객반응: 긍정적, 부적절성: 없음
//
//        **부적절한 넛지 사례**:
//        고객: "해지하고 싶어요"
//        상담사: "해지하시면 위약금 30만원이고, 다른 통신사는 더 비싸요. 절대 후회하실 거예요"
//        → 넛지유형: 손실회피, 부적절성: 강압적/허위정보
//
//        이제 위 기준을 바탕으로 주어진 통신사 상담 대화를 정확히 분석하여 JSON으로만 답변하세요.
//        """, context, consultationContent);
//    }

    private ConsultationAnalysisResult parseAndValidateResult(String response) {
        try {
            String jsonPart = extractJsonFromResponse(response);
            ConsultationAnalysisResult result = objectMapper.readValue(jsonPart, ConsultationAnalysisResult.class);
            validateAndCleanResult(result);
            return result;
        } catch (Exception e) {
            log.error("Error parsing AI response: {}", response, e);
            return createDefaultResult();
        }
    }

    private String extractJsonFromResponse(String response) {
        response = response.trim();
        int startIndex = response.indexOf("{");
        int endIndex = response.lastIndexOf("}");

        if (startIndex >= 0 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }

        throw new IllegalArgumentException("Valid JSON not found in response");
    }

    private void validateAndCleanResult(ConsultationAnalysisResult result) {
        result.setHasMarketing(normalizeYN(result.getHasMarketing()));
        result.setCustomerAgreed(normalizeYN(result.getCustomerAgreed()));
        result.setInappropriateMarketing(normalizeYN(result.getInappropriateMarketing()));

        if ("N".equals(result.getHasMarketing())) {
            result.setMarketingType("N");
            result.setMarketingMent("N");
            result.setCustomerAgreed("N");
        }

        if ("N".equals(result.getInappropriateMarketing())) {
            result.setInappropriateMent("N");
        }

        if (result.getMainInquiry() == null) result.setMainInquiry("문의 내용 확인 필요");
        if (result.getMarketingType() == null) result.setMarketingType("N");
        if (result.getMarketingMent() == null) result.setMarketingMent("N");
        if (result.getInappropriateMent() == null) result.setInappropriateMent("N");
    }

    private String normalizeYN(String value) {
        if (value == null) return "N";
        String normalized = value.trim().toUpperCase();
        return normalized.equals("Y") ? "Y" : "N";
    }

    private ConsultationAnalysisResult createDefaultResult() {
        ConsultationAnalysisResult result = new ConsultationAnalysisResult();
        result.setMainInquiry("분석 실패 - 수동 확인 필요");
        result.setHasMarketing("N");
        result.setMarketingType("N");
        result.setMarketingMent("N");
        result.setCustomerAgreed("N");
        result.setInappropriateMarketing("N");
        result.setInappropriateMent("N");
        return result;
    }

    /**
     * 분석 결과를 데이터베이스에 저장 - 새로운 트랜잭션으로 실행
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAnalysisResult(SttData sttData, ConsultationAnalysisResult result) {
        try {
            sttData.setResponse1(result.getMainInquiry());
            sttData.setResponse2(result.getHasMarketing());
            sttData.setResponse3(result.getMarketingType());
            sttData.setResponse4(result.getMarketingMent());
            sttData.setResponse5(result.getCustomerAgreed());
            sttData.setResponse6(result.getInappropriateMarketing());
            sttData.setResponse7(result.getInappropriateMent());
            sttData.setAnalysisStatus("COMPLETED");
            sttData.setAnalysisDate(LocalDateTime.now());

            sttDataRepository.save(sttData);

            log.info("Analysis result saved for consultation: {}", sttData.getConsultationNumber());

            // Vector Store 저장은 별도 트랜잭션으로 처리 (실패해도 메인 저장에 영향 없음)
            saveToVectorStoreAsync(sttData, result);

        } catch (Exception e) {
            log.error("Error saving analysis result for consultation: {}", sttData.getConsultationNumber(), e);
            throw e; // 트랜잭션 롤백을 위해 예외 다시 던지기
        }
    }

    /**
     * Vector Store 저장 - 비동기로 처리하여 메인 트랜잭션에 영향 없음
     */
    private void saveToVectorStoreAsync(SttData sttData, ConsultationAnalysisResult result) {
        try {
            String analysisResultJson = objectMapper.writeValueAsString(result);

            Map<String, Object> metadata = Map.of(
                    "consultationNumber", sttData.getConsultationNumber(),
                    "consultant", sttData.getConsultant(),
                    "analysisResult", analysisResultJson,
                    "consultationTime", sttData.getConsultationTime().toString()
            );

            Document document = new Document(sttData.getConsultationContent(), metadata);
            vectorStore.add(List.of(document));

            // 관계형 DB 저장은 선택적으로 처리
            try {
                ConsultationVector vector = new ConsultationVector(
                        sttData.getConsultationNumber(),
                        sttData.getConsultationContent(),
                        analysisResultJson
                );
                consultationVectorRepository.save(vector);
            } catch (Exception dbError) {
                log.warn("Failed to save to consultation_vectors table: {}",
                        sttData.getConsultationNumber(), dbError);
                // 메인 저장에는 영향 없음
            }

        } catch (Exception e) {
            log.warn("Failed to save to vector store for consultation: {}",
                    sttData.getConsultationNumber(), e);
            // 메인 저장에는 영향 없음
        }
    }

    /**
     * 실패한 상담의 상태를 FAILED로 업데이트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(SttData sttData) {
        try {
            sttData.setAnalysisStatus("FAILED");
            sttData.setUpdatedDate(LocalDateTime.now());
            sttDataRepository.save(sttData);
            log.info("Marked consultation as FAILED: {}", sttData.getConsultationNumber());
        } catch (Exception e) {
            log.error("Failed to update status to FAILED for consultation: {}",
                    sttData.getConsultationNumber(), e);
        }
    }
}