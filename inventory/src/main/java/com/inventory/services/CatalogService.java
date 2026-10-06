package com.inventory.services;

import com.inventory.daos.CartDao;
import com.inventory.daos.InventoryDao;
import com.inventory.dtos.CatalogItemResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final InventoryDao inventoryDao;
    private final CartDao cartDao;

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> findAll() {
        Map<String, Integer> reserved = cartDao.reservedUnitsBySku();
        return inventoryDao.findAll().stream()
                .filter(item -> !Boolean.FALSE.equals(item.getActive()))
                .map(item -> {
                    int stock = item.getQuantity() == null ? 0 : item.getQuantity();
                    int held = Math.max(0, reserved.getOrDefault(item.getSku(), 0));
                    return CatalogItemResponse.builder()
                            .sku(item.getSku())
                            .name(item.getName())
                            .description(item.getDescription())
                            .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : item.getPrice())
                            .imageUrl(item.getImageUrl())
                            .category(item.getCategory())
                            .availableQuantity(Math.max(0, stock - held))
                            .build();
                })
                .toList();
    }
}
