package com.checkai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.checkai.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
    @Select("SELECT COUNT(*) FROM task WHERE original_task_id = #{originalTaskId} AND status = 'COMPLETED'")
    int countCompletedBatches(String originalTaskId);

    @Select("SELECT total_batches FROM task WHERE original_task_id = #{originalTaskId} LIMIT 1")
    Integer getTotalBatches(String originalTaskId);
}
