package com.facimus.procesos.common.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 62: el dibujo de una version queda con lo que un diagrama usa y nada mas. */
class SaneadorDeSvgTest {

    private static String sanear(String svg) {
        return SaneadorDeSvg.sanear(svg.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("lo de un diagrama de bpmn-js se queda: formas, texto, marcadores y estilos internos")
    void diagrama() {
        String limpio = sanear("""
                <svg xmlns="http://www.w3.org/2000/svg" width="300" height="100" viewBox="0 0 300 100" version="1.1">
                  <defs><marker id="flecha" refX="11" refY="10" markerWidth="20" markerHeight="20" orient="auto">
                    <path d="M 1 5 L 11 10 L 1 15 Z" style="fill: black;"/></marker></defs>
                  <g class="djs-group" data-element-id="Activity_1" transform="matrix(1 0 0 1 10 10)">
                    <rect x="0" y="0" width="100" height="80" rx="10" style="stroke: black; fill: white;"/>
                    <text font-family="Arial" font-size="12"><tspan x="20" y="40">Empacar</tspan></text>
                  </g>
                  <polyline points="110,50 200,50" style="marker-end: url(#flecha);"/>
                </svg>
                """);

        assertThat(limpio).contains("<svg", "viewBox=\"0 0 300 100\"", "<marker", "id=\"flecha\"", "<rect",
                "data-element-id=\"Activity_1\"", "<tspan", "Empacar", "url(#flecha)", "<polyline");
    }

    @Test
    @DisplayName("scripts, manejadores de eventos, objetos HTML, imagenes, use y enlaces se van")
    void peligroso() {
        String limpio = sanear("""
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" onload="alert(1)">
                  <script>alert(document.cookie)</script>
                  <rect width="10" height="10" onclick="alert(2)"/>
                  <foreignObject width="10" height="10"><div xmlns="http://www.w3.org/1999/xhtml">x</div></foreignObject>
                  <image xlink:href="https://example.com/p.png" width="1" height="1"/>
                  <use xlink:href="#a"/>
                  <a xlink:href="javascript:alert(3)"><text>clic</text></a>
                  <animate attributeName="x" to="1"/>
                </svg>
                """);

        assertThat(limpio).doesNotContain("script", "onload", "onclick", "foreignObject", "<image", "<use", "<a ",
                "href", "javascript", "animate", "example.com").contains("<rect");
    }

    @Test
    @DisplayName("un estilo con una URL de afuera o con algo que un navegador viejo ejecuta pierde el atributo")
    void estilos() {
        String limpio = sanear("""
                <svg xmlns="http://www.w3.org/2000/svg">
                  <rect width="1" height="1" style="fill: url(https://example.com/a.svg#x)"/>
                  <rect width="2" height="2" style="background: expression(alert(1))"/>
                  <rect width="3" height="3" fill="url(#bueno) , url(http://example.com/x)"/>
                  <rect width="4" height="4" fill="url('#bueno')"/>
                </svg>
                """);

        assertThat(limpio).doesNotContain("example.com", "expression").contains("width=\"4\"", "url('#bueno')");
    }

    @Test
    @DisplayName("lo que no es un SVG, o trae un DOCTYPE, no se acepta")
    void rechazos() {
        assertThatThrownBy(() -> sanear("<html xmlns=\"http://www.w3.org/1999/xhtml\"/>"))
                .isInstanceOf(XmlRechazadoException.class).hasMessage("El dibujo no es un SVG.");
        assertThatThrownBy(() -> sanear("<svg/>")).isInstanceOf(XmlRechazadoException.class);
        assertThatThrownBy(() -> sanear("<!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>&x;</text></svg>"))
                .isInstanceOf(XmlRechazadoException.class);
        assertThatThrownBy(() -> sanear("no es xml")).isInstanceOf(XmlRechazadoException.class);
    }
}
