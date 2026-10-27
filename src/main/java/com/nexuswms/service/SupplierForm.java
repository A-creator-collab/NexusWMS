package com.nexuswms.service;

import jakarta.validation.constraints.*;

public class SupplierForm {
    @NotBlank @Size(max = 128) public String name;
    @Email @Size(max = 255) public String contactEmail;
    @Size(max = 32) @Pattern(regexp = "^$|^[0-9+()\\-. ]+$", message = "is not a valid phone number") public String phone;
    @Min(0) @Max(365) public int leadTimeDays = 7;
}
