package com.inventory.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartItemMutationRequest {
    @NotNull
    @Min(1)
    @Max(999)
    private Integer quantity;
}
