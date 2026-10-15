package com.sparta.logistics.delivery.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** D-04 요청. 새 구간 담당자 userId */
public record ReassignManagerRequest(@NotNull UUID managerId) {
}
