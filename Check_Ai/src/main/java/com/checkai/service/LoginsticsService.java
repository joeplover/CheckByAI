package com.checkai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.checkai.entity.LogisticsOrder;
import com.checkai.entity.PageBean;
import com.checkai.mapper.LogisticsMapper;
import com.checkai.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class LoginsticsService extends ServiceImpl<LogisticsMapper, LogisticsOrder> {

    private static final String USER_CACHE_PREFIX = "LOGISTICS:";

    private final LogisticsMapper logisticsMapper;

    @Autowired
    private RedisUtil redisUtil;

    public LoginsticsService(LogisticsMapper logisticsMapper) {
        this.logisticsMapper = logisticsMapper;
    }

    private String userPrefix(String userId) {
        return USER_CACHE_PREFIX + userId + ":";
    }

    private void keyClear(String userId) {
        Set<String> keys = redisUtil.getS(userPrefix(userId) + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            redisUtil.delete(key);
        }
    }

    public PageBean<LogisticsOrder> LogisticsList(Integer pageNum, Integer pageSize, String userId) {
        Page<LogisticsOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LogisticsOrder::getUserId, userId)
                .orderByDesc(LogisticsOrder::getId);
        Page<LogisticsOrder> logisticsOrderPage = logisticsMapper.selectPage(page, queryWrapper);
        PageBean<LogisticsOrder> pageBean = new PageBean<>();
        pageBean.setTotal(logisticsOrderPage.getTotal());
        pageBean.setItems(logisticsOrderPage.getRecords());
        return pageBean;
    }

    public PageBean<LogisticsOrder> LogisticsListWithSearch(Integer pageNum, Integer pageSize, String keyword, String userId) {
        String safeKeyword = keyword != null ? keyword.trim() : "";
        String cacheKey = userPrefix(userId) + "PAGE:" + pageNum + ":" + pageSize + ":" + safeKeyword;
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return (PageBean<LogisticsOrder>) cached;
        }

        Page<LogisticsOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LogisticsOrder::getUserId, userId);

        if (!safeKeyword.isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like(LogisticsOrder::getWaybillNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getSourceOrderNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getTransportPlateNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getLoadingAddress, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getUnloadingAddress, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getCargoMainType, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getCargoSubType, safeKeyword)
            );
        }

        queryWrapper.orderByDesc(LogisticsOrder::getId);
        Page<LogisticsOrder> logisticsOrderPage = logisticsMapper.selectPage(page, queryWrapper);
        PageBean<LogisticsOrder> pageBean = new PageBean<>();
        pageBean.setTotal(logisticsOrderPage.getTotal());
        pageBean.setItems(logisticsOrderPage.getRecords());

        redisUtil.set(cacheKey, pageBean, 5, TimeUnit.MINUTES);
        return pageBean;
    }

    public List<LogisticsOrder> logisticsSelectWithSearch(String keyword, String userId) {
        String safeKeyword = keyword != null ? keyword.trim() : "";
        String cacheKey = userPrefix(userId) + "SEARCH:" + safeKeyword;
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return (List<LogisticsOrder>) cached;
        }

        LambdaQueryWrapper<LogisticsOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LogisticsOrder::getUserId, userId);

        if (!safeKeyword.isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like(LogisticsOrder::getWaybillNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getSourceOrderNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getTransportPlateNo, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getLoadingAddress, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getUnloadingAddress, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getCargoMainType, safeKeyword)
                    .or()
                    .like(LogisticsOrder::getCargoSubType, safeKeyword)
            );
        }

        queryWrapper.orderByDesc(LogisticsOrder::getId);
        List<LogisticsOrder> result = logisticsMapper.selectList(queryWrapper);
        redisUtil.set(cacheKey, result, 5, TimeUnit.MINUTES);
        return result;
    }

    public List<LogisticsOrder> logisticsSelect(String userId) {
        String key = userPrefix(userId) + "LIST";
        Object cached = redisUtil.get(key);
        if (cached != null) {
            return (List<LogisticsOrder>) cached;
        }

        QueryWrapper<LogisticsOrder> queryWrapper = new QueryWrapper<LogisticsOrder>()
                .eq("user_id", userId)
                .orderByDesc("id");
        List<LogisticsOrder> logisticsOrders = logisticsMapper.selectList(queryWrapper);
        redisUtil.set(key, logisticsOrders, 30, TimeUnit.MINUTES);
        return logisticsOrders;
    }

    public void logisticsAdd(LogisticsOrder logisticsOrder, String userId) {
        logisticsOrder.setUserId(userId);
        logisticsMapper.insert(logisticsOrder);
        keyClear(userId);
    }

    public boolean logisticsUpdate(LogisticsOrder logisticsOrder, String userId) {
        logisticsOrder.setUserId(userId);
        UpdateWrapper<LogisticsOrder> wrapper = new UpdateWrapper<LogisticsOrder>()
                .eq("id", logisticsOrder.getId())
                .eq("user_id", userId);
        int updated = logisticsMapper.update(logisticsOrder, wrapper);
        if (updated > 0) {
            keyClear(userId);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(2000);
                    keyClear(userId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return true;
        }
        return false;
    }

    public boolean logisticsDelete(Long id, String userId) {
        LambdaQueryWrapper<LogisticsOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsOrder::getId, id)
                .eq(LogisticsOrder::getUserId, userId);
        int deleted = logisticsMapper.delete(wrapper);
        if (deleted > 0) {
            keyClear(userId);
            return true;
        }
        return false;
    }

    public LogisticsOrder logisticsSelectById(Long id, String userId) {
        String key = userPrefix(userId) + "SelectById:" + id;
        Object value = redisUtil.get(key);
        if (value != null) {
            return (LogisticsOrder) value;
        }

        LambdaQueryWrapper<LogisticsOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogisticsOrder::getId, id)
                .eq(LogisticsOrder::getUserId, userId);
        LogisticsOrder logisticsOrder = logisticsMapper.selectOne(wrapper);
        if (logisticsOrder != null) {
            redisUtil.set(key, logisticsOrder, 30, TimeUnit.MINUTES);
        }
        return logisticsOrder;
    }
}
