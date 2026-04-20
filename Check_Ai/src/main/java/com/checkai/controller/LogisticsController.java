package com.checkai.controller;

import com.checkai.entity.LogisticsOrder;
import com.checkai.entity.PageBean;
import com.checkai.entity.Result;
import com.checkai.service.LoginsticsService;
import com.checkai.util.CurrentUserHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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
            return Result.error("用户不存在，请重新登录");
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
            return Result.error("用户不存在，请重新登录");
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
            return Result.error("用户不存在，请重新登录");
        }

        LogisticsOrder order = loginsticsService.logisticsSelectById(id, userId);
        if (order == null) {
            return Result.error("订单不存在或无权访问");
        }
        return Result.success(order);
    }

    @PostMapping("/add")
    public Result<Void> logisticsAdd(@RequestBody LogisticsOrder logisticsOrder) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("用户不存在，请重新登录");
        }

        loginsticsService.logisticsAdd(logisticsOrder, userId);
        return Result.success();
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("用户不存在，请重新登录");
        }
        try {
            Map<String, Object> importResult = loginsticsService.importExcel(file, userId);
            return Result.success(importResult);
        } catch (IllegalArgumentException exception) {
            return Result.error(exception.getMessage());
        } catch (Exception exception) {
            return Result.error("Excel 导入失败: " + exception.getMessage());
        }
    }

    @PutMapping
    public Result<Void> logisticsUpdate(@RequestBody LogisticsOrder logisticsOrder) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("用户不存在，请重新登录");
        }

        boolean updated = loginsticsService.logisticsUpdate(logisticsOrder, userId);
        if (!updated) {
            return Result.error("订单不存在或无权访问");
        }
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> logisticsDelete(@PathVariable Long id) {
        String userId = requireUserId();
        if (userId == null) {
            return Result.error("用户不存在，请重新登录");
        }

        boolean deleted = loginsticsService.logisticsDelete(id, userId);
        if (!deleted) {
            return Result.error("订单不存在或无权访问");
        }
        return Result.success();
    }
}
