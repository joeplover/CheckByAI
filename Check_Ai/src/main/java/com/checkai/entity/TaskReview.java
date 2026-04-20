package com.checkai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@Entity
@TableName("task_review")
@jakarta.persistence.Table(name = "task_review")
public class TaskReview implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @TableId(type = IdType.AUTO)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "review_status", nullable = false, length = 32)
    private String reviewStatus;

    @Column(name = "risk_level", length = 32)
    private String riskLevel;

    @Lob
    @Column(name = "tags")
    private String tags;

    @Lob
    @Column(name = "remark")
    private String remark;

    @Lob
    @Column(name = "review_result")
    private String reviewResult;

    @Column(name = "reviewer", length = 64)
    private String reviewer;

    @Column(name = "review_time")
    private Date reviewTime;

    @Column(name = "create_time", nullable = false)
    private Date createTime;

    @Column(name = "update_time", nullable = false)
    private Date updateTime;

    @PrePersist
    public void prePersist() {
        Date now = new Date();
        if (createTime == null) {
            createTime = now;
        }
        if (updateTime == null) {
            updateTime = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = new Date();
    }
}
