package com.inventory.dtos;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartItemResponse {
    private Long inventoryId;
    private String sku;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String imageUrl;
    private Integer availableQuantity;
}
