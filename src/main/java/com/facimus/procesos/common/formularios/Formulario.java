package com.facimus.procesos.common.formularios;

import java.util.List;
import java.util.Optional;

/**
 * PR 69: el formulario de una actividad de usuario o de un evento de inicio: sus campos, en el orden en que se
 * llenan. Vive en {@code common} por la misma razon que las condiciones: lo escribe {@code modelado}, que lo guarda, lo
 * revisa y lo exporta, y lo lee {@code ejecucion}, que valida contra el lo que llega al completar una tarea.
 */
public record Formulario(List<CampoDeFormulario> campos) {

    public Formulario {
        campos = campos == null ? List.of() : List.copyOf(campos);
    }

    /** El campo que se llama asi. */
    public Optional<CampoDeFormulario> campo(String nombre) {
        return campos.stream().filter(campo -> campo.nombre().equals(nombre)).findFirst();
    }

    public boolean vacio() {
        return campos.isEmpty();
    }

    /** Un formulario sin campos no es un formulario: se guarda como ninguno. */
    public static Optional<Formulario> siTieneCampos(Formulario formulario) {
        return Optional.ofNullable(formulario).filter(lleno -> !lleno.vacio());
    }
}
