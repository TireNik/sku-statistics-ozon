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
Статистика_по_кампании_Оплата_за_клик
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignClickStat {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("objectType")
    private String objectType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("placement")
    private String placement;

    @JsonProperty("weeklyBudget")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal weeklyBudget;

    @JsonProperty("budget")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal budget;

    @JsonProperty("moneySpent")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal moneySpent;

    @JsonProperty("views")
    private String views;

    @JsonProperty("clicks")
    private String clicks;

    @JsonProperty("ctr")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal ctr;

    @JsonProperty("clickPrice")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal clickPrice;

    @JsonProperty("orders")
    private String orders;

    @JsonProperty("ordersMoney")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal ordersMoney;

    @JsonProperty("drr")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal drr;

    @JsonProperty("toCart")
    private String toCart;

    @JsonProperty("strategy")
    private String strategy;
}