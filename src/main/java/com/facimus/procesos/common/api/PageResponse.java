package com.facimus.procesos.common.api;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One page of results")
public record PageResponse<T>(
        List<T> content,
        @Schema(description = "Page number, starting at 0", example = "0") int page,
        @Schema(description = "Maximum items per page", example = "10") int size,
        @Schema(example = "2") long totalElements,
        @Schema(example = "1") int totalPages) {

    public static <T> PageResponse<T> from(Page<T> pagina) {
        return new PageResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
