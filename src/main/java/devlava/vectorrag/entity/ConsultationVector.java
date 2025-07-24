package devlava.vectorrag.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_consultation_vectors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultation_number")
    private String consultationNumber;

    @Column(name = "consultation_content", columnDefinition = "TEXT")
    private String consultationContent;

    // JSONB 타입 처리를 위한 완전한 설정
    @Column(name = "analysis_result")
    @JdbcTypeCode(SqlTypes.JSON)
    private String analysisResult;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "created_date")
    private LocalDateTime createdDate = LocalDateTime.now();

    public ConsultationVector(String consultationNumber, String consultationContent, String analysisResult) {
        this.consultationNumber = consultationNumber;
        this.consultationContent = consultationContent;
        this.analysisResult = analysisResult;
    }
}