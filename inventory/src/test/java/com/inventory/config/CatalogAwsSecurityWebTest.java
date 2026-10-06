package com.inventory.config;

import com.inventory.controllers.CatalogController;
import com.inventory.dtos.CatalogItemResponse;
import com.inventory.services.CatalogService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("aws")
@WebMvcTest(value = CatalogController.class, properties = {
        "flashstock.cognito.issuer=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test",
        "flashstock.cognito.client-id=flashstocktestclient"
})
@Import(AwsCognitoSecurityConfig.class)
class CatalogAwsSecurityWebTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CatalogService service;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void anonymousCatalogExposesOnlyPublicProjection() throws Exception {
        when(service.findAll()).thenReturn(List.of(CatalogItemResponse.builder()
                .sku("SKU-1")
                .name("Producto")
                .description("Descripcion")
                .unitPrice(new BigDecimal("1990"))
                .imageUrl("/product.jpg")
                .category("Frutas")
                .availableQuantity(4)
                .build()));

        mvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.data[0].availableQuantity").value(4))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].supplierId").doesNotExist())
                .andExpect(jsonPath("$.data[0].stock").doesNotExist())
                .andExpect(jsonPath("$.data[0].warehouse").doesNotExist());
    }
}
