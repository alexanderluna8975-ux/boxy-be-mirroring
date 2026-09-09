package com.boxy.boxy.modules.administration.controller;

import com.boxy.boxy.core.response.ApiResponse;
import com.boxy.boxy.modules.administration.dto.CompanyProfileDto;
import com.boxy.boxy.modules.administration.dto.UpdateCompanyProfileRequest;
import com.boxy.boxy.modules.administration.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/administration/company", "/api/v1/company/profile"})
@RequiredArgsConstructor
@Tag(name = "Administration - Company", description = "Endpoints for managing company identity, branding, and feature flags")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @Operation(summary = "Get active company profile, branding and business capabilities")
    public ResponseEntity<ApiResponse<CompanyProfileDto>> getCompanyProfile() {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getCompanyProfile()));
    }

    @PutMapping
    @Operation(summary = "Update active company profile, branding and business capabilities")
    public ResponseEntity<ApiResponse<CompanyProfileDto>> updateCompanyProfile(
            @Valid @RequestBody UpdateCompanyProfileRequest request) {
        CompanyProfileDto updated = companyService.updateCompanyProfile(request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Company profile updated successfully"));
    }
}
