package com.facimus.procesos.gestion.model;

/**
 * PR 68: el pais de una tienda, que dice que festivos se calculan en su calendario laboral. Son los cuatro donde la
 * aplicacion habla el idioma de la gente (D24): Colombia, Estados Unidos, Francia y Australia.
 */
public enum Pais {

    /** Ley 51 de 1983 (Ley Emiliani): los festivos que caen entre semana se pasan al lunes, y los de Pascua. */
    CO,
    /** Los festivos federales (5 U.S.C. 6103), con el dia observado cuando caen en fin de semana. */
    US,
    /** Los jours feries del Code du travail (L3133-1). */
    FR,
    /** Los festivos que tienen todos los estados y territorios; los de cada estado se agregan a mano. */
    AU;

    /** El pais del idioma de la tienda (es-CO, en-US, fr-FR, en-AU); Colombia si no dice ninguno de los cuatro. */
    public static Pais delIdioma(String idioma) {
        if (idioma == null) {
            return CO;
        }
        int guion = idioma.indexOf('-');
        String region = guion < 0 ? "" : idioma.substring(guion + 1);
        for (Pais pais : values()) {
            if (pais.name().equalsIgnoreCase(region)) {
                return pais;
            }
        }
        return CO;
    }
}
