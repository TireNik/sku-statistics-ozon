package com.example.sku_statistics_ozon.dto;

import com.example.sku_statistics_ozon.config.RuDecimalDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductStatRow {

    private String sku;
    private String title;

    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal moneySpent;

    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal ordersMoney;

    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal ctr;

    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal avgBid;

    private String views;
    private String clicks;
    private String toCart;
    private String orders;
}