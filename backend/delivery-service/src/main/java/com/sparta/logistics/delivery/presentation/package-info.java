/**
 * Presentation: Controller, Request/Response DTO.
 * <ul>
 *   <li>외부 API: {@code /api/v1/...}, 응답은 {@code ApiResponse} 래퍼</li>
 *   <li>내부 API: {@code Internal{Resource}Controller}, {@code /internal/...}, 래퍼 없이 DTO 반환</li>
 *   <li>역할 확인은 {@code @RequireRole}, 사용자 정보는 {@code @CurrentUser UserContext}</li>
 * </ul>
 * 규칙: .claude/rules/controller.md
 */
package com.sparta.logistics.delivery.presentation;
