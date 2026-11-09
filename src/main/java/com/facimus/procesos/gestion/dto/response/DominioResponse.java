package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.common.model.RolAcceso;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An e-mail domain of the store. Verified, whoever signs in with Google or Microsoft from it for "
        + "the first time joins the store with the default role")
public record DominioResponse(
        @Schema(example = "4") Long id,
        @Schema(example = "acme.com") String dominio,
        @Schema(example = "EDITOR") RolAcceso rolPorDefecto,
        @Schema(description = "The TXT record the domain has to publish to be verified",
                example = "bpmn-process-manager-verificacion=6f1d0c3a9b2e4f7a8c5d1e0b3a6f9c2d") String registroTxt,
        @Schema(example = "false") boolean verificado,
        @Schema(example = "2026-11-09T15:30:00") LocalDateTime verificadoEn) {
}
