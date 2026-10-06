package com.inventory.services;

import com.inventory.daos.CartDao;
import com.inventory.daos.InventoryDao;
import com.inventory.dtos.CartItemResponse;
import com.inventory.models.CartItem;
import com.inventory.models.Inventory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartDao cartDao;
    private final InventoryDao inventoryDao;

    @Transactional(readOnly = true)
    public List<CartItemResponse> list(Authentication authentication) {
        String owner = owner(authentication);
        List<CartItemResponse> result = new ArrayList<>();
        for (CartItem item : cartDao.findByUserEmail(owner)) {
            inventoryDao.findBySku(item.getSku()).ifPresent(inventory -> result.add(toResponse(owner, item, inventory)));
        }
        return result;
    }

    @Transactional
    public List<CartItemResponse> add(Authentication authentication, String sku, int quantity) {
        String owner = owner(authentication);
        String normalizedSku = normalizeSku(sku);
        cartDao.lockUserCart(owner);
        cartDao.lockSku(normalizedSku);
        Inventory inventory = inventoryDao.findBySkuForUpdate(normalizedSku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        int current = cartDao.findByUserEmailAndSku(owner, normalizedSku).map(CartItem::getQuantity).orElse(0);
        saveValidated(owner, inventory, current + quantity);
        return list(authentication);
    }

    @Transactional
    public List<CartItemResponse> setQuantity(Authentication authentication, String sku, int quantity) {
        String owner = owner(authentication);
        String normalizedSku = normalizeSku(sku);
        cartDao.lockUserCart(owner);
        cartDao.lockSku(normalizedSku);
        Inventory inventory = inventoryDao.findBySkuForUpdate(normalizedSku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        saveValidated(owner, inventory, quantity);
        return list(authentication);
    }

    @Transactional
    public List<CartItemResponse> remove(Authentication authentication, String sku) {
        String owner = owner(authentication);
        cartDao.deleteByUserEmailAndSku(owner, normalizeSku(sku));
        return list(authentication);
    }

    @Transactional
    public List<CartItemResponse> clear(Authentication authentication) {
        String owner = owner(authentication);
        cartDao.deleteByUserEmail(owner);
        return List.of();
    }

    private void saveValidated(String owner, Inventory inventory, int desired) {
        if (desired < 1 || desired > 999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad inválida");
        }
        int stock = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        int reservedByOthers = Math.max(0, cartDao.sumQuantityBySkuAndUserEmailNot(inventory.getSku(), owner));
        int availableForOwner = Math.max(0, stock - reservedByOthers);
        if (desired > availableForOwner) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente");
        }
        cartDao.upsert(owner, inventory.getSku(), inventory.getId(), desired, LocalDateTime.now());
    }

    private CartItemResponse toResponse(String owner, CartItem item, Inventory inventory) {
        int stock = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        int reservedByOthers = Math.max(0, cartDao.sumQuantityBySkuAndUserEmailNot(inventory.getSku(), owner));
        return CartItemResponse.builder()
                .inventoryId(inventory.getId())
                .sku(inventory.getSku())
                .productName(inventory.getName())
                .quantity(item.getQuantity())
                .unitPrice(inventory.getUnitPrice() != null ? inventory.getUnitPrice() : inventory.getPrice())
                .imageUrl(inventory.getImageUrl())
                .availableQuantity(Math.max(0, stock - reservedByOthers))
                .build();
    }

    private String owner(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicio de sesión requerido");
        }
        String owner = authentication.getName();
        if (owner == null || owner.isBlank() || owner.length() > 200) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidad inválida");
        }
        return owner;
    }

    private String normalizeSku(String sku) {
        if (sku == null || sku.isBlank() || sku.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU inválido");
        }
        return sku.trim();
    }
}
