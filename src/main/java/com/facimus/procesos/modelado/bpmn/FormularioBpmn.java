package com.facimus.procesos.modelado.bpmn;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.stream.XMLStreamException;

import org.w3c.dom.Element;

import com.facimus.procesos.common.formularios.CampoDeFormulario;
import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.common.formularios.OpcionDeLista;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.modelado.dto.response.CampoDeFormularioResponse;
import com.facimus.procesos.modelado.dto.response.FormularioResponse;

/**
 * PR 69: el formulario de una {@code userTask} o de un {@code startEvent}, como {@code bpm:formulario} dentro de su
 * {@code extensionElements}. Cada campo es un {@code bpm:campoFormulario} con sus propiedades como atributos y sus
 * opciones como {@code bpm:opcion}; lo compilado de las condiciones no se escribe, porque se vuelve a compilar al
 * leerlo. Otra herramienta lo ignora; al importar, vuelve entero.
 */
final class FormularioBpmn {

    static final String FORMULARIO = "formulario";
    private static final String CAMPO = "campoFormulario";
    private static final String OPCION = "opcion";
    private static final String BPM = "bpm";
    private static final String ETIQUETA = "etiqueta";
    /** Un decimal de XML, sin exponente: {@code 1E+999999999} se leeria, pero escribirlo entero no terminaria. */
    private static final Pattern DECIMAL = Pattern.compile("-?\\d{1,18}(\\.\\d{1,12})?");

    private FormularioBpmn() {
    }

    static void escribir(Salida salida, FormularioResponse formulario) throws XMLStreamException {
        salida.abrir(BPM, FORMULARIO);
        for (CampoDeFormularioResponse campo : formulario.campos()) {
            List<OpcionDeLista> opciones = campo.opciones() == null ? List.of() : campo.opciones();
            if (opciones.isEmpty()) {
                salida.vacio(BPM, CAMPO);
            } else {
                salida.abrir(BPM, CAMPO);
            }
            salida.atributo("nombre", campo.nombre());
            salida.atributo(ETIQUETA, campo.etiqueta());
            salida.atributo("tipo", campo.tipo());
            salida.atributo("ayuda", campo.ayuda());
            salida.atributo("requerido", Boolean.TRUE.equals(campo.requerido()) ? "true" : null);
            salida.atributo("minimo", plano(campo.minimo()));
            salida.atributo("maximo", plano(campo.maximo()));
            salida.atributo("largoMinimo", campo.largoMinimo());
            salida.atributo("largoMaximo", campo.largoMaximo());
            salida.atributo("patron", campo.patron());
            salida.atributo("desde", campo.desde());
            salida.atributo("hasta", campo.hasta());
            salida.atributo("moneda", campo.moneda());
            salida.atributo("porDefecto", campo.porDefecto());
            salida.atributo("visibleSi", campo.visibleSi());
            salida.atributo("requeridoSi", campo.requeridoSi());
            for (OpcionDeLista opcion : opciones) {
                salida.vacio(BPM, OPCION);
                salida.atributo("valor", opcion.valor());
                salida.atributo(ETIQUETA, opcion.etiqueta());
            }
            if (!opciones.isEmpty()) {
                salida.cerrar();
            }
        }
        salida.cerrar();
    }

    /** Un decimal de XML no admite exponente: {@code 1E+2} se escribe {@code 100}. */
    private static String plano(BigDecimal numero) {
        return numero == null ? null : numero.toPlainString();
    }

    /**
     * El formulario que trae el nodo, o nulo si no trae ninguno. No decide nada: un tipo que no existe llega sin tipo,
     * y la conversion deja ese campo fuera con su nota; un numero que no se lee llega vacio.
     */
    static Formulario leer(Element nodo) {
        Element extensiones = Dom.hijo(nodo, EspaciosBpmn.BPMN, "extensionElements");
        Element formulario = extensiones == null ? null : Dom.hijo(extensiones, EspaciosBpmn.BPM, FORMULARIO);
        if (formulario == null) {
            return null;
        }
        List<CampoDeFormulario> campos = new ArrayList<>();
        for (Element campo : Dom.hijos(formulario, EspaciosBpmn.BPM, CAMPO)) {
            List<OpcionDeLista> opciones = new ArrayList<>();
            for (Element opcion : Dom.hijos(campo, EspaciosBpmn.BPM, OPCION)) {
                opciones.add(new OpcionDeLista(Dom.atributo(opcion, "valor"), Dom.atributo(opcion, ETIQUETA)));
            }
            campos.add(new CampoDeFormulario(Dom.atributo(campo, "nombre"), Dom.atributo(campo, ETIQUETA),
                    tipo(Dom.atributo(campo, "tipo")), Dom.atributo(campo, "ayuda"),
                    "true".equals(Dom.atributo(campo, "requerido")) ? Boolean.TRUE : null,
                    decimal(Dom.atributo(campo, "minimo")), decimal(Dom.atributo(campo, "maximo")),
                    entero(Dom.atributo(campo, "largoMinimo")), entero(Dom.atributo(campo, "largoMaximo")),
                    Dom.atributo(campo, "patron"), Dom.atributo(campo, "desde"), Dom.atributo(campo, "hasta"),
                    Dom.atributo(campo, "moneda"), opciones.isEmpty() ? null : opciones,
                    Dom.atributo(campo, "porDefecto"), Dom.atributo(campo, "visibleSi"),
                    Dom.atributo(campo, "requeridoSi")));
        }
        return new Formulario(campos);
    }

    private static TipoDeCampo tipo(String valor) {
        try {
            return valor == null ? null : TipoDeCampo.valueOf(valor.strip());
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static BigDecimal decimal(String valor) {
        try {
            return valor == null || !DECIMAL.matcher(valor.strip()).matches() ? null : new BigDecimal(valor.strip());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static Integer entero(String valor) {
        try {
            return valor == null ? null : Integer.valueOf(valor.strip());
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
