package com.example.sku_statistics_ozon.dto;

import com.example.sku_statistics_ozon.model.CampaignProductStat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignProductStatsResponse {
    private List<CampaignProductStat> rows;
}