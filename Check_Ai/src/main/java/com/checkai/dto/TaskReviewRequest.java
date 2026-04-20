package com.checkai.dto;

import lombok.Data;

@Data
public class TaskReviewRequest {
    private String reviewStatus;
    private String riskLevel;
    private String tags;
    private String remark;
    private String reviewResult;
}
