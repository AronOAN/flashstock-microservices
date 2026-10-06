package com.inventory.dtos;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/** Public storefront projection. Deliberately excludes internal inventory identifiers and warehouse data. */
@Data
@Builder
public class CatalogItemResponse {
    private String sku;
    private String name;
    private String description;
    private BigDecimal unitPrice;
    private String imageUrl;
    private String category;
    private Integer availableQuantity;
}
