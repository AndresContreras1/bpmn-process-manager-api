package com.facimus.procesos.modelado.dto.request;

import java.util.List;

import com.facimus.procesos.common.formularios.CampoDeFormulario;
import com.facimus.procesos.common.formularios.EsquemaDelFormulario;
import com.facimus.procesos.common.formularios.Formulario;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PR 69: el formulario entero de un nodo, que reemplaza al que tenia. Sin campos, el nodo se queda sin formulario. */
@Schema(description = "The whole form of a user task or a start event; an empty list removes it")
public record FormularioRequest(
        @Schema(description = "The fields, in the order they are filled")
        @NotNull(message = "Los campos son obligatorios; una lista vacia quita el formulario.")
        @Size(max = EsquemaDelFormulario.TOPE_DE_CAMPOS, message = "Un formulario admite hasta 100 campos.")
        List<@Valid @NotNull(message = "Un campo no puede ir vacio.") CampoDeFormulario> campos,
        @Schema(description = "Version of the node read with the last GET; if someone saved a change since, it answers "
                + "409", example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {

    public Formulario formulario() {
        return new Formulario(campos);
    }
}
