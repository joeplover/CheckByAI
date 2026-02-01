package com.checkai.controller;

import com.checkai.entity.LogisticsOrder;
import com.checkai.entity.PageBean;
import com.checkai.entity.Result;
import com.checkai.service.LoginsticsService;
import org.springframework.web.bind.annotation.*;

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
    public Result<List<LogisticsOrder>> logisticsList(){
        List<LogisticsOrder> logisticsOrders = loginsticsService.logisticsSelect();
        return Result.success(logisticsOrders);
    }
    @GetMapping("/list/{id}")
    public Result<LogisticsOrder> logisticsSelectById(@PathVariable Long id){
        LogisticsOrder order = loginsticsService.logisticsSelectById(id);
        return Result.success(order);
    }


    @PostMapping("/add")
    public Result logisticsAdd(@RequestBody LogisticsOrder logisticsOrder){
        loginsticsService.logisticsAdd(logisticsOrder);
        return Result.success();
    }

    @PutMapping
    public Result logisticsUpdate(@RequestBody LogisticsOrder logisticsOrder){
        loginsticsService.logisticsUpdate(logisticsOrder);
        return Result.success();
    }
    @DeleteMapping("/delete/{id}")
    public Result logisticsDelete(@PathVariable Long id){
        loginsticsService.logisticsDelete(id);
        return Result.success();

    }
}
