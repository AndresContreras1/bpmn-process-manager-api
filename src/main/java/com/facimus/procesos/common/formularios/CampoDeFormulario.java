package com.facimus.procesos.common.formularios;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PR 69: un campo de un formulario, tal como se guarda en el nodo y se congela en la version. Lo que no aplica a su
 * tipo va vacio: que una propiedad sobre en un tipo que no la admite lo dice {@link EsquemaDelFormulario}.
 *
 * <p>Las condiciones ({@code visibleSi} y {@code requeridoSi}) se escriben en la gramatica de D6 y leen los campos que
 * van antes en el formulario, por su nombre, y las variables del caso: {@code metodo == 'TARJETA'} o
 * {@code order.total > 100}. {@code porDefecto} es la ruta de una variable del caso cuyo valor se propone al llenarlo.
 */
@Schema(description = "A field of a typed form")
public record CampoDeFormulario(
        @Schema(description = "Name of the variable it fills: a letter, then letters, digits or _",
                example = "metodoDePago")
        @NotBlank(message = "El nombre del campo es obligatorio.")
        @Size(max = 60, message = "El nombre del campo no puede superar 60 caracteres.") String nombre,
        @Schema(description = "What the person sees", example = "Payment method")
        @NotBlank(message = "La etiqueta del campo es obligatoria.")
        @Size(max = 200, message = "La etiqueta del campo no puede superar 200 caracteres.") String etiqueta,
        @Schema(example = "LISTA") @NotNull(message = "El tipo del campo es obligatorio.") TipoDeCampo tipo,
        @Schema(description = "A hint shown under the field", example = "As it appears on the invoice")
        @Size(max = 500, message = "La ayuda del campo no puede superar 500 caracteres.") String ayuda,
        @Schema(description = "Whether it must be filled while it is visible", example = "true") Boolean requerido,
        @Schema(description = "NUMERO and MONEDA: the smallest value; LISTA_MULTIPLE: the fewest choices",
                example = "0") BigDecimal minimo,
        @Schema(description = "NUMERO and MONEDA: the largest value; LISTA_MULTIPLE: the most choices",
                example = "5000000") BigDecimal maximo,
        @Schema(description = "TEXTO and TEXTO_LARGO: the fewest characters", example = "3")
        @Min(value = 0, message = "El largo minimo no puede ser negativo.") Integer largoMinimo,
        @Schema(description = "TEXTO and TEXTO_LARGO: the most characters", example = "120")
        @Min(value = 1, message = "El largo maximo debe ser al menos 1.")
        @Max(value = 10_000, message = "El largo maximo no puede superar 10000.") Integer largoMaximo,
        @Schema(description = "TEXTO and TEXTO_LARGO: an RE2 regular expression the whole text must match",
                example = "[A-Z]{3}-\\d{4}")
        @Size(max = Patrones.LARGO_MAXIMO, message = "El patron no puede superar 200 caracteres.") String patron,
        @Schema(description = "FECHA and FECHA_HORA: the earliest value, in ISO 8601", example = "2026-01-01")
        @Size(max = 30, message = "La fecha desde no puede superar 30 caracteres.") String desde,
        @Schema(description = "FECHA and FECHA_HORA: the latest value, in ISO 8601", example = "2026-12-31")
        @Size(max = 30, message = "La fecha hasta no puede superar 30 caracteres.") String hasta,
        @Schema(description = "MONEDA: the ISO 4217 code; it decides how many decimals the amount takes",
                example = "COP")
        @Size(max = 3, message = "La moneda es un codigo ISO 4217 de tres letras.") String moneda,
        @Schema(description = "LISTA and LISTA_MULTIPLE: what can be chosen")
        @Size(max = EsquemaDelFormulario.TOPE_DE_OPCIONES, message = "Una lista admite hasta 200 opciones.")
        List<@Valid @NotNull(message = "Una opcion no puede ir vacia.") OpcionDeLista> opciones,
        @Schema(description = "A case variable whose value is offered when the form opens", example = "order.payment")
        @Size(max = 200, message = "La variable por defecto no puede superar 200 caracteres.") String porDefecto,
        @Schema(description = "D6 condition: the field (or the fields of a section) shows only when it holds",
                example = "order.total > 100")
        @Size(max = 1000, message = "La condicion de visibilidad no puede superar 1000 caracteres.") String visibleSi,
        @Schema(description = "D6 condition: the field must be filled when it holds", example = "metodo == 'TARJETA'")
        @Size(max = 1000, message = "La condicion de obligatoriedad no puede superar 1000 caracteres.")
        String requeridoSi) {

    /** Un campo con lo justo: nombre, etiqueta y tipo. Lo usan el importador y las pruebas. */
    public static CampoDeFormulario de(String nombre, String etiqueta, TipoDeCampo tipo) {
        return new CampoDeFormulario(nombre, etiqueta, tipo, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public boolean esRequerido() {
        return Boolean.TRUE.equals(requerido);
    }

    /** Las opciones de una lista; ninguna, en cualquier otro tipo. */
    public List<OpcionDeLista> lasOpciones() {
        return opciones == null ? List.of() : opciones;
    }

    public boolean esSeccion() {
        return tipo == TipoDeCampo.SECCION;
    }
}
