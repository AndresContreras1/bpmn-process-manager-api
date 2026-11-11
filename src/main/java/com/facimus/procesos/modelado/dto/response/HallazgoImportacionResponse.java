package com.facimus.procesos.modelado.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 54: un elemento del archivo y lo que se hizo con el. */
@Schema(description = "An element of the file and what was done with it")
public record HallazgoImportacionResponse(
        @Schema(description = "Its id in the file", example = "Task_0x8f1") String elemento,
        @Schema(example = "Una manualTask la hace una persona: queda como tarea de usuario.") String detalle) {
}
