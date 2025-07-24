// src/main/java/devlava/dto/ConsultationAnalysisResult.java
package devlava.vectorrag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class ConsultationAnalysisResult {

    @JsonProperty("mainInquiry")
    private String mainInquiry;

    @JsonProperty("hasNudge")  // 변경됨
    private String hasNudge;

    @JsonProperty("nudgeType")  // 변경됨
    private String nudgeType;

    @JsonProperty("nudgeContent")  // 변경됨
    private String nudgeContent;

    @JsonProperty("customerResponse")  // 변경됨
    private String customerResponse;

    @JsonProperty("inappropriateNudge")  // 변경됨
    private String inappropriateNudge;

    @JsonProperty("inappropriateReason")  // 변경됨
    private String inappropriateReason;

    // 하위 호환성을 위한 별칭 메서드들 (기존 코드가 깨지지 않도록)
    public String getHasMarketing() { return hasNudge; }
    public void setHasMarketing(String hasMarketing) { this.hasNudge = hasMarketing; }

    public String getMarketingType() { return nudgeType; }
    public void setMarketingType(String marketingType) { this.nudgeType = marketingType; }

    public String getMarketingMent() { return nudgeContent; }
    public void setMarketingMent(String marketingMent) { this.nudgeContent = marketingMent; }

    public String getCustomerAgreed() { return customerResponse; }
    public void setCustomerAgreed(String customerAgreed) { this.customerResponse = customerAgreed; }

    public String getInappropriateMarketing() { return inappropriateNudge; }
    public void setInappropriateMarketing(String inappropriateMarketing) { this.inappropriateNudge = inappropriateMarketing; }

    public String getInappropriateMent() { return inappropriateReason; }
    public void setInappropriateMent(String inappropriateMent) { this.inappropriateReason = inappropriateMent; }
}