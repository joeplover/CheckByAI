package com.checkai.dto;

import lombok.Data;

import java.util.Date;

@Data
public class TaskReviewVO {
    private String taskId;
    private String taskStatus;
    private Integer taskProgress;
    private Date taskCreateTime;
    private Date taskUpdateTime;
    private String reviewStatus;
    private String riskLevel;
    private String tags;
    private String remark;
    private String reviewResult;
    private String reviewer;
    private Date reviewTime;
    private Date reviewUpdateTime;
}
