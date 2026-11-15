package com.facimus.procesos.common.formularios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.facimus.procesos.common.condiciones.CondicionMalEscrita;
import com.facimus.procesos.common.condiciones.EvaluadorDeCondiciones;
import com.facimus.procesos.common.condiciones.Variables;

/**
 * PR 69: lo que alguien contesto en un formulario, revisado contra su esquema y convertido a valores con tipo. Es lo
 * que el servidor hace al completar una tarea o abrir un caso: la web puede validar antes, pero quien decide es esto.
 *
 * <p>El formulario se recorre de arriba abajo. Cada campo ve las respuestas validas de los campos visibles que van
 * antes, por su nombre, y las variables del caso; asi se decide si se muestra ({@code visibleSi}, y el de su seccion)
 * y si es obligatorio ({@code requerido} o {@code requeridoSi}). Lo que se contesto en un campo escondido no cuenta: se
 * descarta, como si no se hubiera mandado.
 *
 * <p>Los valores salen con el tipo que entienden las condiciones del motor: los numeros como numeros, las fechas como
 * texto ISO 8601, que es como {@code >} y {@code <} las comparan, y las listas multiples como listas.
 */
public final class RespuestasDelFormulario {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int CIFRAS_ENTERAS = 15;
    private static final int DECIMALES_DE_UN_NUMERO = 6;
    private static final int DECIMALES_DE_UNA_MONEDA = 2;
    static final String OBLIGATORIO = "Es obligatorio.";
    private static final String ADMITE_HASTA = "Admite hasta ";

    private RespuestasDelFormulario() {
    }

    /** Lo que se puede comprobar solo afuera: si un id es de una persona o de un documento que se puede usar. */
    public interface Referencias {

        boolean esPersona(long id);

        boolean esArchivo(long id);
    }

    /**
     * @param valores lo que se guarda, por el nombre del campo: solo los campos visibles con respuesta
     * @param errores por que no se acepta cada campo; vacio si se acepta el formulario entero
     */
    public record Resultado(Map<String, Object> valores, Map<String, String> errores) {

        public boolean valido() {
            return errores.isEmpty();
        }
    }

    /** Revisa las respuestas: cada campo que falla dice por que, y si ninguno falla salen los valores con tipo. */
    public static Resultado validar(Formulario formulario, Map<String, Object> respuestas, Variables delCaso,
            Referencias referencias) {
        Map<String, Object> contestadas = respuestas == null ? Map.of() : respuestas;
        Map<String, String> errores = new LinkedHashMap<>();
        desconocidas(formulario, contestadas, errores);
        Recorrido recorrido = new Recorrido(formulario, delCaso);
        for (CampoDeFormulario campo : formulario.campos()) {
            if (recorrido.visible(campo)) {
                revisar(campo, contestadas.get(campo.nombre()), referencias, recorrido, errores);
            }
        }
        return new Resultado(errores.isEmpty() ? recorrido.valores() : Map.of(), Collections.unmodifiableMap(errores));
    }

    /** Un campo visible: su valor queda como contestado, o su nombre en los errores con el motivo. */
    private static void revisar(CampoDeFormulario campo, Object crudo, Referencias referencias, Recorrido recorrido,
            Map<String, String> errores) {
        Object valor;
        try {
            valor = leer(campo, crudo);
            if (valor != null) {
                referencias(campo, valor, referencias);
            }
        } catch (ValorInvalido invalido) {
            errores.put(campo.nombre(), invalido.getMessage());
            return;
        }
        if (valor != null) {
            recorrido.contestado(campo.nombre(), valor);
        } else if (recorrido.obligatorio(campo)) {
            errores.put(campo.nombre(), OBLIGATORIO);
        }
    }

    /**
     * Lo que se propone al abrir el formulario: el valor de la variable del caso que nombra {@code porDefecto}, si
     * cabe en el campo. Lo que no cabe, como un texto en un campo de numero, no se propone.
     */
    public static Map<String, Object> iniciales(Formulario formulario, Variables delCaso) {
        Map<String, Object> iniciales = new LinkedHashMap<>();
        for (CampoDeFormulario campo : formulario.campos()) {
            inicial(campo, delCaso).ifPresent(valor -> iniciales.put(campo.nombre(), valor));
        }
        return Collections.unmodifiableMap(iniciales);
    }

    /** El valor que se propone en un campo: el de la variable que nombra, si existe y cabe en el. */
    private static Optional<Object> inicial(CampoDeFormulario campo, Variables delCaso) {
        if (campo.porDefecto() == null || !campo.tipo().llevaValor()) {
            return Optional.empty();
        }
        return delCaso.valor(campo.porDefecto()).flatMap(variable -> cabe(campo, variable));
    }

    private static Optional<Object> cabe(CampoDeFormulario campo, Object variable) {
        try {
            return Optional.ofNullable(leer(campo, variable));
        } catch (ValorInvalido _) {
            // Un valor por defecto que no cabe en el campo no se propone: quien llena el formulario lo escribe
            return Optional.empty();
        }
    }

    /** El nombre que encabeza una ruta: {@code metodo} en {@code metodo.codigo}. */
    static String primerTramo(String ruta) {
        int punto = ruta.indexOf('.');
        return punto < 0 ? ruta : ruta.substring(0, punto);
    }

    /** Una respuesta a algo que el formulario no pregunta es un error: no se cuela en las variables del caso. */
    private static void desconocidas(Formulario formulario, Map<String, Object> contestadas,
            Map<String, String> errores) {
        for (String nombre : contestadas.keySet()) {
            Optional<CampoDeFormulario> campo = formulario.campo(nombre);
            if (campo.isEmpty()) {
                errores.put(nombre, "No es un campo de este formulario.");
            } else if (campo.orElseThrow().esSeccion()) {
                errores.put(nombre, "Una seccion no lleva valor.");
            }
        }
    }

    /** Las condiciones de cada campo, con lo que ya se contesto arriba y las variables del caso. */
    private static final class Recorrido {

        private final Set<String> nombres = new HashSet<>();
        private final Map<String, Object> arriba = new LinkedHashMap<>();
        private final Variables vista;
        private boolean seccionVisible = true;

        Recorrido(Formulario formulario, Variables delCaso) {
            formulario.campos().forEach(campo -> nombres.add(campo.nombre()));
            Variables delFormulario = Variables.de(arriba);
            // Un nombre del formulario tapa a una variable del caso que se llame igual, aunque todavia no tenga valor
            this.vista = ruta -> nombres.contains(primerTramo(ruta)) ? delFormulario.valor(ruta) : delCaso.valor(ruta);
        }

        /** Una seccion decide por los campos que vienen despues de ella, hasta la siguiente. */
        boolean visible(CampoDeFormulario campo) {
            if (campo.esSeccion()) {
                seccionVisible = seCumple(campo.visibleSi(), true);
                return false;
            }
            return seccionVisible && seCumple(campo.visibleSi(), true);
        }

        boolean obligatorio(CampoDeFormulario campo) {
            return campo.esRequerido() || seCumple(campo.requeridoSi(), false);
        }

        void contestado(String nombre, Object valor) {
            arriba.put(nombre, valor);
        }

        /** Lo contestado en los campos visibles, en el orden del formulario. */
        Map<String, Object> valores() {
            return Collections.unmodifiableMap(arriba);
        }

        /**
         * Una condicion que no compila no deberia llegar aqui: el esquema se reviso al guardarlo y al publicarlo. Si
         * llegara, vale lo que valdria sin ella.
         */
        private boolean seCumple(String condicion, boolean sinCondicion) {
            if (condicion == null || condicion.isBlank()) {
                return sinCondicion;
            }
            try {
                return EvaluadorDeCondiciones.compilar(condicion).seCumple(vista);
            } catch (CondicionMalEscrita _) {
                return sinCondicion;
            }
        }
    }

    /**
     * El valor de la respuesta con su tipo y las reglas del campo cumplidas, o nulo si no se contesto: ni un texto en
     * blanco ni una lista vacia son una respuesta.
     */
    static Object leer(CampoDeFormulario campo, Object crudo) {
        if (crudo == null) {
            return null;
        }
        return switch (campo.tipo()) {
            case TEXTO, TEXTO_LARGO -> texto(campo, crudo);
            case NUMERO -> numero(campo, crudo, DECIMALES_DE_UN_NUMERO);
            case MONEDA -> numero(campo, crudo, decimalesDe(campo.moneda()));
            case FECHA -> fecha(campo, crudo);
            case FECHA_HORA -> fechaYHora(campo, crudo);
            case SI_NO -> siNo(crudo);
            case LISTA -> opcion(campo, crudo);
            case LISTA_MULTIPLE -> sinRespuestaSiVacia(opciones(campo, crudo));
            case PERSONA, ARCHIVO -> id(crudo);
            case SECCION -> null;
        };
    }

    private static String texto(CampoDeFormulario campo, Object crudo) {
        if (!(crudo instanceof String texto)) {
            throw new ValorInvalido("Debe ser un texto.");
        }
        if (texto.isBlank()) {
            return null;
        }
        if (campo.tipo() == TipoDeCampo.TEXTO && (texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0)) {
            throw new ValorInvalido("Es una sola linea: para varias, el campo seria de texto largo.");
        }
        int largo = texto.codePointCount(0, texto.length());
        int maximo = campo.largoMaximo() == null ? campo.tipo().largoTope() : campo.largoMaximo();
        if (largo > maximo) {
            throw new ValorInvalido(ADMITE_HASTA + maximo + " caracteres.");
        }
        if (campo.largoMinimo() != null && largo < campo.largoMinimo()) {
            throw new ValorInvalido("Necesita al menos " + campo.largoMinimo() + " caracteres.");
        }
        if (campo.patron() != null && !Patrones.cumple(campo.patron(), texto)) {
            throw new ValorInvalido("No tiene la forma que se pide.");
        }
        return texto;
    }

    private static Object numero(CampoDeFormulario campo, Object crudo, int decimales) {
        BigDecimal numero = comoNumero(crudo);
        if (Math.max(numero.scale(), 0) > decimales) {
            throw new ValorInvalido(decimales == 0 ? "No admite decimales." : ADMITE_HASTA + decimales
                    + " decimales.");
        }
        if (campo.minimo() != null && numero.compareTo(campo.minimo()) < 0) {
            throw new ValorInvalido("Debe ser al menos " + campo.minimo().toPlainString() + ".");
        }
        if (campo.maximo() != null && numero.compareTo(campo.maximo()) > 0) {
            throw new ValorInvalido("Debe ser como mucho " + campo.maximo().toPlainString() + ".");
        }
        return comoJson(numero);
    }

    /** Un numero de JSON, sin ceros de mas y con hasta quince cifras enteras: lo que cabe en un {@code long}. */
    private static BigDecimal comoNumero(Object crudo) {
        if (!(crudo instanceof Number numero)) {
            throw new ValorInvalido("Debe ser un numero.");
        }
        BigDecimal decimal;
        try {
            decimal = new BigDecimal(numero.toString()).stripTrailingZeros();
        } catch (NumberFormatException _) {
            throw new ValorInvalido("Debe ser un numero.");
        }
        if (decimal.precision() - decimal.scale() > CIFRAS_ENTERAS) {
            throw new ValorInvalido(ADMITE_HASTA + CIFRAS_ENTERAS + " cifras enteras.");
        }
        return decimal;
    }

    /** Un entero sale como entero: {@code 100} y no {@code 1E+2}, que es como se escribe un decimal sin ceros. */
    private static Object comoJson(BigDecimal numero) {
        return numero.scale() <= 0 ? (Object) numero.longValueExact() : numero;
    }

    /** Los decimales de la moneda del campo: cero en el yen, dos en el peso; dos si el campo no dice moneda. */
    private static int decimalesDe(String moneda) {
        if (moneda == null) {
            return DECIMALES_DE_UNA_MONEDA;
        }
        try {
            return Math.max(Currency.getInstance(moneda).getDefaultFractionDigits(), 0);
        } catch (IllegalArgumentException _) {
            return DECIMALES_DE_UNA_MONEDA;
        }
    }

    private static String fecha(CampoDeFormulario campo, Object crudo) {
        LocalDate dia;
        try {
            dia = LocalDate.parse(comoTexto(crudo));
        } catch (DateTimeParseException _) {
            throw new ValorInvalido("Es un dia en ISO 8601, como 2026-11-12.");
        }
        entre(dia.atStartOfDay(), campo, true);
        return dia.toString();
    }

    private static String fechaYHora(CampoDeFormulario campo, Object crudo) {
        LocalDateTime momento;
        try {
            momento = LocalDateTime.parse(comoTexto(crudo)).truncatedTo(ChronoUnit.SECONDS);
        } catch (DateTimeParseException _) {
            throw new ValorInvalido("Es un dia y una hora en ISO 8601, como 2026-11-12T09:30:00.");
        }
        entre(momento, campo, false);
        return FECHA_HORA.format(momento);
    }

    private static String comoTexto(Object crudo) {
        if (!(crudo instanceof String texto)) {
            throw new ValorInvalido("Debe ser un texto con la fecha.");
        }
        return texto;
    }

    /** Desde y hasta incluidos. El esquema ya comprobo que se leen; si no se leyeran, no limitan. */
    private static void entre(LocalDateTime valor, CampoDeFormulario campo, boolean soloDia) {
        limite(campo.desde(), soloDia).filter(valor::isBefore).ifPresent(desde -> {
            throw new ValorInvalido("No puede ser antes de " + campo.desde() + ".");
        });
        limite(campo.hasta(), soloDia).filter(valor::isAfter).ifPresent(hasta -> {
            throw new ValorInvalido("No puede ser despues de " + campo.hasta() + ".");
        });
    }

    private static Optional<LocalDateTime> limite(String texto, boolean soloDia) {
        if (texto == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(soloDia ? LocalDate.parse(texto).atStartOfDay() : LocalDateTime.parse(texto));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }

    private static Boolean siNo(Object crudo) {
        if (!(crudo instanceof Boolean respuesta)) {
            throw new ValorInvalido("Es true o false.");
        }
        return respuesta;
    }

    private static String opcion(CampoDeFormulario campo, Object crudo) {
        if (!(crudo instanceof String elegida)) {
            throw new ValorInvalido("Es el valor de una de las opciones.");
        }
        if (elegida.isEmpty()) {
            return null;
        }
        exigirOpcion(campo, elegida);
        return elegida;
    }

    private static List<String> opciones(CampoDeFormulario campo, Object crudo) {
        if (!(crudo instanceof List<?> lista)) {
            throw new ValorInvalido("Es una lista con los valores de las opciones elegidas.");
        }
        if (lista.isEmpty()) {
            return List.of();
        }
        List<String> elegidas = new ArrayList<>();
        for (Object elemento : lista) {
            if (!(elemento instanceof String elegida)) {
                throw new ValorInvalido("Es una lista con los valores de las opciones elegidas.");
            }
            exigirOpcion(campo, elegida);
            if (elegidas.contains(elegida)) {
                throw new ValorInvalido("La opcion \"" + elegida + "\" esta elegida dos veces.");
            }
            elegidas.add(elegida);
        }
        if (campo.minimo() != null && elegidas.size() < campo.minimo().intValue()) {
            throw new ValorInvalido("Elige al menos " + campo.minimo().intValue() + ".");
        }
        if (campo.maximo() != null && elegidas.size() > campo.maximo().intValue()) {
            throw new ValorInvalido("Elige como mucho " + campo.maximo().intValue() + ".");
        }
        return List.copyOf(elegidas);
    }

    /** Una lista sin ninguna opcion elegida no es una respuesta. */
    private static Object sinRespuestaSiVacia(List<String> elegidas) {
        return elegidas.isEmpty() ? null : elegidas;
    }

    private static void exigirOpcion(CampoDeFormulario campo, String elegida) {
        if (campo.lasOpciones().stream().noneMatch(opcion -> opcion.valor().equals(elegida))) {
            throw new ValorInvalido("\"" + elegida + "\" no es una de las opciones.");
        }
    }

    /** El id de una persona o de un documento: un entero positivo. Si existe lo dice {@link Referencias}. */
    private static Long id(Object crudo) {
        BigDecimal numero = comoNumero(crudo);
        if (numero.scale() > 0 || numero.signum() <= 0) {
            throw new ValorInvalido("Es un id: un entero positivo.");
        }
        return numero.longValueExact();
    }

    private static void referencias(CampoDeFormulario campo, Object valor, Referencias referencias) {
        if (campo.tipo() == TipoDeCampo.PERSONA && !referencias.esPersona((Long) valor)) {
            throw new ValorInvalido("No es una persona activa de la organizacion.");
        }
        if (campo.tipo() == TipoDeCampo.ARCHIVO && !referencias.esArchivo((Long) valor)) {
            throw new ValorInvalido("No es un documento del proceso listo para usar.");
        }
    }

    /** Por que no se acepta una respuesta. No sale de aqui: termina como el error de su campo. */
    private static final class ValorInvalido extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ValorInvalido(String mensaje) {
            super(mensaje, null, false, false);
        }
    }
}
