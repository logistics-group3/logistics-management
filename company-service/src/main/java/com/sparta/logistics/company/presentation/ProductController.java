package com.sparta.logistics.company.presentation;

import com.sparta.logistics.common.auth.CurrentUser;
import com.sparta.logistics.common.auth.UserContext;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.common.response.PageResponse;
import com.sparta.logistics.company.application.ProductQueryService;
import com.sparta.logistics.company.application.ProductService;
import com.sparta.logistics.company.presentation.dto.CreateProductRequest;
import com.sparta.logistics.company.presentation.dto.ProductResponse;
import com.sparta.logistics.company.presentation.dto.ProductSearchRequest;
import com.sparta.logistics.company.presentation.dto.UpdateProductRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품은 업체의 하위 리소스라 생성·단건·수정·삭제는 업체 경로 아래에 있다. 검색(P-02)만 업체를 가로지르므로 /api/v1/products다.
 */
@Tag(name = "상품", description = "상품 CRUD (P-01~P-05)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService productService;
    private final ProductQueryService productQueryService;

    @Operation(summary = "P-01 상품 생성")
    @PostMapping("/companies/{companyId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@CurrentUser UserContext user, @PathVariable UUID companyId,
                                               @Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.created("상품이 생성되었습니다.",
                ProductResponse.from(productService.create(user, companyId, request.toCommand())));
    }

    @Operation(summary = "P-02 상품 검색")
    @GetMapping("/products")
    public ApiResponse<PageResponse<ProductResponse>> search(@CurrentUser UserContext user,
                                                             @ModelAttribute ProductSearchRequest request,
                                                             Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                productQueryService.searchProducts(request.toCondition(), pageable), ProductResponse::from));
    }

    @Operation(summary = "P-03 상품 단건 조회")
    @GetMapping("/companies/{companyId}/products/{productId}")
    public ApiResponse<ProductResponse> get(@CurrentUser UserContext user, @PathVariable UUID companyId,
                                            @PathVariable UUID productId) {
        return ApiResponse.ok(ProductResponse.from(productQueryService.getProduct(companyId, productId)));
    }

    @Operation(summary = "P-04 상품 수정")
    @PatchMapping("/companies/{companyId}/products/{productId}")
    public ApiResponse<ProductResponse> update(@CurrentUser UserContext user, @PathVariable UUID companyId,
                                               @PathVariable UUID productId,
                                               @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(ProductResponse.from(
                productService.update(user, companyId, productId, request.toCommand())));
    }

    @Operation(summary = "P-05 상품 삭제")
    @DeleteMapping("/companies/{companyId}/products/{productId}")
    public ApiResponse<Void> delete(@CurrentUser UserContext user, @PathVariable UUID companyId,
                                    @PathVariable UUID productId) {
        productService.delete(user, companyId, productId);
        return ApiResponse.ok("상품이 삭제되었습니다.", null);
    }
}
