package com.order.services;

import com.order.daos.InventoryDao;
import com.order.daos.OrderDao;
import com.order.daos.ShipmentDao;
import com.order.dtos.OrderCustomerShippingResponse;
import com.order.dtos.OrderRequest;
import com.order.dtos.OrderResponse;
import com.order.models.Inventory;
import com.order.models.CustomerOrder;
import com.order.models.Shipment;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final Set<String> ALLOWED_ORDER_STATUS = Set.of("proceso", "enviado", "cancelado", "completado");

    private final OrderDao dao;
    private final InventoryDao inventoryDao;
    private final ShipmentDao shipmentDao;

    public List<OrderResponse> findAll() {
        return dao.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderResponse create(OrderRequest request, String customerSub, String verifiedEmail) {
        if (customerSub == null || customerSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Inventory inventory = resolveInventoryForUpdate(request.getInventoryId(), request.getSku());
        if (inventory == null) {
            throw new IllegalArgumentException("No existe inventario para SKU: " + request.getSku());
        }

        if (Boolean.FALSE.equals(inventory.getActive())) {
            throw new IllegalStateException("El producto no esta disponible para la venta: " + request.getSku());
        }

        int currentStock = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        int requestedUnits = request.getQuantity() == null ? 0 : request.getQuantity();

        if (requestedUnits <= 0) {
            throw new IllegalArgumentException("La cantidad del pedido debe ser mayor a 0");
        }

        if (currentStock < requestedUnits) {
            throw new IllegalStateException("Stock insuficiente para SKU " + request.getSku() + ". Disponible: " + currentStock);
        }

        try {
            inventory.setQuantity(currentStock - requestedUnits);
            inventoryDao.save(inventory);
        } catch (OptimisticLockingFailureException | PessimisticLockingFailureException ex) {
            throw new IllegalStateException("Otro usuario compro este producto al mismo tiempo. Refresca e intenta nuevamente.", ex);
        }

        CustomerOrder order = CustomerOrder.builder()
                .orderNumber(null)
                .sku(inventory.getSku())
                .inventoryId(inventory.getId())
                .quantity(request.getQuantity())
                .customerFirstName(request.getCustomerFirstName())
                .customerLastName(request.getCustomerLastName())
                .customerSub(customerSub)
                .customerEmail(verifiedEmail)
                .shippingAddress(request.getShippingAddress())
                .status("proceso")
                .createdAt(LocalDateTime.now())
                .build();

        for (int attempt = 0; attempt < 5; attempt++) {
            order.setOrderNumber(generateUniqueOrderNumber());
            try {
                return toResponse(dao.save(order));
            } catch (DataIntegrityViolationException ex) {
                if (!isDuplicateKey(ex)) {
                    throw ex;
                }
            }
        }

        throw new IllegalStateException("No fue posible generar un orderNumber unico. Intenta nuevamente.");
    }

    public OrderResponse findByOrderNumber(String orderNumber, String customerSub, boolean admin) {
        if (customerSub == null || customerSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        CustomerOrder order = (admin ? dao.findByOrderNumber(orderNumber)
                : dao.findByOrderNumberAndCustomerSub(orderNumber, customerSub))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateStatus(String orderNumber, String status) {
        CustomerOrder order = dao.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("No existe pedido: " + orderNumber));
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();
        if (!ALLOWED_ORDER_STATUS.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Estado de pedido invalido. Usa: proceso, enviado, cancelado o completado");
        }
        order.setStatus(normalizedStatus);
        return toResponse(dao.save(order));
    }

    public List<OrderCustomerShippingResponse> findCustomerOrderShipping(String status) {
        return dao.findCustomerOrderShipping(status).stream()
                .map(row -> OrderCustomerShippingResponse.builder()
                        .orderId(row.getOrderId())
                        .orderNumber(row.getOrderNumber())
                        .orderStatus(row.getOrderStatus())
                        .shipmentTrackingNumber(row.getShipmentTrackingNumber())
                        .shipmentStatus(row.getShipmentStatus())
                        .carrier(row.getCarrier())
                        .customerFirstName(row.getCustomerFirstName())
                        .customerLastName(row.getCustomerLastName())
                        .customerEmail(row.getCustomerEmail())
                        .shippingAddress(row.getShippingAddress())
                        .build())
                .toList();
    }

    @Transactional
    public OrderResponse confirmReceived(String orderNumber, String customerSub) {
        if (customerSub == null || customerSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        CustomerOrder order = dao.lockOwnedOrder(orderNumber, customerSub)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!"enviado".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido aun no puede confirmarse");
        }

        order.setStatus("completado");
        dao.save(order);

        Shipment shipment = shipmentDao.findByOrderNumber(order.getOrderNumber()).orElse(null);
        if (shipment != null) {
            shipment.setStatus("completado");
            shipment.setLastUpdate(LocalDateTime.now());
            shipmentDao.save(shipment);
        }

        return toResponse(order);
    }

    public List<OrderCustomerShippingResponse> findMyOrderHistory(String customerSub, String status) {
                if (customerSub == null || customerSub.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                }
                return dao.findCustomerOrderShippingBySub(customerSub, status).stream()
                    .map(row -> OrderCustomerShippingResponse.builder()
                        .orderId(row.getOrderId())
                        .orderNumber(row.getOrderNumber())
                        .orderStatus(row.getOrderStatus())
                        .shipmentTrackingNumber(row.getShipmentTrackingNumber())
                        .shipmentStatus(row.getShipmentStatus())
                        .carrier(row.getCarrier())
                        .customerFirstName(row.getCustomerFirstName())
                        .customerLastName(row.getCustomerLastName())
                        .customerEmail(row.getCustomerEmail())
                        .shippingAddress(row.getShippingAddress())
                        .build())
                    .toList();
                }

    private OrderResponse toResponse(CustomerOrder order) {
        return OrderResponse.builder()
                .orderNumber(order.getOrderNumber())
                .inventoryId(order.getInventoryId())
                .sku(order.getSku())
                .quantity(order.getQuantity())
                .customerFirstName(order.getCustomerFirstName())
                .customerLastName(order.getCustomerLastName())
                .customerEmail(order.getCustomerEmail())
                .shippingAddress(order.getShippingAddress())
                .status(order.getStatus())
                .build();
    }

    private Inventory resolveInventoryForUpdate(Long inventoryId, String sku) {
        if (inventoryId != null) {
            Inventory byId = inventoryDao.findByIdForUpdate(inventoryId).orElse(null);
            if (byId != null) {
                return byId;
            }
        }

        if (sku == null || sku.isBlank()) {
            return null;
        }

        return inventoryDao.findBySkuForUpdate(sku.trim()).orElse(null);
    }

    private String generateUniqueOrderNumber() {
        for (int i = 0; i < 8; i++) {
            String candidate = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
            if (!dao.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean isDuplicateKey(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
        if (msg == null) {
            return false;
        }
        String normalized = msg.toLowerCase();
        return normalized.contains("duplicate key") || normalized.contains("sqlstate: 23505");
    }
}
