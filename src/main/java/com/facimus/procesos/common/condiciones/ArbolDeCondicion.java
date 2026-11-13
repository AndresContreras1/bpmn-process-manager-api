package com.facimus.procesos.common.condiciones;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * D36: una condicion ya compilada, en la forma que viaja como JSON. El servidor la compila con la gramatica de D6 y la
 * web solo la recorre: asi la gramatica vive en un solo lugar y no se repite en TypeScript.
 *
 * <p>Es un solo tipo de nodo para que se lea igual desde cualquier lado: {@code Y} y {@code O} llevan dos partes,
 * {@code NO} lleva una, y la {@code COMPARACION} es la hoja, con su ruta, su operador y el valor ya con su tipo (un
 * numero, un booleano o un texto). Los parentesis no dejan nodo: solo cambian como se agrupa, y eso ya esta en el
 * arbol.
 */
@Schema(description = "A condition compiled by the server (D6 grammar) for the web to evaluate: Y and O have two "
        + "parts, NO has one, COMPARACION is the leaf")
public record ArbolDeCondicion(
        @Schema(description = "Y, O, NO or COMPARACION", example = "COMPARACION") String tipo,
        @Schema(description = "The parts of Y, O and NO; empty in a comparison") List<ArbolDeCondicion> partes,
        @Schema(description = "The variable a comparison reads", example = "metodoDePago") String ruta,
        @Schema(description = "==, !=, >, >=, < or <=", example = "==") String operador,
        @Schema(description = "The value it is compared with: a number, a boolean or a text", example = "TARJETA")
        Object valor) {

    public static final String Y = "Y";
    public static final String O = "O";
    public static final String NO = "NO";
    public static final String COMPARACION = "COMPARACION";

    /** El arbol de una condicion compilada. */
    public static ArbolDeCondicion de(Condicion condicion) {
        return switch (condicion) {
            case Condicion.Y(Condicion izquierda, Condicion derecha) -> new ArbolDeCondicion(Y, List.of(de(izquierda),
                    de(derecha)), null, null, null);
            case Condicion.O(Condicion izquierda, Condicion derecha) -> new ArbolDeCondicion(O, List.of(de(izquierda),
                    de(derecha)), null, null, null);
            case Condicion.No(Condicion negada) -> new ArbolDeCondicion(NO, List.of(de(negada)), null, null, null);
            case Condicion.Grupo(Condicion agrupada) -> de(agrupada);
            case Condicion.Comparacion(String ruta, Operador operador, Object literal, _) -> new ArbolDeCondicion(
                    COMPARACION, List.of(), ruta, operador.simbolo(), literal);
        };
    }

    /** Las variables que lee la condicion, en el orden en que aparecen y sin repetir. */
    public static List<String> rutas(Condicion condicion) {
        List<String> rutas = new ArrayList<>();
        juntarRutas(condicion, rutas);
        return List.copyOf(rutas);
    }

    private static void juntarRutas(Condicion condicion, List<String> rutas) {
        switch (condicion) {
            case Condicion.Y(Condicion izquierda, Condicion derecha) -> {
                juntarRutas(izquierda, rutas);
                juntarRutas(derecha, rutas);
            }
            case Condicion.O(Condicion izquierda, Condicion derecha) -> {
                juntarRutas(izquierda, rutas);
                juntarRutas(derecha, rutas);
            }
            case Condicion.No(Condicion negada) -> juntarRutas(negada, rutas);
            case Condicion.Grupo(Condicion agrupada) -> juntarRutas(agrupada, rutas);
            case Condicion.Comparacion(String ruta, _, _, _) when !rutas.contains(ruta) -> rutas.add(ruta);
            case Condicion.Comparacion _ -> {
                // La ruta ya estaba: cada una se lista una sola vez
            }
        }
    }
}
