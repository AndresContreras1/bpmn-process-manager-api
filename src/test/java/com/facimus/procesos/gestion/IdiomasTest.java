package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.gestion.service.Idiomas;

/** F12: que idioma de la web le toca a cada peticion, y con que hora empieza una organizacion. */
class IdiomasTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', nullValues = "nada", value = {
            "es-CO                         | es-CO",
            "es-MX,es;q=0.9                | es-CO",
            "en-US,en;q=0.9                | en-US",
            "en-GB                         | en-US",
            "en-AU,en;q=0.8                | en-AU",
            "en-NZ                         | en-AU",
            "fr-CA,fr;q=0.9,en;q=0.5       | fr-FR",
            "de-DE,fr;q=0.7,en;q=0.9       | en-US",
            "de-DE,ja;q=0.5                | es-CO",
            "*                             | es-CO",
            "nada                          | es-CO",
            "esto no es una cabecera;q=x   | es-CO"})
    @DisplayName("Cada Accept-Language da el idioma de la web mas parecido, en el orden de preferencia que trae")
    void deLaCabecera(String cabecera, String idioma) {
        assertThat(Idiomas.deLaCabecera(cabecera)).isEqualTo(idioma);
    }

    @Test
    @DisplayName("Los correos que esperaban en la cola antes de F12 decian solo la lengua: siguen teniendo su idioma")
    void deLaWeb_desdeLaLengua() {
        assertThat(Idiomas.deLaWeb("fr")).isEqualTo("fr-FR");
        assertThat(Idiomas.deLaWeb("en")).isEqualTo("en-US");
        assertThat(Idiomas.deLaWeb("es")).isEqualTo("es-CO");
        assertThat(Idiomas.deLaWeb("en-AU")).isEqualTo("en-AU");
        assertThat(Idiomas.deLaWeb(null)).isEqualTo("es-CO");
    }

    @Test
    @DisplayName("Una organizacion empieza en la hora de la capital de su idioma, y una zona se escribe como en IANA")
    void zonas() {
        assertThat(Idiomas.zonaPorDefecto("en-AU")).isEqualTo("Australia/Sydney");
        assertThat(Idiomas.zonaPorDefecto("fr-FR")).isEqualTo("Europe/Paris");
        assertThat(Idiomas.zonaPorDefecto("xx-YY")).isEqualTo("America/Bogota");
        assertThat(Idiomas.validarZona(" Europe/Paris ")).isEqualTo("Europe/Paris");
        assertThat(Idiomas.validarZona("UTC")).isEqualTo("UTC");
        assertThatThrownBy(() -> Idiomas.validarZona("-05:00")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> Idiomas.validarZona("Atlantida/Capital")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> Idiomas.validar("pt-BR")).isInstanceOf(SolicitudInvalidaException.class);
    }
}
