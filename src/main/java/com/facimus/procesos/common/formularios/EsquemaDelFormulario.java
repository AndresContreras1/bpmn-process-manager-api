package com.facimus.procesos.common.formularios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.facimus.procesos.common.condiciones.ArbolDeCondicion;
import com.facimus.procesos.common.condiciones.CondicionMalEscrita;
import com.facimus.procesos.common.condiciones.EvaluadorDeCondiciones;

/**
 * PR 69: si un formulario esta bien armado. Se pregunta al guardarlo, donde un problema es un 400 con el campo, y otra
 * vez al publicar, donde es el error E-22 del diagnostico: un formulario que llega dentro de un archivo BPMN no paso
 * por la API.
 *
 * <p>Cada problema va con la ruta de la propiedad que lo tiene, {@code campos[2].patron}, como los errores de
 * validacion del resto de la API.
 */
public final class EsquemaDelFormulario {

    /** Cuantos campos admite un formulario. */
    public static final int TOPE_DE_CAMPOS = 100;

    /** Cuantas opciones admite una lista. */
    public static final int TOPE_DE_OPCIONES = 200;

    private static final Pattern NOMBRE = Pattern.compile("[A-Za-z]\\w{0,59}");
    private static final Pattern RUTA = Pattern.compile("[A-Za-z_]\\w{0,59}(\\.[A-Za-z_]\\w{0,59}){0,9}");
    /** Las palabras de la gramatica y los prefijos que el motor usa para sus propias variables. */
    private static final Set<String> RESERVADOS = Set.of("and", "or", "not", "true", "false", "caso", "tarea");
    private static final String NO_APLICA = "No aplica a un campo de tipo ";
    private static final String CAMPOS = "campos";
    private static final String MINIMO = "minimo";
    private static final String MAXIMO = "maximo";
    private static final String EL_NOMBRE = "nombre";
    private static final String LAS_OPCIONES = "opciones";
    private static final String LARGO_MINIMO = "largoMinimo";
    private static final String DESDE = "desde";
    private static final String LA_MONEDA = "moneda";
    private static final String CARACTERES = " caracteres.";

    private EsquemaDelFormulario() {
    }

    /** Los problemas del formulario, por la ruta de la propiedad; vacio si esta bien armado. */
    public static Map<String, String> problemas(Formulario formulario) {
        Map<String, String> problemas = new LinkedHashMap<>();
        List<CampoDeFormulario> campos = formulario.campos();
        if (campos.size() > TOPE_DE_CAMPOS) {
            problemas.put(CAMPOS, "Un formulario admite hasta " + TOPE_DE_CAMPOS + " campos.");
            return problemas;
        }
        Set<String> nombres = new HashSet<>();
        for (int i = 0; i < campos.size(); i++) {
            new Revision(campos, i, problemas).revisar(nombres);
        }
        return problemas;
    }

    /** Lo que se revisa de un campo, con las rutas de sus propiedades. */
    private record Revision(List<CampoDeFormulario> campos, int i, Map<String, String> problemas) {

        void revisar(Set<String> nombres) {
            CampoDeFormulario campo = campos.get(i);
            nombre(campo, nombres);
            texto("etiqueta", campo.etiqueta(), 200, true);
            texto("ayuda", campo.ayuda(), 500, false);
            if (campo.tipo() == null) {
                anotar("tipo", "El tipo es obligatorio.");
                return;
            }
            loQueNoAplica(campo);
            TipoDeCampo tipo = campo.tipo();
            if (tipo.conOpciones()) {
                opciones(campo);
            }
            if (tipo.conMinimoYMaximo()) {
                minimoYMaximo(campo);
            }
            if (tipo.esTexto()) {
                largos(campo);
                if (campo.patron() != null) {
                    Patrones.problema(campo.patron()).ifPresent(problema -> anotar("patron", problema));
                }
            }
            if (tipo.conDesdeYHasta()) {
                desdeYHasta(campo);
            }
            if (tipo == TipoDeCampo.MONEDA && campo.moneda() != null) {
                moneda(campo.moneda());
            }
            if (campo.porDefecto() != null && !RUTA.matcher(campo.porDefecto()).matches()) {
                anotar("porDefecto", "Es la ruta de una variable del caso, como order.total.");
            }
            condicion("visibleSi", campo.visibleSi());
            condicion("requeridoSi", campo.requeridoSi());
        }

        private void nombre(CampoDeFormulario campo, Set<String> nombres) {
            String nombre = campo.nombre();
            if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
                anotar(EL_NOMBRE, "El nombre empieza por una letra y sigue con letras, digitos o _, hasta 60: es el "
                        + "nombre de la variable que llena.");
            } else if (RESERVADOS.contains(nombre.toLowerCase(Locale.ROOT))) {
                anotar(EL_NOMBRE, "\"" + nombre + "\" es una palabra reservada.");
            } else if (!nombres.add(nombre.toLowerCase(Locale.ROOT))) {
                anotar(EL_NOMBRE, "Ya hay otro campo que se llama \"" + nombre + "\".");
            }
        }

        private void texto(String propiedad, String valor, int tope, boolean obligatorio) {
            if (obligatorio && (valor == null || valor.isBlank())) {
                anotar(propiedad, "Es obligatoria.");
            } else if (valor != null && valor.length() > tope) {
                anotar(propiedad, "No puede superar " + tope + CARACTERES);
            }
        }

        /** Una propiedad que el tipo no admite: sobra y confundiria a quien lee el formulario. */
        private void loQueNoAplica(CampoDeFormulario campo) {
            TipoDeCampo tipo = campo.tipo();
            String noAplica = NO_APLICA + tipo + ".";
            sobra(LAS_OPCIONES, campo.opciones() != null && !campo.opciones().isEmpty() && !tipo.conOpciones(),
                    noAplica);
            sobra(MINIMO, campo.minimo() != null && !tipo.conMinimoYMaximo(), noAplica);
            sobra(MAXIMO, campo.maximo() != null && !tipo.conMinimoYMaximo(), noAplica);
            sobra(LARGO_MINIMO, campo.largoMinimo() != null && !tipo.esTexto(), noAplica);
            sobra("largoMaximo", campo.largoMaximo() != null && !tipo.esTexto(), noAplica);
            sobra("patron", campo.patron() != null && !tipo.esTexto(), noAplica);
            sobra(DESDE, campo.desde() != null && !tipo.conDesdeYHasta(), noAplica);
            sobra("hasta", campo.hasta() != null && !tipo.conDesdeYHasta(), noAplica);
            sobra(LA_MONEDA, campo.moneda() != null && tipo != TipoDeCampo.MONEDA, noAplica);
            sobra("requerido", campo.esRequerido() && !tipo.llevaValor(), noAplica);
            sobra("requeridoSi", campo.requeridoSi() != null && !tipo.llevaValor(), noAplica);
            sobra("porDefecto", campo.porDefecto() != null && !tipo.llevaValor(), noAplica);
        }

        private void sobra(String propiedad, boolean sobra, String problema) {
            if (sobra) {
                anotar(propiedad, problema);
            }
        }

        private void opciones(CampoDeFormulario campo) {
            List<OpcionDeLista> opciones = campo.lasOpciones();
            if (opciones.isEmpty()) {
                anotar(LAS_OPCIONES, "Una lista necesita al menos una opcion.");
                return;
            }
            if (opciones.size() > TOPE_DE_OPCIONES) {
                anotar(LAS_OPCIONES, "Una lista admite hasta " + TOPE_DE_OPCIONES + " opciones.");
                return;
            }
            Set<String> valores = new HashSet<>();
            for (int j = 0; j < opciones.size(); j++) {
                OpcionDeLista opcion = opciones.get(j);
                String ruta = "opciones[" + j + "].valor";
                if (opcion.valor() == null || opcion.valor().isBlank()) {
                    anotar(ruta, "El valor de la opcion es obligatorio.");
                } else if (opcion.valor().length() > 100) {
                    anotar(ruta, "No puede superar 100" + CARACTERES);
                } else if (!valores.add(opcion.valor())) {
                    anotar(ruta, "La opcion \"" + opcion.valor() + "\" esta repetida.");
                }
                if (opcion.etiqueta() != null && opcion.etiqueta().length() > 200) {
                    anotar("opciones[" + j + "].etiqueta", "No puede superar 200" + CARACTERES);
                }
            }
        }

        private void minimoYMaximo(CampoDeFormulario campo) {
            BigDecimal minimo = campo.minimo();
            BigDecimal maximo = campo.maximo();
            if (!cabe(MINIMO, minimo) || !cabe(MAXIMO, maximo)) {
                return;
            }
            if (minimo != null && maximo != null && minimo.compareTo(maximo) > 0) {
                anotar(MINIMO, "El minimo no puede ser mayor que el maximo.");
            }
            if (campo.tipo() == TipoDeCampo.LISTA_MULTIPLE) {
                cuantasOpciones(MINIMO, minimo, campo.lasOpciones().size());
                cuantasOpciones(MAXIMO, maximo, campo.lasOpciones().size());
            }
        }

        /**
         * Un limite cabe en lo que un numero puede valer: quince cifras enteras y seis decimales. {@code 1E+999999999}
         * es un BigDecimal valido, pero escribirlo entero, en un mensaje o en el BPMN, no terminaria.
         */
        private boolean cabe(String propiedad, BigDecimal valor) {
            if (valor == null) {
                return true;
            }
            BigDecimal sinCeros = valor.stripTrailingZeros();
            if (sinCeros.precision() - sinCeros.scale() > 15 || sinCeros.scale() > 6) {
                anotar(propiedad, "Admite hasta 15 cifras enteras y 6 decimales.");
                return false;
            }
            return true;
        }

        /** En una lista multiple, el minimo y el maximo cuentan opciones: un entero entre cero y cuantas hay. */
        private void cuantasOpciones(String propiedad, BigDecimal valor, int opciones) {
            if (valor == null) {
                return;
            }
            if (valor.signum() < 0 || valor.stripTrailingZeros().scale() > 0) {
                anotar(propiedad, "Cuenta opciones elegidas: es un entero, cero o mas.");
            } else if (valor.compareTo(BigDecimal.valueOf(opciones)) > 0) {
                anotar(propiedad, "La lista tiene " + opciones + " opciones: no se pueden elegir mas.");
            }
        }

        private void largos(CampoDeFormulario campo) {
            int tope = campo.tipo().largoTope();
            Integer minimo = campo.largoMinimo();
            Integer maximo = campo.largoMaximo();
            if (minimo != null && (minimo < 0 || minimo > tope)) {
                anotar(LARGO_MINIMO, "Va de 0 a " + tope + CARACTERES);
            }
            if (maximo != null && (maximo < 1 || maximo > tope)) {
                anotar("largoMaximo", "Un campo " + campo.tipo() + " admite de 1 a " + tope + CARACTERES);
            }
            if (minimo != null && maximo != null && minimo > maximo) {
                anotar(LARGO_MINIMO, "El largo minimo no puede ser mayor que el maximo.");
            }
        }

        private void desdeYHasta(CampoDeFormulario campo) {
            Optional<LocalDateTime> desde = fecha(DESDE, campo.tipo(), campo.desde());
            Optional<LocalDateTime> hasta = fecha("hasta", campo.tipo(), campo.hasta());
            if (desde.isPresent() && hasta.isPresent() && desde.orElseThrow().isAfter(hasta.orElseThrow())) {
                anotar(DESDE, "La fecha desde no puede ser posterior a la fecha hasta.");
            }
        }

        private Optional<LocalDateTime> fecha(String propiedad, TipoDeCampo tipo, String valor) {
            if (valor == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(tipo == TipoDeCampo.FECHA ? LocalDate.parse(valor).atStartOfDay()
                        : LocalDateTime.parse(valor));
            } catch (DateTimeParseException _) {
                anotar(propiedad, tipo == TipoDeCampo.FECHA ? "Es un dia en ISO 8601, como 2026-12-31."
                        : "Es un dia y una hora en ISO 8601, como 2026-12-31T18:00:00.");
                return Optional.empty();
            }
        }

        private void moneda(String codigo) {
            try {
                if (!codigo.equals(Currency.getInstance(codigo).getCurrencyCode())) {
                    anotar(LA_MONEDA, "Es un codigo ISO 4217 en mayusculas, como COP o EUR.");
                }
            } catch (IllegalArgumentException _) {
                anotar(LA_MONEDA, "\"" + codigo + "\" no es un codigo ISO 4217, como COP o EUR.");
            }
        }

        /**
         * La condicion compila con la gramatica de D6, y de los campos del formulario solo pregunta por los que van
         * antes y tienen valor: asi no hay ciclos y la web puede evaluar el formulario de arriba abajo.
         */
        private void condicion(String propiedad, String condicion) {
            if (condicion == null || condicion.isBlank()) {
                return;
            }
            if (condicion.length() > 1_000) {
                anotar(propiedad, "No puede superar 1000" + CARACTERES);
                return;
            }
            List<String> rutas;
            try {
                rutas = ArbolDeCondicion.rutas(EvaluadorDeCondiciones.compilar(condicion));
            } catch (CondicionMalEscrita mala) {
                anotar(propiedad, mala.getMessage());
                return;
            }
            for (String ruta : rutas) {
                String primero = RespuestasDelFormulario.primerTramo(ruta);
                int donde = posicion(primero);
                if (donde < 0) {
                    continue;
                }
                if (donde >= i) {
                    anotar(propiedad, "Pregunta por \"" + primero + "\", que no va antes que este campo: una "
                            + "condicion solo ve los campos de arriba.");
                    return;
                }
                if (campos.get(donde).esSeccion()) {
                    anotar(propiedad, "\"" + primero + "\" es una seccion: no tiene valor por el que preguntar.");
                    return;
                }
            }
        }

        private int posicion(String nombre) {
            for (int j = 0; j < campos.size(); j++) {
                if (nombre.equals(campos.get(j).nombre())) {
                    return j;
                }
            }
            return -1;
        }

        private void anotar(String propiedad, String problema) {
            problemas.putIfAbsent(CAMPOS + "[" + i + "]." + propiedad, problema);
        }
    }
}
