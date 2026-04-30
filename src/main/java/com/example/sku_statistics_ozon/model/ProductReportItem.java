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
Получить_отчёт_по_товарам_в_оплате_за_заказ_выбранные_товары
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductReportItem {

    @JsonProperty("SKU")
    private String sku;

    @JsonProperty("OfferID")
    private String offerId;

    @JsonProperty("Title")
    private String title;

    @JsonProperty("Category")
    private String category;

    @JsonProperty("PromotionStatus")
    private String promotionStatus;

    @JsonProperty("Price")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal price;

    @JsonProperty("Bid")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal bid;

    @JsonProperty("BidValue")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal bidValue;

    @JsonProperty("Orders")
    private String orders;

    @JsonProperty("OrdersMoney")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal ordersMoney;

    @JsonProperty("MoneySpent")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal moneySpent;

    @JsonProperty("DRR")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal drr;

    @JsonProperty("PromotionStatusChangedAt")
    private String promotionStatusChangedAt;

    @JsonProperty("MoneySpentFromCPC")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal moneySpentFromCPC;

    @JsonProperty("ordersFromCPC")
    private String ordersFromCPC;

    @JsonProperty("ordersMoneyFromCPC")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal ordersMoneyFromCPC;
}
