package com.facimus.procesos.modelado.dto.response;

import java.util.List;

import com.facimus.procesos.common.formularios.Formulario;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR 69: el formulario de una actividad de usuario o de un inicio. Viaja en el diagrama, asi que al publicar se congela
 * en la version con sus condiciones compiladas: lo que el motor y la web leen despues es esto, no el borrador.
 */
@Schema(description = "The typed form of a user task or of a start event")
public record FormularioResponse(List<CampoDeFormularioResponse> campos) {

    /** El formulario para el API, o nulo si no hay ninguno. */
    public static FormularioResponse de(Formulario formulario) {
        return Formulario.siTieneCampos(formulario)
                .map(lleno -> new FormularioResponse(lleno.campos().stream().map(CampoDeFormularioResponse::de)
                        .toList()))
                .orElse(null);
    }

    /** El esquema que se guardo, para validar contra el. */
    public Formulario formulario() {
        return new Formulario(campos == null ? List.of()
                : campos.stream().map(CampoDeFormularioResponse::campo).toList());
    }
}
