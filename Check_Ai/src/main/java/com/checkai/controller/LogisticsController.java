package com.checkai.controller;

import com.checkai.entity.LogisticsOrder;
import com.checkai.entity.PageBean;
import com.checkai.entity.Result;
import com.checkai.service.LoginsticsService;
import com.checkai.util.CurrentUserHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/logistics")
public class LogisticsController {
    private final LoginsticsService loginsticsService;

    public LogisticsController(LoginsticsService loginsticsService) {
        this.loginsticsService = loginsticsService;
    }

    private String requireUserId() {
        return CurrentUserHolder.getUserId();
    }

    @GetMapping("/pagelist")
    public Result<PageBean<LogisticsOrder>> logisticsPageList(
            Integer pageNum,
            Integer pageSize,
            @RequestParam(required = false) String keyword) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("User not found, please login again");
        }

        PageBean<LogisticsOrder> logisticsOrderPageBean;
        if (keyword != null && !keyword.trim().isEmpty()) {
            logisticsOrderPageBean = loginsticsService.LogisticsListWithSearch(pageNum, pageSize, keyword, userId);
        } else {
            logisticsOrderPageBean = loginsticsService.LogisticsList(pageNum, pageSize, userId);
        }
        return Result.success(logisticsOrderPageBean);
    }

    @GetMapping("/list")
    public Result<List<LogisticsOrder>> logisticsList(
            @RequestParam(required = false) String keyword) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("User not found, please login again");
        }

        List<LogisticsOrder> logisticsOrders;
        if (keyword != null && !keyword.trim().isEmpty()) {
            logisticsOrders = loginsticsService.logisticsSelectWithSearch(keyword, userId);
        } else {
            logisticsOrders = loginsticsService.logisticsSelect(userId);
        }
        return Result.success(logisticsOrders);
    }

    @GetMapping("/list/{id}")
    public Result<LogisticsOrder> logisticsSelectById(@PathVariable Long id) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("User not found, please login again");
        }

        LogisticsOrder order = loginsticsService.logisticsSelectById(id, userId);
        if (order == null) {
            return Result.error("Order not found or access denied");
        }
        return Result.success(order);
    }

    @PostMapping("/add")
    public Result logisticsAdd(@RequestBody LogisticsOrder logisticsOrder) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("User not found, please login again");
        }

        loginsticsService.logisticsAdd(logisticsOrder, userId);
        return Result.success();
    }

    @PutMapping
    public Result logisticsUpdate(@RequestBody LogisticsOrder logisticsOrder) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("User not found, please login again");
        }

        boolean updated = loginsticsService.logisticsUpdate(logisticsOrder, userId);
        if (!updated) {
            return Result.error("Order not found or access denied");
        }
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result logisticsDelete(@PathVariable Long id) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("User not found, please login again");
        }

        boolean deleted = loginsticsService.logisticsDelete(id, userId);
        if (!deleted) {
            return Result.error("Order not found or access denied");
        }
        return Result.success();
    }
}
