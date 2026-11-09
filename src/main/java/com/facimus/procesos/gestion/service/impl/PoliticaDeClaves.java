package com.facimus.procesos.gestion.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.SolicitudInvalidaException;

/**
 * Lo que una contrasena elegida por una persona tiene que cumplir, como pide NIST SP 800-63B-4 (3.1.1.2). Ninguna
 * regla de composicion: ni mayusculas ni simbolos obligatorios, que solo llevan a claves predecibles. Al menos 15
 * caracteres mientras la contrasena sea el unico factor (8 cuando haya un segundo factor). Que no este entre las
 * filtradas o las de siempre, ni sea un caracter o un grupo corto repetido o una serie, ni lleve el nombre, el correo
 * o la tienda de quien la elige.
 *
 * <p>El largo se cuenta en caracteres de verdad, y el maximo en bytes: BCrypt no mira mas alla de 72. Las comparaciones
 * no distinguen mayusculas, tildes ni espacios.
 */
@Component
public class PoliticaDeClaves {

    /** Con la contrasena como unico factor; con un segundo factor bastaran 8. */
    public static final int MINIMO_UN_FACTOR = 15;

    static final int MAXIMO_BYTES = 72;

    /** Las palabras del contexto mas cortas que esto no se buscan: "ana" esta dentro de demasiadas claves buenas. */
    private static final int PALABRA_MINIMA = 4;

    /** El grupo mas largo que se busca repetido: "abcd" cuatro veces son 16 caracteres que no valen nada. */
    private static final int GRUPO_MAXIMO = 4;

    /** El nombre del servicio tambien es contexto. */
    private static final Set<String> DEL_SERVICIO = Set.of("bpmn");

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");
    private static final Pattern SEPARADORES = Pattern.compile("[\\s@._+\\-]+");

    private final Set<String> filtradas;

    public PoliticaDeClaves() {
        this.filtradas = cargar("claves/filtradas.txt");
    }

    /** Lanza si la clave no cumple, con el motivo; el contexto es el nombre, el correo y la tienda de quien la elige. */
    public void comprobar(String clave, String... contexto) {
        if (clave.codePointCount(0, clave.length()) < MINIMO_UN_FACTOR) {
            throw new SolicitudInvalidaException("La contraseña debe tener al menos " + MINIMO_UN_FACTOR
                    + " caracteres. Una frase de varias palabras sirve, y se recuerda mejor.");
        }
        if (clave.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            throw new SolicitudInvalidaException("La contraseña no puede superar " + MAXIMO_BYTES
                    + " bytes: con tildes, eñes o símbolos cada carácter ocupa más de uno.");
        }
        String normal = normalizar(clave);
        if (filtradas.contains(normal)) {
            throw new SolicitudInvalidaException("Esa contraseña aparece entre las filtradas o las de siempre: "
                    + "elige otra.");
        }
        if (repetida(normal) || seguida(normal)) {
            throw new SolicitudInvalidaException("Un carácter o un grupo corto repetido, o una serie como abcdef o "
                    + "123456, se adivina enseguida: elige otra contraseña.");
        }
        boolean conContexto = Stream.concat(Arrays.stream(contexto).filter(Objects::nonNull)
                        .flatMap(PoliticaDeClaves::palabras), DEL_SERVICIO.stream())
                .anyMatch(normal::contains);
        if (conContexto) {
            throw new SolicitudInvalidaException("La contraseña no puede llevar tu nombre, tu correo, el nombre de la "
                    + "tienda ni el del servicio.");
        }
    }

    /** Sin mayusculas, tildes ni espacios: "Contraseña Segura" es la misma clave que "contrasenasegura". */
    static String normalizar(String texto) {
        String sinTildes = MARCAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return ESPACIOS.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** Las palabras de un nombre, un correo o una tienda que vale la pena buscar dentro de una clave. */
    private static Stream<String> palabras(String dato) {
        return SEPARADORES.splitAsStream(dato)
                .map(PoliticaDeClaves::normalizar)
                .filter(palabra -> palabra.codePointCount(0, palabra.length()) >= PALABRA_MINIMA);
    }

    /** La clave entera es un grupo de hasta cuatro caracteres repetido: aaaa, 1212, abcabc, 12341234. */
    private static boolean repetida(String clave) {
        int[] caracteres = clave.codePoints().toArray();
        for (int grupo = 1; grupo <= GRUPO_MAXIMO; grupo++) {
            boolean repite = true;
            for (int i = grupo; i < caracteres.length && repite; i++) {
                repite = caracteres[i] == caracteres[i - grupo];
            }
            if (repite) {
                return true;
            }
        }
        return false;
    }

    /** Cada caracter es el siguiente del anterior, o cada uno el anterior: abcdef, 98765. */
    private static boolean seguida(String clave) {
        int[] caracteres = clave.codePoints().toArray();
        boolean subiendo = true;
        boolean bajando = true;
        for (int i = 1; i < caracteres.length; i++) {
            int salto = caracteres[i] - caracteres[i - 1];
            subiendo &= salto == 1;
            bajando &= salto == -1;
        }
        return subiendo || bajando;
    }

    private static Set<String> cargar(String recurso) {
        Set<String> claves = new HashSet<>();
        try (InputStream entrada = PoliticaDeClaves.class.getClassLoader().getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new IllegalStateException("Falta la lista de contraseñas filtradas: " + recurso);
            }
            new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8)).lines()
                    .map(String::strip)
                    .filter(linea -> !linea.isEmpty() && !linea.startsWith("#"))
                    .map(PoliticaDeClaves::normalizar)
                    .forEach(claves::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Set.copyOf(claves);
    }
}
