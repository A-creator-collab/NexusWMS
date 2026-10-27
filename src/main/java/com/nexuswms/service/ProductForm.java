package com.nexuswms.service;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Form/DTO for creating and editing a product (validated in the UI and again in ProductService). */
public class ProductForm {
    @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._\\- ]+$", message = "may only contain letters, digits, space, '.', '_' and '-'")
    public String sku;
    @NotBlank @Size(max = 160) public String name;
    @Size(max = 64) public String category;
    @Size(max = 2000) public String description;
    @NotNull @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2) public BigDecimal unitPrice;
    @Min(0) @Max(10_000_000) public int reorderPoint;
    @Min(0) @Max(10_000_000) public int safetyStock;
    public Long supplierId;
    @Min(0) @Max(10_000_000) public int initialQuantity;
    @Size(max = 32) @Pattern(regexp = "^$|^[A-Za-z0-9\\-]+$", message = "may only contain letters, digits and '-'") public String bin;
}
