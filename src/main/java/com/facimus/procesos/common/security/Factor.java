package com.facimus.procesos.common.security;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.authority.FactorGrantedAuthority;

/**
 * PR 49: con que probo una persona quien es al abrir su sesion (NIST SP 800-63B-4). Los factores viajan en el token y
 * Spring Security los ve como {@link FactorGrantedAuthority}, con la hora en que se verificaron: asi una regla pide un
 * factor, o que sea reciente, sin consultar la base.
 */
public enum Factor {

    /** La contrasena de la persona. */
    PASSWORD(FactorGrantedAuthority.PASSWORD_AUTHORITY);

    private final String autoridad;

    Factor(String autoridad) {
        this.autoridad = autoridad;
    }

    public String autoridad() {
        return autoridad;
    }

    /** Los factores de una lista guardada o de un claim; lo que no se reconoce se ignora. */
    public static Set<Factor> leer(String nombres) {
        if (nombres == null || nombres.isBlank()) {
            return EnumSet.noneOf(Factor.class);
        }
        return Arrays.stream(nombres.split(","))
                .map(String::strip)
                .flatMap(nombre -> Arrays.stream(values()).filter(factor -> factor.name().equals(nombre)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Factor.class)));
    }

    /** La forma en que se guardan y viajan: los nombres separados por comas, en el orden del enum. */
    public static String escribir(Set<Factor> factores) {
        return factores.stream().sorted().map(Factor::name).collect(Collectors.joining(","));
    }
}
