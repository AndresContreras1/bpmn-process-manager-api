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
    private static final String NOMBRE = "nombre";
    private static final String MINIMO = "minimo";
    private static final String MAXIMO = "maximo";
    private static final String LARGO_MINIMO = "largoMinimo";
    private static final String LARGO_MAXIMO = "largoMaximo";
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
            salida.atributo(NOMBRE, campo.nombre());
            salida.atributo(ETIQUETA, campo.etiqueta());
            salida.atributo("tipo", campo.tipo());
            salida.atributo("ayuda", campo.ayuda());
            salida.atributo("requerido", Boolean.TRUE.equals(campo.requerido()) ? "true" : null);
            salida.atributo(MINIMO, plano(campo.minimo()));
            salida.atributo(MAXIMO, plano(campo.maximo()));
            salida.atributo(LARGO_MINIMO, campo.largoMinimo());
            salida.atributo(LARGO_MAXIMO, campo.largoMaximo());
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
     * y la conversion deja ese campo fuera con su nota; un numero que no se lee llega vacio, y
     * {@link #limitesIlegibles(Element)} dice cuales.
     */
    static Formulario leer(Element nodo) {
        Element formulario = elFormulario(nodo);
        if (formulario == null) {
            return null;
        }
        List<CampoDeFormulario> campos = new ArrayList<>();
        for (Element campo : Dom.hijos(formulario, EspaciosBpmn.BPM, CAMPO)) {
            List<OpcionDeLista> opciones = new ArrayList<>();
            for (Element opcion : Dom.hijos(campo, EspaciosBpmn.BPM, OPCION)) {
                opciones.add(new OpcionDeLista(Dom.atributo(opcion, "valor"), Dom.atributo(opcion, ETIQUETA)));
            }
            campos.add(new CampoDeFormulario(Dom.atributo(campo, NOMBRE), Dom.atributo(campo, ETIQUETA),
                    tipo(Dom.atributo(campo, "tipo")), Dom.atributo(campo, "ayuda"),
                    "true".equals(Dom.atributo(campo, "requerido")) ? Boolean.TRUE : null,
                    decimal(Dom.atributo(campo, MINIMO)), decimal(Dom.atributo(campo, MAXIMO)),
                    entero(Dom.atributo(campo, LARGO_MINIMO)), entero(Dom.atributo(campo, LARGO_MAXIMO)),
                    Dom.atributo(campo, "patron"), Dom.atributo(campo, "desde"), Dom.atributo(campo, "hasta"),
                    Dom.atributo(campo, "moneda"), opciones.isEmpty() ? null : opciones,
                    Dom.atributo(campo, "porDefecto"), Dom.atributo(campo, "visibleSi"),
                    Dom.atributo(campo, "requeridoSi")));
        }
        return new Formulario(campos);
    }

    /**
     * Los limites que el formulario trae pero no se leen como numero, uno por linea: {@link #leer(Element)} los deja
     * vacios, y la conversion avisa que el campo se quedo sin ese limite en vez de aflojarlo en silencio.
     */
    static List<String> limitesIlegibles(Element nodo) {
        Element formulario = elFormulario(nodo);
        if (formulario == null) {
            return List.of();
        }
        List<String> ilegibles = new ArrayList<>();
        for (Element campo : Dom.hijos(formulario, EspaciosBpmn.BPM, CAMPO)) {
            for (String limite : List.of(MINIMO, MAXIMO)) {
                String valor = Dom.atributo(campo, limite);
                if (valor != null && decimal(valor) == null) {
                    ilegibles.add(ilegible(campo, limite, "un numero de hasta 18 cifras y 12 decimales"));
                }
            }
            for (String limite : List.of(LARGO_MINIMO, LARGO_MAXIMO)) {
                String valor = Dom.atributo(campo, limite);
                if (valor != null && entero(valor) == null) {
                    ilegibles.add(ilegible(campo, limite, "un entero"));
                }
            }
        }
        return ilegibles;
    }

    private static String ilegible(Element campo, String limite, String esperado) {
        return "El campo \"" + Dom.atributo(campo, NOMBRE) + "\" del formulario trae un " + limite + " que no es "
                + esperado + ": se deja sin ese limite.";
    }

    private static Element elFormulario(Element nodo) {
        Element extensiones = Dom.hijo(nodo, EspaciosBpmn.BPMN, "extensionElements");
        return extensiones == null ? null : Dom.hijo(extensiones, EspaciosBpmn.BPM, FORMULARIO);
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
