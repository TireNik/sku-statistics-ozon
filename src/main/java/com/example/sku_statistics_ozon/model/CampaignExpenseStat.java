package com.example.sku_statistics_ozon.model;

import com.example.sku_statistics_ozon.config.RuDecimalDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/*
Статистика_по_расходу_кампаний
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignExpenseStat {

    @JsonProperty("id")
    private String id;

    @JsonProperty("date")
    private String date;

    @JsonProperty("title")
    private String title;

    @JsonProperty("moneySpent")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal moneySpent;

    @JsonProperty("bonusSpent")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal bonusSpent;

    @JsonProperty("prepaymentSpent")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal prepaymentSpent;
}