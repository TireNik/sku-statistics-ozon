package com.example.sku_statistics_ozon.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    private String startDate;
    private String endDate;
    private String token;
}

