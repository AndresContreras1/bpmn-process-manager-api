package com.facimus.procesos.modelado.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 61: un documento y todas sus versiones, la mas nueva primero. */
@Schema(description = "A document and all its versions, newest first")
public record DocumentoDetalleResponse(DocumentoResponse documento, List<VersionDocumentoResponse> versiones) {

    public DocumentoDetalleResponse {
        versiones = List.copyOf(versiones);
    }
}
