package com.facimus.procesos.common.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.facimus.procesos.common.api.ApiExceptionHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * PR 52: lo que un XML hostil intenta, y que no lo logra. Un servidor HTTP local hace de atacante: si alguna lectura
 * siguiera una entidad, un DTD, un XInclude, un xsl:include o un xs:import hacia el, lo contaria, y al final tiene
 * que haber contado cero.
 */
class SecureXmlTest {

    private static final LimitesXml LIMITES = LimitesXml.BPMN;
    private static final AtomicInteger PETICIONES = new AtomicInteger();
    private static HttpServer atacante;
    private static String url;

    @BeforeAll
    static void levantarAtacante() throws IOException {
        atacante = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        atacante.createContext("/", intercambio -> {
            PETICIONES.incrementAndGet();
            byte[] cuerpo = "<!ENTITY robado 'secreto'>".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(200, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        atacante.start();
        url = "http://127.0.0.1:" + atacante.getAddress().getPort();
    }

    @AfterEach
    void elAtacanteNoRecibioNada() {
        assertThat(PETICIONES.get()).as("peticiones que recibio el servidor del atacante").isZero();
    }

    @AfterAll
    static void apagarAtacante() {
        atacante.stop(0);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        // XXE por archivo
        "<!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><r>&x;</r>",
        // XXE por URL
        "<!DOCTYPE r [<!ENTITY x SYSTEM \"@URL@/xxe\">]><r>&x;</r>",
        // Entidad de parametro, que trae su propio DTD
        "<!DOCTYPE r [<!ENTITY % p SYSTEM \"@URL@/p.dtd\"> %p;]><r>&robado;</r>",
        // DTD externo
        "<!DOCTYPE r SYSTEM \"@URL@/d.dtd\"><r/>",
        // Billion laughs
        "<!DOCTYPE r [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">"
                + "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\"><!ENTITY d \"&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;\">]>"
                + "<r>&d;&d;&d;&d;&d;&d;&d;&d;&d;&d;</r>",
        // Una entidad interna inocente tambien es un DTD
        "<!DOCTYPE r [<!ENTITY x \"hola\">]><r>&x;</r>"})
    @DisplayName("Un DOCTYPE se rechaza, con entidades internas, externas o de parametro, sin llamar a nadie")
    void doctype_seRechaza(String plantilla) {
        String xml = plantilla.replace("@URL@", url);

        assertThatThrownBy(() -> SecureXml.comprobar(entrada(xml), LIMITES))
                .isInstanceOf(XmlRechazadoException.class);
        assertThatThrownBy(() -> SecureXml.documentos().parse(entrada(xml))).isInstanceOf(SAXException.class);
        assertThatThrownBy(() -> SecureXml.sax().parse(entrada(xml), new DefaultHandler()))
                .isInstanceOf(SAXException.class);
    }

    @Test
    @DisplayName("La explosion cuadratica, una entidad grande repetida miles de veces, tampoco pasa")
    void explosionCuadratica() {
        String grande = "x".repeat(50_000);
        String xml = "<!DOCTYPE r [<!ENTITY g \"" + grande + "\">]><r>" + "&g;".repeat(10_000) + "</r>";

        assertThatThrownBy(() -> SecureXml.comprobar(entrada(xml), LIMITES)).isInstanceOf(XmlRechazadoException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"UTF-7", "ISO-8859-1", "EBCDIC-CP-US", "x-unknown"})
    @DisplayName("Solo UTF-8 o UTF-16: una codificacion rara es como se le esconde un DOCTYPE a un filtro")
    void codificacionesRaras(String codificacion) {
        String xml = "<?xml version=\"1.0\" encoding=\"" + codificacion + "\"?><r/>";

        assertThatThrownBy(() -> SecureXml.comprobar(entrada(xml), LIMITES)).isInstanceOf(XmlRechazadoException.class);
    }

    @Test
    @DisplayName("UTF-16 con su BOM se lee, y un BPMN normal tambien, como DOM")
    void loNormal_seLee() throws IOException {
        byte[] utf16 = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><r a=\"1\">hola</r>".getBytes(StandardCharsets.UTF_16);
        assertThat(SecureXml.comprobar(new ByteArrayInputStream(utf16), LIMITES)).isEqualTo(utf16);

        Document documento = SecureXml.documento(entrada("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1">
                  <bpmn:process id="Process_1"><bpmn:task id="Activity_1" name="Pick and pack"/></bpmn:process>
                </bpmn:definitions>
                """), LIMITES);
        assertThat(documento.getDocumentElement().getLocalName()).isEqualTo("definitions");
    }

    @Test
    @DisplayName("XInclude esta apagado: el include queda como un elemento mas y no se va a buscar")
    void xinclude_noSeSigue() {
        String xml = "<r xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"" + url + "/x\"/></r>";

        Document documento = SecureXml.documento(entrada(xml), LIMITES);

        assertThat(documento.getElementsByTagNameNS("http://www.w3.org/2001/XInclude", "include").getLength())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Una hoja de estilo con xsl:include externo no se compila, y no se va a buscar")
    void xslInclude_noSeSigue() {
        String xslt = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:include href=\"" + url + "/x.xsl\"/></xsl:stylesheet>";

        assertThatThrownBy(() -> SecureXml.transformadores().newTransformer(new StreamSource(new StringReader(xslt))))
                .isInstanceOf(TransformerConfigurationException.class);
    }

    @Test
    @DisplayName("Un esquema que importa otro de una URL no se carga, y no se va a buscar")
    void xsImport_noSeSigue() {
        String xsd = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
                + "<xs:import namespace=\"urn:otro\" schemaLocation=\"" + url + "/x.xsd\"/>"
                + "<xs:element name=\"r\" type=\"xs:string\"/></xs:schema>";

        assertThatThrownBy(() -> SecureXml.esquemas().newSchema(new StreamSource(new StringReader(xsd))))
                .isInstanceOf(SAXException.class);
    }

    @Test
    @DisplayName("Cada limite corta: tamano, elementos, profundidad, atributos, texto y tiempo")
    void limites() {
        LimitesXml chicos = new LimitesXml(1024, 5, 3, 2, 10, Duration.ofSeconds(5));

        assertThatThrownBy(() -> SecureXml.comprobar(entrada("<r>" + "x".repeat(2000) + "</r>"), chicos))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("bytes");
        assertThatThrownBy(() -> SecureXml.comprobar(entrada("<r><a/><a/><a/><a/><a/></r>"), chicos))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("elementos");
        assertThatThrownBy(() -> SecureXml.comprobar(entrada("<r><a><b><c/></b></a></r>"), chicos))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("niveles");
        assertThatThrownBy(() -> SecureXml.comprobar(entrada("<r a=\"1\" b=\"2\" c=\"3\"/>"), chicos))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("atributos");
        assertThatThrownBy(() -> SecureXml.comprobar(entrada("<r>texto demasiado largo</r>"), chicos))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("caracteres");
        assertThatThrownBy(() -> SecureXml.comprobar(entrada("<r a=\"atributo demasiado largo\"/>"), chicos))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("caracteres");
        LimitesXml sinTiempo = new LimitesXml(1024, 5, 3, 2, 10, Duration.ZERO);
        assertThatThrownBy(() -> SecureXml.comprobar(entrada("<r><a/></r>"), sinTiempo))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("segundos");
    }

    @Test
    @DisplayName("Lo mal formado tambien se rechaza, con su motivo")
    void malFormado() {
        assertThatThrownBy(() -> SecureXml.comprobar(entrada("<r><a></r>"), LIMITES))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("no se pudo leer");
    }

    @Test
    @DisplayName("Por la API, un XML rechazado es un 400 con el motivo")
    void porLaApi_es400() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LectorDePrueba())
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/xml").contentType(MediaType.APPLICATION_XML)
                        .content("<!DOCTYPE r [<!ENTITY x SYSTEM \"" + url + "/xxe\">]><r>&x;</r>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("DOCTYPE")));
        mockMvc.perform(post("/xml").contentType(MediaType.APPLICATION_XML).content("<r>bien</r>"))
                .andExpect(status().isOk());
    }

    private static InputStream entrada(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** Un endpoint que lee XML como lo hara el importador de BPMN: con SecureXml y sus limites. */
    @RestController
    static class LectorDePrueba {

        @PostMapping(value = "/xml", consumes = MediaType.APPLICATION_XML_VALUE)
        String leer(@RequestBody byte[] cuerpo) {
            return SecureXml.documento(new ByteArrayInputStream(cuerpo), LIMITES).getDocumentElement().getTagName();
        }
    }
}
