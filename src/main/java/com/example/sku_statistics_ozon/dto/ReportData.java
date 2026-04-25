package com.example.sku_statistics_ozon.dto;

import com.example.sku_statistics_ozon.model.ReportRow;
import com.example.sku_statistics_ozon.model.ReportTotals;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportData {
    private List<ReportRow> rows;
    private ReportTotals totals;
}