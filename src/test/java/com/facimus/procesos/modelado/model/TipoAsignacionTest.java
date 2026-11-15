package com.facimus.procesos.modelado.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.facimus.procesos.common.condiciones.NombreDeVariable;
import com.facimus.procesos.common.model.RolAcceso;

/** PR 70: que ruta se lee como una variable del caso, como se llaman los datos de una tarea y quien tiene tareas. */
class TipoAsignacionTest {

    @ParameterizedTest(name = "\"{0}\" es una variable")
    @ValueSource(strings = {"a", "Z", "_", "aprobador", "_x9", "tarea.revisarPedido.aprobador", "a.b.c.d", "a1.B2"})
    @DisplayName("Tramos con letra o guion bajo al empezar, y letras, digitos o guiones bajos despues")
    void rutasQueSi(String ruta) {
        assertThat(TipoAsignacion.esRutaDeVariable(ruta)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" no es una variable")
    @NullAndEmptySource
    @ValueSource(strings = {"9", "1a", "a.", ".a", "a..b", "a-b", "a b", "ñandu", "a.9", "a.b$", "{a}", "a/b", "a@b",
        "a[b", "a`b", "a:b"})
    @DisplayName("Lo demas no: vacio, un tramo vacio, un digito al empezar o un signo")
    void rutasQueNo(String ruta) {
        assertThat(TipoAsignacion.esRutaDeVariable(ruta)).isFalse();
    }

    @Test
    @DisplayName("Hasta 200 caracteres, lo que cabe en su columna")
    void largo() {
        assertThat(TipoAsignacion.esRutaDeVariable("a".repeat(TipoAsignacion.LARGO_DEL_CAMPO))).isTrue();
        assertThat(TipoAsignacion.esRutaDeVariable("a".repeat(TipoAsignacion.LARGO_DEL_CAMPO + 1))).isFalse();
    }

    @Test
    @DisplayName("Los datos de una tarea quedan bajo tarea.<su nombre en camello>")
    void rutaDeLaTarea() {
        assertThat(NombreDeVariable.deLaTarea("Review the order", "aprobador"))
                .isEqualTo("tarea.reviewTheOrder.aprobador");
        assertThat(NombreDeVariable.enCamello("  pick & pack: items ")).isEqualTo("pickPackItems");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RolAcceso.class)
    @DisplayName("Tiene tareas quien las puede completar: no quien solo lee")
    void completaTareas(RolAcceso rol) {
        assertThat(rol.completaTareas()).isEqualTo(rol != RolAcceso.SOLO_LECTURA);
    }
}
