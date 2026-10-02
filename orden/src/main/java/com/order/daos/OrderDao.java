package com.order.daos;

import com.order.models.CustomerOrder;
import com.order.repos.OrderCustomerShippingProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderDao extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByOrderNumber(String orderNumber);
    Optional<CustomerOrder> findByOrderNumberAndCustomerSub(String orderNumber, String customerSub);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.orderNumber = :orderNumber and o.customerSub = :customerSub")
    Optional<CustomerOrder> lockOwnedOrder(@Param("orderNumber") String orderNumber, @Param("customerSub") String customerSub);
    boolean existsByOrderNumber(String orderNumber);

    @Query(value = """
        SELECT o.id AS orderId,
           o.order_number AS orderNumber,
           o.status AS orderStatus,
           s.tracking_number AS shipmentTrackingNumber,
           s.status AS shipmentStatus,
           s.carrier AS carrier,
           o.customer_first_name AS customerFirstName,
           o.customer_last_name AS customerLastName,
           o.customer_email AS customerEmail,
           o.shipping_address AS shippingAddress
        FROM orders o
        LEFT JOIN shipments s ON s.order_number = o.order_number
        WHERE (:status IS NULL OR o.status = :status)
        ORDER BY o.created_at DESC
        """, nativeQuery = true)
    List<OrderCustomerShippingProjection> findCustomerOrderShipping(@Param("status") String status);

    @Query(value = """
        SELECT o.id AS orderId,
           o.order_number AS orderNumber,
           o.status AS orderStatus,
           s.tracking_number AS shipmentTrackingNumber,
           s.status AS shipmentStatus,
           s.carrier AS carrier,
           o.customer_first_name AS customerFirstName,
           o.customer_last_name AS customerLastName,
           o.customer_email AS customerEmail,
           o.shipping_address AS shippingAddress
        FROM orders o
        LEFT JOIN shipments s ON s.order_number = o.order_number
        WHERE o.customer_sub = :customerSub
          AND (:status IS NULL OR o.status = :status)
        ORDER BY o.created_at DESC
        """, nativeQuery = true)
    List<OrderCustomerShippingProjection> findCustomerOrderShippingBySub(
        @Param("customerSub") String customerSub,
        @Param("status") String status
    );
}
