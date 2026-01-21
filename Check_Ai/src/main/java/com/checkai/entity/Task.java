package com.checkai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("task")
public class Task implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String id;
    private String taskId;
    private String originalTaskId;
    private String userId;
    private Integer batchNumber;
    private Integer totalBatches;
    private String status;
    private String dataContent;
    private Date createTime;
    private Date updateTime;
    private Date timeoutTime;
    private Integer progress;
    private Integer totalProgress;
}
