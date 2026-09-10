package com.boxy.boxy.modules.administration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBranchRequest {
    @NotBlank(message = "Branch code is required")
    private String code;

    @NotBlank(message = "Branch name is required")
    private String name;

    private String address;
    private String phone;
    private String email;

    // A primitive-boolean field named isMain gets a getter isMain() (property "main") but a
    // setter setIsMain() (property "isMain") — the two disagree on the JSON name. @JsonProperty
    // pins both to "isMain" so the request body and any echoed response use the same key.
    @JsonProperty("isMain")
    private boolean isMain;
}
