package com.example.sku_statistics_ozon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DateRangeRequest {
    @JsonProperty("from")
    private String from;
    @JsonProperty("to")
    private String to;
}