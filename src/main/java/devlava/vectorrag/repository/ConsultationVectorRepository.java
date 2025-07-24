package devlava.vectorrag.repository;

import devlava.vectorrag.entity.ConsultationVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ConsultationVectorRepository extends JpaRepository<ConsultationVector, Long> {

    ConsultationVector findByConsultationNumber(String consultationNumber);
    void deleteByConsultationNumber(String consultationNumber);

    // JSONB 저장을 위한 네이티브 쿼리 추가
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO tb_consultation_vectors (consultation_number, consultation_content, analysis_result, created_date) " +
            "VALUES (:consultationNumber, :consultationContent, CAST(:analysisResult AS jsonb), CURRENT_TIMESTAMP)",
            nativeQuery = true)
    void saveWithJsonb(@Param("consultationNumber") String consultationNumber,
                       @Param("consultationContent") String consultationContent,
                       @Param("analysisResult") String analysisResult);
}