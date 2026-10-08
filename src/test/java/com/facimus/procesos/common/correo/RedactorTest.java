package com.facimus.procesos.common.correo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Las plantillas de los correos, en sus tres idiomas, con lo que cada una tiene que decir. */
class RedactorTest {

    private static final String ENLACE = "https://tienda.example/restablecer-clave#token=abc123";

    private final Redactor redactor = new Redactor();

    @ParameterizedTest(name = "{0} en {1}: {2}")
    @CsvSource(delimiter = '|', value = {
            "verificar-correo | es | Verifica tu correo | 48 horas",
            "verificar-correo | en | Verify your e-mail | 48 hours",
            "verificar-correo | fr | Vérifiez votre adresse e-mail | 48 heures",
            "recuperar-clave  | es | Restablece tu contraseña | 15 minutos",
            "recuperar-clave  | en | Reset your password | 15 minutes",
            "recuperar-clave  | fr | Réinitialisez votre mot de passe | 15 minutes",
            "invitacion       | es | Te invitaron a una tienda en BPMN Process Manager | 7 días",
            "invitacion       | en | You were invited to a store on BPMN Process Manager | 7 days",
            "invitacion       | fr | Vous êtes invité à une boutique sur BPMN Process Manager | 7 jours"})
    @DisplayName("Cada correo sale en su idioma, con el enlace y el plazo en el texto y en el HTML")
    void cadaPlantilla_enCadaIdioma(String plantilla, String idioma, String asunto, String plazo) {
        CorreoSaliente correo = redactor.redactar(plantilla, Locale.of(idioma), "ana@acme.com", datos("Ana"));

        assertThat(correo.para()).isEqualTo("ana@acme.com");
        assertThat(correo.asunto()).isEqualTo(asunto);
        assertThat(correo.texto()).contains(ENLACE, plazo);
        assertThat(correo.html()).contains("href=\"" + ENLACE + "\"", plazo, "lang=\"" + idioma + "\"");
    }

    @Test
    @DisplayName("Un idioma que los correos no hablan recibe el espanol")
    void idiomaDesconocido_recibeElEspanol() {
        CorreoSaliente correo = redactor.redactar("recuperar-clave", Locale.of("de"), "ana@acme.com", datos("Ana"));

        assertThat(correo.asunto()).isEqualTo("Restablece tu contraseña");
        assertThat(Redactor.idioma(null)).isEqualTo(Locale.of("es"));
    }

    @Test
    @DisplayName("Lo que viene de afuera, como el nombre de una tienda, sale escapado en el HTML")
    void datosDeAfuera_salenEscapadosEnElHtml() {
        Map<String, Object> datos = Map.of("enlace", ENLACE, "dias", 7L, "quienInvita", "<b>Ana</b>",
                "tienda", "<script>alert(1)</script>");

        CorreoSaliente correo = redactor.redactar("invitacion", Locale.of("es"), "luis@acme.com", datos);

        assertThat(correo.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt;", "&lt;b&gt;Ana&lt;/b&gt;")
                .doesNotContain("<script>", "<b>Ana");
    }

    @Test
    @DisplayName("El enlace no llega a un log: el toString del correo no lleva su cuerpo")
    void toString_noLlevaElCuerpo() {
        CorreoSaliente correo = redactor.redactar("recuperar-clave", Locale.of("es"), "ana@acme.com", datos("Ana"));

        assertThat(correo.toString()).contains("ana@acme.com").doesNotContain("abc123");
    }

    private static Map<String, Object> datos(String nombre) {
        return Map.of("nombre", nombre, "enlace", ENLACE, "horas", 48L, "minutos", 15L, "dias", 7L,
                "tienda", "Acme Store", "quienInvita", "Ana");
    }
}
