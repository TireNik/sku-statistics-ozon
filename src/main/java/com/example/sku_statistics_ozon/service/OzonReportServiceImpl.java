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

        Set<String> knownOfferIds = allProducts.stream()
                .map(ProductReportItem::getOfferId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.info("Известных offerId из ProductReport: {}", knownOfferIds.size());

        Map<String, String> offerIdToSkuFromProducts = allProducts.stream()
                .filter(p -> p.getOfferId() != null && p.getSku() != null)
                .collect(Collectors.toMap(
                        ProductReportItem::getOfferId,
                        ProductReportItem::getSku,
                        (a, b) -> a
                ));

        Map<String, String> offerIdToTitleFromProducts = allProducts.stream()
                .filter(p -> p.getOfferId() != null && p.getTitle() != null)
                .collect(Collectors.toMap(
                        ProductReportItem::getOfferId,
                        ProductReportItem::getTitle,
                        (a, b) -> a
                ));

        log.info("Справочник из ProductReport: {} товаров", offerIdToSkuFromProducts.size());

        Map<String, OrderReportItem> offerIdToFirstOrder = new LinkedHashMap<>();
        Map<String, BigDecimal> offerIdToTotalMoneySpent = new LinkedHashMap<>();
        Map<String, BigDecimal> offerIdToCpoCost = new LinkedHashMap<>();
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

        List<CampaignClickStat> runningCampaigns = allCampaigns.stream()
                .filter(c -> STATUS_RUNNING.equalsIgnoreCase(c.getStatus()))
                .toList();

        log.info("Running кампаний: {}", runningCampaigns.size());

        List<EnrichedCampaignRow> cpcRows = new ArrayList<>();

        for (CampaignClickStat campaign : runningCampaigns) {
            String offerId = findOfferId(campaign.getTitle(), knownOfferIds);
            String sku = offerId != null
                    ? offerIdToSkuFromProducts.getOrDefault(
                    offerId,
                    offerIdToFirstOrder.containsKey(offerId)
                            ? offerIdToFirstOrder.get(offerId).getAdvSku()
                            : null)
                    : null;

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

        Set<String> cpoOfferIds = offerIdToCpoOrderCount.keySet();

        log.info("CPO товаров (уникальных offerId): {}", cpoOfferIds.size());

        List<EnrichedCampaignRow> cpoRows = new ArrayList<>();

        for (String offerId : cpoOfferIds) {
            String sku = offerIdToSkuFromProducts.get(offerId);
            String title = offerIdToTitleFromProducts.getOrDefault(
                    offerId,
                    offerIdToFirstOrder.containsKey(offerId)
                            ? offerIdToFirstOrder.get(offerId).getTitle()
                            : offerId);

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
                    .campaignId("-")
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

        List<EnrichedCampaignRow> result = new ArrayList<>();
        result.addAll(cpcRows);
        result.addAll(cpoRows);

        long withoutSku = result.stream().filter(r -> r.getAdvSku() == null).count();
        log.info("Итого строк: {} (CPC={}, CPO={}), без SKU: {}",
                result.size(), cpcRows.size(), cpoRows.size(), withoutSku);

        return result;
    }

    private String findOfferId(String campaignTitle, Set<String> knownOfferIds) {
        if (campaignTitle == null || campaignTitle.isBlank()) return null;

        String titleUpper = campaignTitle.toUpperCase();

        String firstWord = campaignTitle.trim().split("\\s+")[0];
        if (knownOfferIds.contains(firstWord)) {
            return firstWord;
        }

        String[] words = campaignTitle.trim().split("[\\s,.()/\\-]+");
        List<String> candidates = new ArrayList<>();

        for (String word : words) {
            if (knownOfferIds.contains(word)) {
                candidates.add(word);
            }
        }

        if (!candidates.isEmpty()) {
            return candidates.stream()
                    .max(Comparator.comparingInt(String::length))
                    .orElse(null);
        }

        return knownOfferIds.stream()
                .filter(id -> {
                    int idx = campaignTitle.indexOf(id);
                    if (idx < 0) return false;
                    boolean beforeOk = idx == 0
                            || !Character.isLetterOrDigit(campaignTitle.charAt(idx - 1));
                    boolean afterOk  = idx + id.length() >= campaignTitle.length()
                            || !Character.isLetterOrDigit(campaignTitle.charAt(idx + id.length()));
                    return beforeOk && afterOk;
                })
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
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