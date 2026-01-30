package com.checkai.controller;

import com.checkai.entity.LogisticsOrder;
import com.checkai.entity.PageBean;
import com.checkai.entity.Result;
import com.checkai.service.LoginsticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/logistics")
public class LogisticsController {
    LoginsticsService loginsticsService;
    public LogisticsController(LoginsticsService loginsticsService) {
        this.loginsticsService = loginsticsService;
    }

    @GetMapping("/pagelist")
    public Result<PageBean<LogisticsOrder>> logisticsPageList(Integer pageNum, Integer pageSize){
        PageBean<LogisticsOrder> logisticsOrderPageBean = loginsticsService.LogisticsList(pageNum, pageSize);
        return Result.success(logisticsOrderPageBean);

    }

    @GetMapping("/list")
    public List<LogisticsOrder> logisticsList(){
        return loginsticsService.logisticsSelect();
    }
}
