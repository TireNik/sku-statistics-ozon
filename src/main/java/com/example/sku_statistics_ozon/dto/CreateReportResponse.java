package com.example.sku_statistics_ozon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportResponse {
    @JsonProperty("UUID")
    private String uuid;
    private String vendor;
}
