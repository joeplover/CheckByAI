package com.checkai.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LogisticsOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String userId;
    private String waybillNo;
    private String sourceOrderNo;
    private String loadingDistrict;
    private String loadingAddress;
    private String unloadingDistrict;
    private String unloadingAddress;
    private BigDecimal loadingWeight;
    private BigDecimal unloadingWeight;
    private String transportPlateNo;
    private String cargoMainType;
    private String cargoSubType;
    private LocalDateTime loadingTime;
    private LocalDateTime unloadingTime;
    private String loadingWeightBillUrls;
    private String unloadingWeightBillUrls;
}
