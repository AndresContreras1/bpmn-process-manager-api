package com.facimus.procesos.gestion.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.facimus.procesos.common.SolicitudInvalidaException;

/**
 * F12: los idiomas de la web y lo que se deduce de ellos. Cada uno es una compilacion de la web, con sus fechas,
 * numeros y moneda, y la carpeta de la URL donde NGINX la sirve; los correos los escriben en su lengua.
 */
public final class Idiomas {

    public static final String ES_CO = "es-CO";
    public static final String EN_US = "en-US";
    public static final String EN_AU = "en-AU";
    public static final String FR_FR = "fr-FR";

    /** El de una organizacion que no dijo otro, y el de cualquier peticion que no se parece a ninguno. */
    public static final String POR_DEFECTO = ES_CO;

    /** En el orden en que la web los ofrece. */
    public static final List<String> DE_LA_WEB = List.of(ES_CO, EN_US, EN_AU, FR_FR);

    /** La hora con que empieza una organizacion nueva que no dijo la suya: la de la capital de su idioma. */
    private static final Map<String, String> ZONA_POR_DEFECTO = Map.of(
            ES_CO, "America/Bogota",
            EN_US, "America/New_York",
            EN_AU, "Australia/Sydney",
            FR_FR, "Europe/Paris");

    private Idiomas() {
    }

    public static boolean esDeLaWeb(String idioma) {
        return idioma != null && DE_LA_WEB.contains(idioma);
    }

    /** El idioma, si la web lo tiene; si no, un 400 que dice cuales tiene. */
    public static String validar(String idioma) {
        if (!esDeLaWeb(idioma)) {
            throw new SolicitudInvalidaException("El idioma es es-CO, en-US, en-AU o fr-FR.");
        }
        return idioma;
    }

    /**
     * El de la web que mejor responde a una cabecera Accept-Language, en el orden de preferencia que trae: el mismo
     * si coincide, y si no el de su lengua (fr-CA da fr-FR). Sin cabecera, o sin ninguno parecido, el de por defecto.
     */
    public static String deLaCabecera(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return POR_DEFECTO;
        }
        List<Locale.LanguageRange> rangos;
        try {
            rangos = Locale.LanguageRange.parse(acceptLanguage);
        } catch (IllegalArgumentException _) {
            return POR_DEFECTO;
        }
        for (Locale.LanguageRange rango : rangos) {
            String parecido = parecido(Locale.forLanguageTag(rango.getRange()));
            if (parecido != null) {
                return parecido;
            }
        }
        return POR_DEFECTO;
    }

    /** El de la web para una etiqueta cualquiera, como los es, en o fr que guardaban los correos antes de F12. */
    public static String deLaWeb(String etiqueta) {
        String parecido = etiqueta == null ? null : parecido(Locale.forLanguageTag(etiqueta));
        return parecido == null ? POR_DEFECTO : parecido;
    }

    public static String zonaPorDefecto(String idioma) {
        return ZONA_POR_DEFECTO.getOrDefault(idioma, ZONA_POR_DEFECTO.get(POR_DEFECTO));
    }

    /**
     * La zona como la escribe la base de IANA, que es lo que entiende el navegador. Una que no existe, o un
     * desplazamiento fijo como +05:00, no sirve: el desplazamiento no sabe del horario de verano.
     */
    public static String validarZona(String zonaHoraria) {
        ZoneId zona;
        try {
            zona = ZoneId.of(zonaHoraria.strip());
        } catch (DateTimeException _) {
            throw new SolicitudInvalidaException("La zona horaria " + zonaHoraria.strip() + " no existe: es un nombre "
                    + "de la base de IANA, como America/Bogota.");
        }
        if (!zona.getId().contains("/") && !"UTC".equals(zona.getId())) {
            throw new SolicitudInvalidaException("La zona horaria es un nombre de la base de IANA, como America/Bogota.");
        }
        return zona.getId();
    }

    private static String parecido(Locale locale) {
        String etiqueta = locale.toLanguageTag();
        for (String idioma : DE_LA_WEB) {
            if (idioma.equalsIgnoreCase(etiqueta)) {
                return idioma;
            }
        }
        return switch (locale.getLanguage()) {
            case "es" -> ES_CO;
            case "en" -> "AU".equals(locale.getCountry()) || "NZ".equals(locale.getCountry()) ? EN_AU : EN_US;
            case "fr" -> FR_FR;
            default -> null;
        };
    }
}
