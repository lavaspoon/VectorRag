package devlava.vectorrag.repository;

import devlava.vectorrag.entity.SttData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SttDataRepository extends JpaRepository<SttData, String> {

    @Query("SELECT s FROM SttData s WHERE s.analysisStatus = 'PENDING' ORDER BY s.consultationTime ASC")
    List<SttData> findUnanalyzedData(Pageable pageable);

    @Query("SELECT s FROM SttData s WHERE s.analysisStatus = 'COMPLETED' AND s.response1 IS NOT NULL")
    List<SttData> findAnalyzedData();

    @Query("SELECT COUNT(s) FROM SttData s WHERE s.analysisStatus = 'PENDING'")
    long countPendingAnalysis();

    @Query("SELECT COUNT(s) FROM SttData s WHERE s.analysisStatus = 'COMPLETED'")
    long countCompletedAnalysis();

    @Query("SELECT COUNT(s) FROM SttData s WHERE s.analysisStatus = 'FAILED'")
    long countFailedAnalysis();
}