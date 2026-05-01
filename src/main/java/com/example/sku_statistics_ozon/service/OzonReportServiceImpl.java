package com.example.sku_statistics_ozon.service;

import com.example.sku_statistics_ozon.dto.EnrichedCampaignRow;
import com.example.sku_statistics_ozon.model.CampaignClickStat;
import com.example.sku_statistics_ozon.model.OrderReportItem;
import com.example.sku_statistics_ozon.model.ProductReportItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OzonReportServiceImpl implements OzonReportService {

    private final OzonPerformanceClient client;

    private static final String STATUS_RUNNING = "running";
    private static final String INSTRUMENT_CPC = "Оплата за клик";
    private static final String INSTRUMENT_CPO = "Оплата за заказ: выбранные товары";
    private static final String SOURCE_CPC = "Кампания за клики";
    private static final String SOURCE_CPO = "Оплата за заказ";

    @Override
    public List<EnrichedCampaignRow> buildReport(String dateFrom, String dateTo, String token) {

        String postFrom = dateFrom + "T00:00:00Z";
        String postTo   = dateTo   + "T23:59:59Z";

        List<CampaignClickStat> allCampaigns = client.getCampaignProductStats(dateFrom, dateTo, token);
        List<OrderReportItem>   allOrders    = client.getOrderReport(postFrom, postTo, token);
        List<ProductReportItem> allProducts  = client.getProductReport(postFrom, postTo, token);

        log.info("Кампаний: {}, заказов: {}, товаров: {}",
                allCampaigns.size(), allOrders.size(), allProducts.size());

        // ============================================================
        // Справочник из ProductReportItem: offerId -> SKU и title
        // ProductReportItem содержит точную связь OfferID <-> SKU
        // ============================================================
        // offerId -> SKU (из отчёта по товарам — самый надёжный источник)
        Map<String, String> offerIdToSkuFromProducts = allProducts.stream()
                .filter(p -> p.getOfferId() != null && p.getSku() != null)
                .collect(Collectors.toMap(
                        ProductReportItem::getOfferId,
                        ProductReportItem::getSku,
                        (a, b) -> a
                ));

        // offerId -> title (из отчёта по товарам)
        Map<String, String> offerIdToTitleFromProducts = allProducts.stream()
                .filter(p -> p.getOfferId() != null && p.getTitle() != null)
                .collect(Collectors.toMap(
                        ProductReportItem::getOfferId,
                        ProductReportItem::getTitle,
                        (a, b) -> a
                ));

        log.info("Справочник из ProductReport: {} товаров", offerIdToSkuFromProducts.size());

        // ============================================================
        // Из OrderReportItem:
        // - offerId -> advSku (для обогащения, но менее надёжен чем ProductReport)
        // - offerId -> title (название из заказа)
        // - offerId -> суммарный moneySpent (расходы)
        // - offerId -> ID CPO кампании (из ordersSource если есть)
        // ============================================================
        // offerId -> первый заказ (для мета-данных)
        Map<String, OrderReportItem> offerIdToFirstOrder = new LinkedHashMap<>();
        // offerId -> суммарный moneySpent по ВСЕМ заказам
        Map<String, BigDecimal> offerIdToTotalMoneySpent = new LinkedHashMap<>();
        // offerId -> суммарный cost только CPO заказов
        Map<String, BigDecimal> offerIdToCpoCost = new LinkedHashMap<>();
        // offerId -> кол-во CPO заказов
        Map<String, Integer> offerIdToCpoOrderCount = new LinkedHashMap<>();

        for (OrderReportItem order : allOrders) {
            String offerId = order.getOfferId();
            if (offerId == null) continue;

            offerIdToFirstOrder.putIfAbsent(offerId, order);
            offerIdToTotalMoneySpent.merge(offerId, nvl(order.getMoneySpent()), BigDecimal::add);

            boolean isCpo = order.getOrdersSource() != null
                    && order.getOrdersSource().contains(SOURCE_CPO)
                    && !order.getOrdersSource().contains(SOURCE_CPC);

            if (isCpo) {
                offerIdToCpoCost.merge(offerId, nvl(order.getCost()), BigDecimal::add);
                offerIdToCpoOrderCount.merge(offerId, 1, Integer::sum);
            }
        }

        // ============================================================
        // Running кампании -> CPC строки
        // ============================================================
        List<CampaignClickStat> runningCampaigns = allCampaigns.stream()
                .filter(c -> STATUS_RUNNING.equalsIgnoreCase(c.getStatus()))
                .toList();

        log.info("Running кампаний: {}", runningCampaigns.size());

        List<EnrichedCampaignRow> cpcRows = new ArrayList<>();

        for (CampaignClickStat campaign : runningCampaigns) {
            String offerId = extractOfferId(campaign.getTitle());

            // SKU: приоритет ProductReport (точнее), потом OrderReport
            String sku = offerId != null
                    ? offerIdToSkuFromProducts.getOrDefault(
                    offerId,
                    offerIdToFirstOrder.containsKey(offerId)
                            ? offerIdToFirstOrder.get(offerId).getAdvSku()
                            : null)
                    : null;

            // Title: приоритет ProductReport, потом OrderReport, потом название кампании
            String title = offerId != null
                    ? offerIdToTitleFromProducts.getOrDefault(
                    offerId,
                    offerIdToFirstOrder.containsKey(offerId)
                            ? offerIdToFirstOrder.get(offerId).getTitle()
                            : campaign.getTitle())
                    : campaign.getTitle();

            BigDecimal moneySpentOrders = offerIdToTotalMoneySpent
                    .getOrDefault(offerId, BigDecimal.ZERO);

            cpcRows.add(EnrichedCampaignRow.builder()
                    .campaignId(campaign.getId())
                    .campaignTitle(campaign.getTitle())
                    .objectType(campaign.getObjectType())
                    .status(campaign.getStatus())
                    .placement(campaign.getPlacement())
                    .weeklyBudget(nvl(campaign.getWeeklyBudget()))
                    .moneySpentCpc(nvl(campaign.getMoneySpent()))
                    .views(parseIntSafe(campaign.getViews()))
                    .clicks(parseIntSafe(campaign.getClicks()))
                    .ctr(campaign.getCtr())
                    .clickPrice(campaign.getClickPrice())
                    .ordersCpc(parseIntSafe(campaign.getOrders()))
                    .ordersMoneyСpc(nvl(campaign.getOrdersMoney()))
                    .drr(campaign.getDrr())
                    .toCart(parseIntSafe(campaign.getToCart()))
                    .strategy(campaign.getStrategy())
                    .advSku(sku)
                    .offerId(offerId)
                    .title(title)
                    .instrument(INSTRUMENT_CPC)
                    .moneySpentOrders(moneySpentOrders)
                    .build());

            if (sku == null) {
                log.warn("SKU не найден: campaignId={} title='{}' offerId='{}'",
                        campaign.getId(), campaign.getTitle(), offerId);
            }
        }

        // ============================================================
        // CPO строки — из заказов где source содержит "Оплата за заказ"
        // Группируем по offerId
        // ============================================================

        // Собираем уникальные offerId с CPO заказами
        Set<String> cpoOfferIds = offerIdToCpoOrderCount.keySet();

        log.info("CPO товаров (уникальных offerId): {}", cpoOfferIds.size());

        List<EnrichedCampaignRow> cpoRows = new ArrayList<>();

        for (String offerId : cpoOfferIds) {
            // SKU и title — строго из ProductReport по offerId
            String sku = offerIdToSkuFromProducts.get(offerId);
            String title = offerIdToTitleFromProducts.getOrDefault(
                    offerId,
                    offerIdToFirstOrder.containsKey(offerId)
                            ? offerIdToFirstOrder.get(offerId).getTitle()
                            : offerId);

            // Расход CPO = сумма moneySpent ТОЛЬКО CPO заказов данного offerId
            // (не смешиваем с CPC расходами)
            BigDecimal cpoCost     = offerIdToCpoCost.getOrDefault(offerId, BigDecimal.ZERO);
            BigDecimal cpoMoneySpent = allOrders.stream()
                    .filter(o -> offerId.equals(o.getOfferId())
                            && o.getOrdersSource() != null
                            && o.getOrdersSource().contains(SOURCE_CPO)
                            && !o.getOrdersSource().contains(SOURCE_CPC))
                    .map(o -> nvl(o.getMoneySpent()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int ordersCount = offerIdToCpoOrderCount.getOrDefault(offerId, 0);

            cpoRows.add(EnrichedCampaignRow.builder()
                    .campaignId("-")        // ID CPO кампании нет в данных заказов
                    .campaignTitle("-")
                    .objectType("-")
                    .status("-")
                    .placement("-")
                    .weeklyBudget(BigDecimal.ZERO)
                    .moneySpentCpc(BigDecimal.ZERO)
                    .views(null)
                    .clicks(null)
                    .ctr(null)
                    .clickPrice(null)
                    .ordersCpc(ordersCount)
                    .ordersMoneyСpc(cpoCost)
                    .drr(calcDrr(cpoMoneySpent, cpoCost))
                    .toCart(null)
                    .strategy("-")
                    .advSku(sku)
                    .offerId(offerId)
                    .title(title)
                    .instrument(INSTRUMENT_CPO)
                    .moneySpentOrders(cpoMoneySpent)
                    .build());

            if (sku == null) {
                log.warn("CPO: SKU не найден для offerId='{}'", offerId);
            }
        }

        // ============================================================
        // Объединяем
        // ============================================================
        List<EnrichedCampaignRow> result = new ArrayList<>();
        result.addAll(cpcRows);
        result.addAll(cpoRows);

        long withoutSku = result.stream().filter(r -> r.getAdvSku() == null).count();
        log.info("Итого строк: {} (CPC={}, CPO={}), без SKU: {}",
                result.size(), cpcRows.size(), cpoRows.size(), withoutSku);

        return result;
    }

    private String extractOfferId(String campaignTitle) {
        if (campaignTitle == null || campaignTitle.isBlank()) return null;
        String first = campaignTitle.trim().split("\\s+")[0];
        if (first.matches(".*[\\d-].*")) {
            return first;
        }
        return null;
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

    private int parseIntSafe(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}