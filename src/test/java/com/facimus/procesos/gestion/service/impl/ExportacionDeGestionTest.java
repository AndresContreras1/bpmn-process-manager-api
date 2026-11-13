package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.VerificacionDeBitacoraResponse;
import com.facimus.procesos.gestion.service.BitacoraService;
import com.facimus.procesos.gestion.service.LecturaDeFilasDePrueba;
import com.facimus.procesos.gestion.service.PaqueteEnMemoria;

/**
 * PR 66: lo que gestion pone en la exportacion: que archivo sale de cada consulta, cada proceso con sus versiones, la
 * bitacora en el orden de su cadena con sus anclas, y ninguna columna de un secreto.
 */
class ExportacionDeGestionTest {

    private static final Long EMPRESA = 1L;

    private final LecturaDeFilasDePrueba lector = new LecturaDeFilasDePrueba();
    private final BitacoraService bitacoraService = mock(BitacoraService.class);
    private final Almacen almacen = mock(Almacen.class);
    private final PaqueteEnMemoria paquete = new PaqueteEnMemoria();
    private final ExportacionDeGestion parte = new ExportacionDeGestion(lector, bitacoraService, almacen);
    private final VerificacionDeBitacoraResponse verificacion =
            new VerificacionDeBitacoraResponse(true, 2, null, null, null);

    @BeforeEach
    void preparar() {
        when(bitacoraService.verificar(EMPRESA)).thenReturn(verificacion);
    }

    private static Map<String, Object> fila(Object... claveYValor) {
        Map<String, Object> fila = new LinkedHashMap<>();
        for (int i = 0; i < claveYValor.length; i += 2) {
            fila.put((String) claveYValor[i], claveYValor[i + 1]);
        }
        return fila;
    }

    @Test
    @DisplayName("cada tabla de gestion va a su archivo, en este orden, con lo que devuelve su consulta")
    void cadaTablaASuArchivo() {
        lector.con(ExportacionDeGestion.TIENDA, List.of(fila("id", 1L, "nombre", "Acme")), EMPRESA)
                .con(ExportacionDeGestion.CONFIGURACION, List.of(fila("idioma", "es-CO")), EMPRESA)
                .con(ExportacionDeGestion.PERSONAS, List.of(fila("id", 7L, "nombre", "Ana")), EMPRESA)
                .con(ExportacionDeGestion.ROLES, List.of(fila("id", 2L)), EMPRESA)
                .con(ExportacionDeGestion.MEMBRESIAS, List.of(fila("id", 3L)), EMPRESA)
                .con(ExportacionDeGestion.CARPETAS, List.of(fila("id", 4L)), EMPRESA)
                .con(ExportacionDeGestion.CONCESIONES, List.of(fila("id", 5L)), EMPRESA)
                .con(ExportacionDeGestion.HISTORIAL, List.of(fila("id", 6L)), EMPRESA)
                .con(ExportacionDeGestion.BITACORA, List.of(fila("secuencia", 1L), fila("secuencia", 2L)), EMPRESA);

        parte.escribir(EMPRESA, paquete);

        assertThat(paquete.archivos().keySet()).containsExactly("organizacion.json", "personas.json", "roles.json",
                "membresias.json", "carpetas.json", "concesiones.json", "procesos/procesos.json", "historial.json",
                "bitacora/bitacora.json", "bitacora/verificacion.json", "bitacora/anclas.json");
        assertThat(paquete.archivos().get("organizacion.json")).isEqualTo(Map.of("tienda", fila("id", 1L, "nombre",
                "Acme"), "configuracion", fila("idioma", "es-CO")));
        assertThat(paquete.filas("personas.json")).containsExactly(fila("id", 7L, "nombre", "Ana"));
        assertThat(paquete.filas("roles.json")).containsExactly(fila("id", 2L));
        assertThat(paquete.filas("membresias.json")).containsExactly(fila("id", 3L));
        assertThat(paquete.filas("carpetas.json")).containsExactly(fila("id", 4L));
        assertThat(paquete.filas("concesiones.json")).containsExactly(fila("id", 5L));
        assertThat(paquete.filas("historial.json")).containsExactly(fila("id", 6L));
        assertThat(paquete.filas("bitacora/bitacora.json")).hasSize(2);
        assertThat(paquete.archivos().get("bitacora/verificacion.json")).isSameAs(verificacion);
        assertThat(lector.cursores()).containsEntry(ExportacionDeGestion.BITACORA, "secuencia")
                .containsEntry(ExportacionDeGestion.PERSONAS, "id");
    }

    @Test
    @DisplayName("una tienda sin fila ni configuracion sale vacia, no nula")
    void organizacionVacia() {
        parte.escribir(EMPRESA, paquete);

        assertThat(paquete.archivos().get("organizacion.json")).isEqualTo(Map.of("tienda", Map.of(),
                "configuracion", Map.of()));
    }

    @Test
    @DisplayName("cada proceso tiene su carpeta con su ficha, y cada version su JSON, su BPMN y su dibujo si lo tiene; "
            + "un proceso sin versiones no tiene carpeta de versiones")
    void procesosYVersiones() {
        lector.con(ExportacionDeGestion.PROCESOS, List.of(fila("id", 7L, "nombre", "Order Fulfillment"),
                fila("id", 8L, "nombre", "Devoluciones")), EMPRESA)
                .con(ExportacionDeGestion.VERSIONES, List.of(fila("numero", 1, "huella", "a"),
                        fila("numero", 2, "huella", "b")), EMPRESA, 7L)
                .con(ExportacionDeGestion.CONTENIDO_DE_LA_VERSION, List.of(fila("definicion", "{\"v\":1}",
                        "modeloBpmn", "<v1/>", "svg", null)), EMPRESA, 7L, 1)
                .con(ExportacionDeGestion.CONTENIDO_DE_LA_VERSION, List.of(fila("definicion", "{\"v\":2}",
                        "modeloBpmn", null, "svg", "<svg/>")), EMPRESA, 7L, 2);

        parte.escribir(EMPRESA, paquete);

        assertThat(paquete.archivos()).containsEntry("procesos/7-order-fulfillment/proceso.json",
                fila("id", 7L, "nombre", "Order Fulfillment"))
                .containsEntry("procesos/7-order-fulfillment/versiones/v1.json", "{\"v\":1}")
                .containsEntry("procesos/7-order-fulfillment/versiones/v1.bpmn", "<v1/>")
                .containsEntry("procesos/7-order-fulfillment/versiones/v2.json", "{\"v\":2}")
                .containsEntry("procesos/7-order-fulfillment/versiones/v2.svg", "<svg/>")
                .containsKey("procesos/8-devoluciones/proceso.json")
                .doesNotContainKeys("procesos/7-order-fulfillment/versiones/v1.svg",
                        "procesos/7-order-fulfillment/versiones/v2.bpmn",
                        "procesos/8-devoluciones/versiones/versiones.json");
        assertThat(paquete.archivos().get("procesos/7-order-fulfillment/versiones/versiones.json"))
                .isEqualTo(List.of(fila("numero", 1, "huella", "a"), fila("numero", 2, "huella", "b")));
        assertThat(paquete.archivos().get("procesos/procesos.json")).isEqualTo(List.of(fila("id", 7L, "nombre",
                "Order Fulfillment"), fila("id", 8L, "nombre", "Devoluciones")));
    }

    @Test
    @DisplayName("cada ancla sale en su fila y con el archivo del almacen inmutable; una que ya no esta se salta")
    void anclas() {
        lector.con(ExportacionDeGestion.ANCLAS, List.of(fila("dia", "2026-11-10", "clave", "anclas/1/2026-11-10"),
                fila("dia", "2026-11-11", "clave", "anclas/1/2026-11-11")), EMPRESA);
        when(almacen.abrirInmutable("anclas/1/2026-11-10"))
                .thenThrow(new ArchivoNoEncontradoException("anclas/1/2026-11-10"));
        when(almacen.abrirInmutable("anclas/1/2026-11-11"))
                .thenReturn(new ByteArrayInputStream("{\"hash\":\"h\"}".getBytes(StandardCharsets.UTF_8)));

        parte.escribir(EMPRESA, paquete);

        assertThat(paquete.archivos()).doesNotContainKey("bitacora/anclas/2026-11-10.json");
        assertThat(new String((byte[]) paquete.archivos().get("bitacora/anclas/2026-11-11.json"),
                StandardCharsets.UTF_8)).isEqualTo("{\"hash\":\"h\"}");
        assertThat(paquete.filas("bitacora/anclas.json")).hasSize(2);
    }

    @Test
    @DisplayName("si el almacen no se deja leer, la exportacion falla en vez de salir sin el ancla")
    void anclaIlegible() {
        lector.con(ExportacionDeGestion.ANCLAS, List.of(fila("dia", "2026-11-10", "clave", "anclas/1/2026-11-10")),
                EMPRESA);
        when(almacen.abrirInmutable("anclas/1/2026-11-10")).thenReturn(new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public void close() throws IOException {
                throw new IOException("disco roto");
            }
        });

        assertThatThrownBy(() -> parte.escribir(EMPRESA, paquete)).isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("anclas/1/2026-11-10");
    }

    @Test
    @DisplayName("la bitacora sale con cada campo de su hash, en su orden, y su hash")
    void bitacoraConCadaCampo() {
        assertThat(ExportacionDeGestion.BITACORA).contains("select empresa_id, secuencia, momento, actor_tipo, "
                + "actor_id, accion, recurso, recurso_id, exito, ip,", "user_agent, request_id, trace_id, detalle, "
                        + "hash_anterior, hash", "order by secuencia");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"password_hash", "webauthn_id", "secreto", "codigo_hash", "token_hash", "token",
        "credencial", "clave_publica", "segundos_factores", "codigos_de_recuperacion", "passkeys", "sesiones",
        "refresh_tokens", "enlaces_de_un_uso", "identidades_externas"})
    @DisplayName("ninguna consulta nombra una columna o una tabla de secretos")
    void sinSecretos(String secreto) {
        Map<String, String> consultas = new HashMap<>();
        consultas.put("TIENDA", ExportacionDeGestion.TIENDA);
        consultas.put("CONFIGURACION", ExportacionDeGestion.CONFIGURACION);
        consultas.put("PERSONAS", ExportacionDeGestion.PERSONAS);
        consultas.put("ROLES", ExportacionDeGestion.ROLES);
        consultas.put("MEMBRESIAS", ExportacionDeGestion.MEMBRESIAS);
        consultas.put("CARPETAS", ExportacionDeGestion.CARPETAS);
        consultas.put("CONCESIONES", ExportacionDeGestion.CONCESIONES);
        consultas.put("PROCESOS", ExportacionDeGestion.PROCESOS);
        consultas.put("VERSIONES", ExportacionDeGestion.VERSIONES);
        consultas.put("HISTORIAL", ExportacionDeGestion.HISTORIAL);
        consultas.put("BITACORA", ExportacionDeGestion.BITACORA);
        consultas.put("ANCLAS", ExportacionDeGestion.ANCLAS);
        assertThat(consultas).allSatisfy((nombre, consulta) -> assertThat(consulta).as(nombre)
                .doesNotContainPattern("[^a-z_]" + secreto + "[^a-z_]"));
    }
}
