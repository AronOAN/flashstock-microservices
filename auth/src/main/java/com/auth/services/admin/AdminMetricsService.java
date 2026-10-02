package com.auth.services.admin;

import com.auth.daos.CartDao;
import com.auth.daos.InventoryDao;
import com.auth.daos.OrderDao;
import com.auth.daos.ShipmentDao;
import com.auth.dtos.AdminMetricsResponse;
import com.auth.dtos.AdminSkuMetricResponse;
import com.auth.models.CustomerOrder;
import com.auth.models.Shipment;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private final CartDao cartDao;
    private final InventoryDao inventoryDao;
    private final OrderDao orderDao;
    private final ShipmentDao shipmentDao;

    @Value("${app.metrics.cashflow.unit-value:12.5}")
    private double unitValue;

    public AdminMetricsResponse getAdminMetrics() {
        var inventories = inventoryDao.findAll().stream()
            .filter(entity -> !Boolean.FALSE.equals(entity.getActive()))
            .toList();
        var orders = orderDao.findAll();
        var shipments = shipmentDao.findAll();
        Map<String, Integer> reservedBySku = cartDao.reservedUnitsBySku();

        Map<String, Integer> orderedBySku = new HashMap<>();
        Map<String, CustomerOrder> orderByNumber = new HashMap<>();
        for (CustomerOrder order : orders) {
            String sku = order.getSku();
            int quantity = order.getQuantity() == null ? 0 : order.getQuantity();
            orderedBySku.merge(sku, quantity, Integer::sum);
            orderByNumber.put(order.getOrderNumber(), order);
        }

        int totalStock = inventories.stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();

        List<AdminSkuMetricResponse> skuMetrics = inventories.stream().map(inventory -> {
            int stock = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
            int ordered = orderedBySku.getOrDefault(inventory.getSku(), 0);
            int reserved = Math.max(0, reservedBySku.getOrDefault(inventory.getSku(), 0));
            int available = Math.max(stock - reserved, 0);
            String risk = riskLevel(stock, reserved);
            return AdminSkuMetricResponse.builder()
                    .sku(inventory.getSku())
                    .warehouse(inventory.getWarehouse())
                    .currentStock(stock)
                    .orderedUnits(ordered)
                    .availableUnits(available)
                    .riskLevel(risk)
                    .build();
        }).toList();

        RiskCounts risk = countRisks(skuMetrics);
        OrderCounts orderCounts = countOrders(orders);
        ShipmentCounts shipmentCounts = countShipments(shipments);
        Set<String> deliveredOrderNumbers = shipmentCounts.deliveredOrderNumbers();

        double grossCashflow = orders.stream()
                .mapToDouble(order -> (order.getQuantity() == null ? 0 : order.getQuantity()) * unitValue)
                .sum();

        double realizedCashflow = orders.stream()
                .filter(order -> deliveredOrderNumbers.contains(order.getOrderNumber()) || isCompletedOrder(order.getStatus()))
                .mapToDouble(order -> (order.getQuantity() == null ? 0 : order.getQuantity()) * unitValue)
                .sum();

        double pendingCashflow = Math.max(grossCashflow - realizedCashflow, 0);

        return AdminMetricsResponse.builder()
                .timestamp(LocalDateTime.now(ZoneOffset.UTC).toString())
                .inventorySkuCount(inventories.size())
                .totalStock(totalStock)
                .lowRiskSkuCount(risk.low())
                .criticalRiskSkuCount(risk.critical())
                .totalOrders(orders.size())
                .createdOrders(orderCounts.created())
                .completedOrders(orderCounts.completed())
                .cancelledOrders(orderCounts.cancelled())
                .totalShipments(shipments.size())
                .preparingShipments(shipmentCounts.preparing())
                .inTransitShipments(shipmentCounts.inTransit())
                .deliveredShipments(shipmentCounts.delivered())
                .grossCashflow(roundMoney(grossCashflow))
                .realizedCashflow(roundMoney(realizedCashflow))
                .pendingCashflow(roundMoney(pendingCashflow))
                .skuMetrics(skuMetrics)
                .build();
    }

    private RiskCounts countRisks(List<AdminSkuMetricResponse> metrics) {
        int low = 0;
        int critical = 0;
        for (AdminSkuMetricResponse metric : metrics) {
            if ("CRITICAL".equals(metric.getRiskLevel())) critical++;
            else if ("LOW".equals(metric.getRiskLevel())) low++;
        }
        return new RiskCounts(low, critical);
    }

    private OrderCounts countOrders(List<CustomerOrder> orders) {
        int created = 0;
        int completed = 0;
        int cancelled = 0;
        for (CustomerOrder order : orders) {
            String status = normalize(order.getStatus());
            if (status.contains("CANCEL")) cancelled++;
            else if (isCompletedOrder(status)) completed++;
            else created++;
        }
        return new OrderCounts(created, completed, cancelled);
    }

    private ShipmentCounts countShipments(List<Shipment> shipments) {
        int preparing = 0;
        int inTransit = 0;
        int delivered = 0;
        Set<String> deliveredOrderNumbers = new HashSet<>();
        for (Shipment shipment : shipments) {
            String status = normalize(shipment.getStatus());
            if (status.contains("DELIVER")) {
                delivered++;
                deliveredOrderNumbers.add(shipment.getOrderNumber());
            } else if (status.contains("TRANSIT") || status.contains("SHIPPED")) inTransit++;
            else preparing++;
        }
        return new ShipmentCounts(preparing, inTransit, delivered, deliveredOrderNumbers);
    }

    private record RiskCounts(int low, int critical) { }
    private record OrderCounts(int created, int completed, int cancelled) { }
    private record ShipmentCounts(int preparing, int inTransit, int delivered,
                                  Set<String> deliveredOrderNumbers) { }

    private boolean isCompletedOrder(String status) {
        String normalized = normalize(status);
        return normalized.contains("COMPLET") || normalized.contains("PAID") || normalized.contains("CLOSED");
    }

    private String riskLevel(int currentStock, int orderedUnits) {
        int projected = currentStock - orderedUnits;
        if (projected <= 0) {
            return "CRITICAL";
        }
        if (projected <= 5) {
            return "LOW";
        }
        return "HEALTHY";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private double roundMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
