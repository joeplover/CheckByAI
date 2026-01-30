package com.checkai.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LogisticsOrder implements Serializable {
/*    LogisticsOrder 实体类无法序列化，这是因为在将对象存储到Redis时，对象需要实现
    Serializable 接口。根据错误堆栈，LogisticsOrder 类没有实现 Serializable 接口
        ，导致在Redis存储时出现 NotSerializableException 异常*/
    private static final long serialVersionUID = 1L;

    private Long id;                    // 主键（MyBatis 需要）
    private String waybillNo;           // 运单号
    private String sourceOrderNo;       // 来源货单
    private String loadingDistrict;     // 装货地县区
    private String loadingAddress;      // 装货地址
    private String unloadingDistrict;   // 卸货地县区
    private String unloadingAddress;    // 卸货地址
    private BigDecimal loadingWeight;   // 装货重量
    private BigDecimal unloadingWeight; // 卸货重量
    private String transportPlateNo;    // 运输车牌号
    private String cargoMainType;       // 货物大类型
    private String cargoSubType;        // 货物小类型
    private LocalDateTime loadingTime;  // 装货时间
    private LocalDateTime unloadingTime;// 卸货时间
    private String loadingWeightBillUrls;   // 装货磅单地址
    private String unloadingWeightBillUrls; // 卸货磅单地址
}