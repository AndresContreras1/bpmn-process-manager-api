package com.facimus.procesos.common.formularios;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Arma campos de formulario para las pruebas, con lo justo y lo que cada una cambie: {@code campo("monto",
 * MONEDA).minimo("0").requerido().armar()}. La etiqueta sale del nombre, salvo que se diga otra.
 */
public final class CampoArmado {

    private String nombre;
    private String etiqueta;
    private TipoDeCampo tipo;
    private String ayuda;
    private Boolean requerido;
    private BigDecimal minimo;
    private BigDecimal maximo;
    private Integer largoMinimo;
    private Integer largoMaximo;
    private String patron;
    private String desde;
    private String hasta;
    private String moneda;
    private List<OpcionDeLista> opciones;
    private String porDefecto;
    private String visibleSi;
    private String requeridoSi;

    private CampoArmado(String nombre, TipoDeCampo tipo) {
        this.nombre = nombre;
        this.etiqueta = "Etiqueta de " + nombre;
        this.tipo = tipo;
    }

    public static CampoArmado campo(String nombre, TipoDeCampo tipo) {
        return new CampoArmado(nombre, tipo);
    }

    public static Formulario formulario(CampoArmado... campos) {
        return new Formulario(Arrays.stream(campos).map(CampoArmado::armar).toList());
    }

    public CampoArmado nombre(String otro) {
        nombre = otro;
        return this;
    }

    public CampoArmado etiqueta(String otra) {
        etiqueta = otra;
        return this;
    }

    public CampoArmado ayuda(String texto) {
        ayuda = texto;
        return this;
    }

    public CampoArmado requerido() {
        requerido = Boolean.TRUE;
        return this;
    }

    public CampoArmado minimo(String valor) {
        minimo = new BigDecimal(valor);
        return this;
    }

    public CampoArmado maximo(String valor) {
        maximo = new BigDecimal(valor);
        return this;
    }

    public CampoArmado largo(Integer desdeLargo, Integer hastaLargo) {
        largoMinimo = desdeLargo;
        largoMaximo = hastaLargo;
        return this;
    }

    public CampoArmado patron(String expresion) {
        patron = expresion;
        return this;
    }

    public CampoArmado entre(String primera, String ultima) {
        desde = primera;
        hasta = ultima;
        return this;
    }

    public CampoArmado moneda(String codigo) {
        moneda = codigo;
        return this;
    }

    /** Opciones cuyo valor y etiqueta son el mismo texto. */
    public CampoArmado opciones(String... valores) {
        opciones = Arrays.stream(valores).map(valor -> new OpcionDeLista(valor, valor)).toList();
        return this;
    }

    public CampoArmado opcionesTal(List<OpcionDeLista> estas) {
        opciones = estas;
        return this;
    }

    public CampoArmado porDefecto(String ruta) {
        porDefecto = ruta;
        return this;
    }

    public CampoArmado visibleSi(String condicion) {
        visibleSi = condicion;
        return this;
    }

    public CampoArmado requeridoSi(String condicion) {
        requeridoSi = condicion;
        return this;
    }

    public CampoDeFormulario armar() {
        return new CampoDeFormulario(nombre, etiqueta, tipo, ayuda, requerido, minimo, maximo, largoMinimo,
                largoMaximo, patron, desde, hasta, moneda, opciones, porDefecto, visibleSi, requeridoSi);
    }
}
