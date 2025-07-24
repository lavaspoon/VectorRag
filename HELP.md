com.consultation
├── ConsultationAnalysisApplication.java          # Main Application Class
├──
├── config/                                       # 설정 클래스
│   ├── ConsultationAnalysisConfig.java          # Spring AI 설정
│   ├── OptimizedOllamaConfig.java               # Ollama 연결 설정
│   └── DatabaseConfig.java                      # DB 커넥션 설정
├──
├── controller/                                   # REST API Controller
│   └── ConsultationAnalysisController.java      # API 엔드포인트
├──
├── service/                                      # 비즈니스 로직
│   ├── ConsultationAnalysisService.java         # RAG 분석 서비스
│   ├── OptimizedConsultationAnalysisBatch.java  # 배치 처리 서비스
│   ├── VectorStoreInitializer.java              # Vector DB 초기화
│   └── AnalysisMetricsService.java              # 성능 메트릭 수집
├──
├── repository/                                   # 데이터 액세스
│   ├── SttDataRepository.java                   # 상담 데이터 Repository
│   └── ConsultationVectorRepository.java        # Vector 데이터 Repository
├──
├── entity/                                       # JPA Entity
│   ├── SttData.java                             # 상담 데이터 엔티티
│   └── ConsultationVector.java                  # Vector 저장 엔티티
├──
├── dto/                                          # Data Transfer Object
│   └── ConsultationAnalysisResult.java          # 분석 결과 DTO
├──
├── util/                                         # 유틸리티 클래스
│   ├── AnalysisResultParser.java                # JSON 파싱 유틸
│   └── ConsultationAnalysisLogger.java          # 로깅 유틸
├──
└── exception/                                    # 예외 처리
└── ConsultationAnalysisExceptionHandler.java # 글로벌 예외 핸들러