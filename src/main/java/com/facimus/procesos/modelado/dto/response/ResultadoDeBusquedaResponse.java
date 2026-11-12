package com.facimus.procesos.modelado.dto.response;

import java.util.List;

import com.facimus.procesos.modelado.model.TipoDeResultado;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR 65: algo que encontro la busqueda, con lo que hace falta para llegar a el y un fragmento de su texto. El
 * fragmento va en tramos de texto plano, unos resaltados: nunca HTML, asi que se muestra como texto, con lo que traiga.
 */
@Schema(description = "A search hit: what it is, the ids to open it and a fragment of its text with the matching "
        + "words marked")
public record ResultadoDeBusquedaResponse(
        @Schema(description = "PROCESO, ELEMENTO, DOCUMENTO or COMENTARIO") TipoDeResultado tipo,
        @Schema(example = "12") Long procesoId,
        @Schema(description = "Name of the process", example = "Credit approval") String proceso,
        @Schema(description = "The process, element or file name, or the author of the comment",
                example = "Approve credit") String titulo,
        @Schema(description = "The element; for a document or a comment, the element it is attached to",
                example = "Activity_1f3a9c0b7e42") String bpmnId,
        @Schema(description = "For an element: POOL, LANE, ACTIVIDAD, GATEWAY, EVENTO, ARCO, MENSAJE or ARTEFACTO",
                example = "ACTIVIDAD") String tipoDeElemento,
        @Schema(description = "For an element of the published version, or a thread on one: its number; empty for "
                + "the draft", example = "3") Integer version,
        @Schema(example = "40") Long documentoId,
        @Schema(example = "77") Long comentarioId,
        @Schema(description = "The first comment of the thread", example = "75") Long hiloId,
        @Schema(description = "The fragment of text around the matches, in plain-text segments; show each one as "
                + "text, with the highlighted ones marked") List<Segmento> fragmento) {

    /** Un tramo del fragmento: texto plano, resaltado si es una de las palabras buscadas. */
    @Schema(description = "A plain-text segment of the fragment")
    public record Segmento(@Schema(example = "credit") String texto, boolean resaltado) {
    }
}
