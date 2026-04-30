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
Получить_отчёт_по_заказам_в_оплате_за_заказ_выбранные_товары
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderReportItem {

    @JsonProperty("date")
    private String date;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("orderNumber")
    private String orderNumber;

    @JsonProperty("sku")
    private String sku;

    @JsonProperty("advSku")
    private String advSku;

    @JsonProperty("offerId")
    private String offerId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("ordersSource")
    private String ordersSource;

    @JsonProperty("quantity")
    private String quantity;

    @JsonProperty("price")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal price;

    @JsonProperty("cost")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal cost;

    @JsonProperty("bid")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal bid;

    @JsonProperty("bidValue")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal bidValue;

    @JsonProperty("moneySpent")
    @JsonDeserialize(using = RuDecimalDeserializer.class)
    private BigDecimal moneySpent;
}
