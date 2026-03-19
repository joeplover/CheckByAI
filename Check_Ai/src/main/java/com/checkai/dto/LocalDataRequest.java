package com.checkai.dto;

import lombok.Data;
import java.util.List;

@Data
public class LocalDataRequest {
    private List<Long> orderIds;
    private String mode;
}
