package devlava.vectorrag.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_stt_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SttData {

    @Id
    @Column(name = "consultation_number")
    private String consultationNumber;

    @Column(name = "consultant")
    private String consultant;

    @Column(name = "consultation_content", columnDefinition = "TEXT")
    private String consultationContent;

    @Column(name = "consultation_time")
    private LocalDateTime consultationTime;

    // 분석 결과 컬럼들
    @Column(name = "response1", columnDefinition = "TEXT")
    private String response1; // 고객의 주요 문의 내용

    @Column(name = "response2", length = 1)
    private String response2; // 마케팅 권유 내용 유무 (Y/N)

    @Column(name = "response3", length = 100)
    private String response3; // 마케팅 유형

    @Column(name = "response4", columnDefinition = "TEXT")
    private String response4; // 마케팅 멘트

    @Column(name = "response5", length = 1)
    private String response5; // 마케팅 권유에 고객 동의 여부 (Y/N)

    @Column(name = "response6", length = 1)
    private String response6; // 부적절한 마케팅 응대 여부 (Y/N)

    @Column(name = "response7", columnDefinition = "TEXT")
    private String response7; // 부적절한 상담사 멘트

    @Column(name = "analysis_status", length = 20)
    private String analysisStatus = "PENDING";

    @Column(name = "analysis_date")
    private LocalDateTime analysisDate;

    @Column(name = "created_date")
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "updated_date")
    private LocalDateTime updatedDate = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedDate = LocalDateTime.now();
    }
}
