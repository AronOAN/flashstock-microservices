package com.order.services;

import com.order.daos.InventoryDao;
import com.order.daos.OrderDao;
import com.order.daos.ShipmentDao;
import com.order.dtos.ReceiptEmailRequest;
import com.order.dtos.ReceiptLineItem;
import com.order.dtos.ReceiptShipmentInfo;
import com.order.models.Inventory;
import com.order.models.CustomerOrder;
import com.order.models.Shipment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceiptDataService {

    private final OrderDao orderDao;
    private final InventoryDao inventoryDao;
    private final ShipmentDao shipmentDao;

    public ReceiptEmailRequest buildFromOrderNumbers(List<String> orderNumbers, String customerSub) {
        validateOrderNumbers(orderNumbers, customerSub);
        List<CustomerOrder> orders = loadOwnedOrders(orderNumbers, customerSub);

        CustomerOrder first = orders.get(0);
        BigDecimal subtotal = BigDecimal.ZERO;
        List<ReceiptLineItem> items = new ArrayList<>();
        List<ReceiptShipmentInfo> shipments = new ArrayList<>();
        LocalDateTime latest = first.getCreatedAt();

        for (CustomerOrder order : orders) {
            if (order.getCreatedAt() != null && (latest == null || order.getCreatedAt().isAfter(latest))) {
                latest = order.getCreatedAt();
            }
            ReceiptLineItem item = lineItem(order, resolveInventory(order));
            subtotal = subtotal.add(item.getLineTotal());
            items.add(item);
            shipments.add(shipmentInfo(order));
        }

        BigDecimal shipping = BigDecimal.valueOf(3);
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(shipping).subtract(discount);

        ReceiptEmailRequest receipt = new ReceiptEmailRequest();
        receipt.setReceiptNumber("BOL-" + first.getOrderNumber());
        receipt.setCreatedAt((latest == null ? LocalDateTime.now(ZoneOffset.UTC) : latest).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        receipt.setCustomerFirstName(first.getCustomerFirstName());
        receipt.setCustomerLastName(first.getCustomerLastName());
        receipt.setCustomerEmail(first.getCustomerEmail());
        receipt.setShippingAddress(first.getShippingAddress());
        receipt.setSubtotal(subtotal);
        receipt.setShipping(shipping);
        receipt.setDiscount(discount);
        receipt.setTotal(total);
        receipt.setItems(items);
        receipt.setShipments(shipments);
        return receipt;
    }

    private void validateOrderNumbers(List<String> orderNumbers, String customerSub) {
        if (customerSub == null || customerSub.isBlank()
                || orderNumbers == null || orderNumbers.isEmpty() || orderNumbers.size() > 20
                || orderNumbers.stream().anyMatch(number -> number == null || number.isBlank())
                || orderNumbers.stream().map(String::trim).distinct().count() != orderNumbers.size()) {
            throw new IllegalArgumentException("Debes indicar al menos un numero de pedido para generar boleta.");
        }
    }

    private List<CustomerOrder> loadOwnedOrders(List<String> orderNumbers, String customerSub) {
        List<CustomerOrder> orders = new ArrayList<>();
        for (String orderNumber : orderNumbers) {
            CustomerOrder order = orderDao.findByOrderNumberAndCustomerSub(orderNumber.trim(), customerSub)
                    .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
            orders.add(order);
        }
        return orders;
    }

    private ReceiptLineItem lineItem(CustomerOrder order, Inventory inventory) {
        BigDecimal unitPrice = inventory != null && inventory.getUnitPrice() != null ? inventory.getUnitPrice() : BigDecimal.ZERO;
        int qty = order.getQuantity() == null ? 0 : order.getQuantity();
        ReceiptLineItem item = new ReceiptLineItem();
        item.setInventoryId(order.getInventoryId());
        item.setSku(order.getSku());
        item.setProductName(inventory != null && inventory.getName() != null ? inventory.getName() : order.getSku());
        item.setQuantity(qty);
        item.setUnitPrice(unitPrice);
        item.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(qty)));
        item.setOrderNumber(order.getOrderNumber());
        return item;
    }

    private ReceiptShipmentInfo shipmentInfo(CustomerOrder order) {
        Shipment shipment = shipmentDao.findByOrderNumber(order.getOrderNumber()).orElse(null);
        ReceiptShipmentInfo info = new ReceiptShipmentInfo();
        info.setOrderNumber(order.getOrderNumber());
        info.setTrackingNumber(shipment != null ? shipment.getTrackingNumber() : "N/A");
        info.setCarrier(shipment != null ? shipment.getCarrier() : "N/A");
        String courierName = "Repartidor FlashStock";
        if (shipment != null) {
            courierName = shipment.getCourierName() != null
                    ? shipment.getCourierName() : "Repartidor " + shipment.getCarrier();
        }
        info.setCourierName(courierName);
        info.setStatus(shipment != null ? shipment.getStatus() : "pendiente");
        info.setEta(shipment != null ? shipment.getEta() : "N/A");
        return info;
    }

    private Inventory resolveInventory(CustomerOrder order) {
        if (order.getInventoryId() != null) {
            Inventory byId = inventoryDao.findById(order.getInventoryId()).orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        if (order.getSku() == null || order.getSku().isBlank()) {
            return null;
        }
        return inventoryDao.findBySku(order.getSku()).orElse(null);
    }
}
