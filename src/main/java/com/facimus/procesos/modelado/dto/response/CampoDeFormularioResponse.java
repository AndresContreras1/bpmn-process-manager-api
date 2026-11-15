package com.facimus.procesos.modelado.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.facimus.procesos.common.condiciones.ArbolDeCondicion;
import com.facimus.procesos.common.condiciones.CondicionMalEscrita;
import com.facimus.procesos.common.condiciones.EvaluadorDeCondiciones;
import com.facimus.procesos.common.formularios.CampoDeFormulario;
import com.facimus.procesos.common.formularios.OpcionDeLista;
import com.facimus.procesos.common.formularios.TipoDeCampo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR 69: un campo del formulario como lo ve quien lo dibuja o lo llena: su esquema y, D36, sus condiciones ya
 * compiladas por el servidor, para que la web solo las recorra. Una condicion que no compila, o que es demasiado larga
 * para compilarla, llega sin arbol: el diagnostico la marca como E-22 y la version no se publica con ella.
 */
@Schema(description = "A field of a typed form, with its conditions compiled by the server")
public record CampoDeFormularioResponse(
        @Schema(example = "metodoDePago") String nombre,
        @Schema(example = "Payment method") String etiqueta,
        @Schema(example = "LISTA") TipoDeCampo tipo,
        @Schema(example = "As it appears on the invoice") String ayuda,
        @Schema(example = "true") Boolean requerido,
        @Schema(example = "0") BigDecimal minimo,
        @Schema(example = "5000000") BigDecimal maximo,
        @Schema(example = "3") Integer largoMinimo,
        @Schema(example = "120") Integer largoMaximo,
        @Schema(example = "[A-Z]{3}-\\d{4}") String patron,
        @Schema(example = "2026-01-01") String desde,
        @Schema(example = "2026-12-31") String hasta,
        @Schema(example = "COP") String moneda,
        List<OpcionDeLista> opciones,
        @Schema(example = "order.payment") String porDefecto,
        @Schema(example = "order.total > 100") String visibleSi,
        @Schema(description = "visibleSi compiled; empty without a condition or when it does not compile")
        ArbolDeCondicion visibleSiArbol,
        @Schema(example = "metodo == 'TARJETA'") String requeridoSi,
        @Schema(description = "requeridoSi compiled; empty without a condition or when it does not compile")
        ArbolDeCondicion requeridoSiArbol) {

    static CampoDeFormularioResponse de(CampoDeFormulario campo) {
        return new CampoDeFormularioResponse(campo.nombre(), campo.etiqueta(), campo.tipo(), campo.ayuda(),
                campo.requerido(), campo.minimo(), campo.maximo(), campo.largoMinimo(), campo.largoMaximo(),
                campo.patron(), campo.desde(), campo.hasta(), campo.moneda(), campo.opciones(), campo.porDefecto(),
                campo.visibleSi(), arbol(campo.visibleSi()), campo.requeridoSi(), arbol(campo.requeridoSi()));
    }

    /** El esquema, sin lo que se compilo de el. */
    CampoDeFormulario campo() {
        return new CampoDeFormulario(nombre, etiqueta, tipo, ayuda, requerido, minimo, maximo, largoMinimo,
                largoMaximo, patron, desde, hasta, moneda, opciones, porDefecto, visibleSi, requeridoSi);
    }

    /**
     * Lo que pasa de {@link CampoDeFormulario#LARGO_DE_CONDICION} no se compila: llega asi por un BPMN, que no pasa por
     * la validacion de la API, y el compilador baja un nivel por cada {@code not} o parentesis. El diagnostico lo marca
     * como E-22 por largo.
     */
    private static ArbolDeCondicion arbol(String condicion) {
        if (condicion == null || condicion.isBlank() || condicion.length() > CampoDeFormulario.LARGO_DE_CONDICION) {
            return null;
        }
        try {
            return ArbolDeCondicion.de(EvaluadorDeCondiciones.compilar(condicion));
        } catch (CondicionMalEscrita _) {
            return null;
        }
    }
}
