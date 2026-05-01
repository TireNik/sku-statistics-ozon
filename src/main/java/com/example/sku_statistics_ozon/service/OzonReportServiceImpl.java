package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.CampaignReportWrapper;
import com.example.sku_statistics_ozon.dto.EnrichedCampaignRow;
import com.example.sku_statistics_ozon.dto.ProductStatRow;
import com.example.sku_statistics_ozon.model.CampaignClickStat;
import com.example.sku_statistics_ozon.model.OrderReportItem;
import com.example.sku_statistics_ozon.model.ProductReportItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OzonReportServiceImpl implements OzonReportService {

    private final OzonPerformanceClient client;

    @Override
    public List<EnrichedCampaignRow> buildReport(
            String dateFrom, String dateTo, String token) {

        List<CampaignClickStat> campaigns =
                client.getCampaignProductStats(dateFrom, dateTo, token);

        log.info("Кампаний получено: {}", campaigns.size());

        List<CampaignClickStat> skuCampaigns = campaigns.stream()
                .filter(c -> "SKU".equalsIgnoreCase(c.getObjectType()))
                .filter(c -> "running".equalsIgnoreCase(c.getStatus()))
                .toList();

        log.info("SKU кампаний со статусом running: {}", skuCampaigns.size());
        List<String> campaignIds = skuCampaigns.stream()
                .map(CampaignClickStat::getId)
                .toList();

        Map<String, CampaignReportWrapper> reports =
                client.getCampaignReport(campaignIds, dateFrom, dateTo, token);

        List<EnrichedCampaignRow> result = new ArrayList<>();

        for (CampaignClickStat campaign : skuCampaigns) {

            CampaignReportWrapper wrapper = reports.get(campaign.getId());
            if (wrapper == null || wrapper.getReport() == null) {
                continue;
            }

            List<ProductStatRow> rows = wrapper.getReport().getRows();

            if (rows == null || rows.isEmpty()) {
                rows = List.of(wrapper.getReport().getTotals());
            }

            for (ProductStatRow row : rows) {

                result.add(EnrichedCampaignRow.builder()
                        .advSku(row.getSku())
                        .campaignId(campaign.getId())
                        .campaignTitle(campaign.getTitle())
                        .placement(campaign.getPlacement())

                        .moneySpentCpc(nvl(row.getMoneySpent()))
                        .ordersMoneyСpc(nvl(row.getOrdersMoney()))

                        .ordersCpc(parseInt(row.getOrders()))
                        .views(parseInt(row.getViews()))
                        .clicks(parseInt(row.getClicks()))
                        .toCart(parseInt(row.getToCart()))

                        .ctr(row.getCtr())
                        .clickPrice(row.getAvgBid())

                        .title(row.getTitle())

                        .objectType(campaign.getObjectType())
                        .status(campaign.getStatus())
                        .weeklyBudget(campaign.getWeeklyBudget())
                        .strategy(campaign.getStrategy())

                        .build());
            }
        }

        log.info("Строк после обогащения: {}", result.size());
        return result;
    }
        @Override
    public List<EnrichedCampaignRow> buildOrderReport(
            String dateFrom, String dateTo, String token) {

        String postFrom = dateFrom + "T00:00:00Z";
        String postTo = dateTo + "T00:00:00Z";

        List<OrderReportItem> allOrders = client.getOrderReport(postFrom, postTo, token);
        List<ProductReportItem> allProducts = client.getProductReport(postFrom, postTo, token);

        log.info("Заказов получено: {}", allOrders.size());
        log.info("Товаров получены: {}", allProducts.size());

        // Группируем по advSku
        Map<String, List<OrderReportItem>> byAdvSku = allOrders.stream()
                .filter(o -> o.getAdvSku() != null)
                .collect(Collectors.groupingBy(
                        OrderReportItem::getAdvSku,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<String, ProductReportItem> bySku = allProducts.stream()
                .filter(p -> p.getSku() != null)
                .collect(Collectors.toMap(
                        ProductReportItem::getSku,
                        Function.identity()
                ));

        log.info("количество с advSku 826917711 {}", byAdvSku.get("826917711").size());

        log.info("Уникальных advSku: {}", byAdvSku.size());

        List<EnrichedCampaignRow> result = new ArrayList<>();

        for (Map.Entry<String, List<OrderReportItem>> entry : byAdvSku.entrySet()) {
            String advSku = entry.getKey();
            List<OrderReportItem> orders = entry.getValue();
            ProductReportItem prod = bySku.get(advSku);

            // Суммируем moneySpent — это расход
            BigDecimal totalMoneySpent = orders.stream()
                    .map(o -> nvl(o.getMoneySpent()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Суммируем cost — это продажи
            BigDecimal totalCost = orders.stream()
                    .map(o -> nvl(o.getCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // ДРР
            BigDecimal drr = calcDrr(totalMoneySpent, totalCost);

            // Уникальные источники
            Set<String> sources = orders.stream()
                    .map(OrderReportItem::getOrdersSource)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            String ordersSource = sources.size() == 1
                    ? sources.iterator().next()
                    : "Смешанный: " + String.join(" + ", sources);

            result.add(EnrichedCampaignRow.builder()
                            .advSku(advSku)
                            .campaignTitle(prod.getTitle())
                            .instrument(ordersSource)
                            .moneySpentOrders(totalMoneySpent)
                            .drr(drr)
                            .ordersMoneyСpc(totalCost)
                            .ordersMoneyСpc(prod.getOrdersMoney())
                            .ordersCpc(Integer.valueOf(prod.getOrders()))
                    .build());
        }

        log.info("Строк в отчёте: {}", result.size());
        return result;
    }

    private Integer parseInt(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal calcDrr(BigDecimal spent, BigDecimal sales) {
        if (spent == null || sales == null
                || sales.compareTo(BigDecimal.ZERO) == 0) return null;
        return spent.divide(sales, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

}