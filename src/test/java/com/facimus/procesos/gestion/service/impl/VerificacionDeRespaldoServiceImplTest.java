package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.facimus.procesos.gestion.dto.response.VerificacionDeBitacoraResponse;
import com.facimus.procesos.gestion.service.BitacoraService;
import com.facimus.procesos.gestion.service.InstantaneaDelModelo;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService.Fallo;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService.Informe;

/**
 * PR 67: la verificacion de una base restaurada sin PostgreSQL. Las versiones de cada tienda son filas en memoria
 * detras de un JdbcTemplate de mentira; la cadena y la huella las dan dobles de BitacoraService y del modelo. Con la
 * base de verdad la prueba VerificacionDeRespaldoIntegracionTest.
 */
class VerificacionDeRespaldoServiceImplTest {

    private static final String SANA = "{\"sana\":true}";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BitacoraService bitacora = mock(BitacoraService.class);
    private final InstantaneaDelModelo modelo = mock(InstantaneaDelModelo.class);
    private final Map<Long, List<Version>> versiones = new HashMap<>();
    private final List<Object> consultadas = new ArrayList<>();
    private final VerificacionDeRespaldoServiceImpl verificacion =
            new VerificacionDeRespaldoServiceImpl(jdbc, bitacora, modelo);

    /** Una fila de versiones_proceso: lo que se lee de ella. */
    private record Version(long procesoId, int numero, String huella, String definicion) {
    }

    @BeforeEach
    void lasTiendas() {
        when(jdbc.queryForList("select id from empresas order by id", Long.class)).thenReturn(List.of(3L, 8L));
        when(bitacora.verificar(3L)).thenReturn(new VerificacionDeBitacoraResponse(true, 4, null, null, null));
        when(bitacora.verificar(8L)).thenReturn(new VerificacionDeBitacoraResponse(true, 6, null, null, null));
        when(modelo.huellaDe(SANA)).thenReturn("a".repeat(64));
        doAnswer(invocacion -> {
            Object[] argumentos = invocacion.getArguments();
            Object tienda = argumentos[2] instanceof Object[] valores ? valores[0] : argumentos[2];
            consultadas.add(tienda);
            RowCallbackHandler manejador = invocacion.getArgument(1);
            for (Version version : versiones.getOrDefault((Long) tienda, List.of())) {
                manejador.processRow(fila(version));
            }
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    @DisplayName("Con cada cadena entera y cada huella igual, la base esta bien y dice cuanto reviso")
    void todoCuadra() {
        versiones.put(3L, List.of(new Version(10, 1, "a".repeat(64), SANA), new Version(10, 2, "a".repeat(64), SANA)));
        versiones.put(8L, List.of(new Version(11, 1, "a".repeat(64), SANA)));

        Informe informe = verificacion.verificar();

        assertThat(informe.correcta()).isTrue();
        assertThat(informe.fallos()).isEmpty();
        assertThat(informe.tiendas()).isEqualTo(2);
        assertThat(informe.registrosDeBitacora()).isEqualTo(10);
        assertThat(informe.versiones()).isEqualTo(3);
        assertThat(consultadas).containsExactly(3L, 8L);
    }

    @Test
    @DisplayName("Una bitacora rota es un fallo de su tienda, con el registro donde se rompe y por que")
    void bitacoraRota() {
        when(bitacora.verificar(8L)).thenReturn(new VerificacionDeBitacoraResponse(false, 2, 2L,
                "El hash no coincide con lo que dice el registro.", null));

        Informe informe = verificacion.verificar();

        assertThat(informe.correcta()).isFalse();
        assertThat(informe.fallos()).containsExactly(new Fallo(8L,
                "La bitácora se rompe en el registro 2: El hash no coincide con lo que dice el registro."));
        assertThat(informe.registrosDeBitacora()).isEqualTo(6);
    }

    @Test
    @DisplayName("Una version cuya definicion ya no da su huella es un fallo que dice cual, y se sigue con las demas")
    void huellaDistinta() {
        versiones.put(3L, List.of(new Version(10, 1, "a".repeat(64), "{\"cambiada\":true}"),
                new Version(10, 2, "a".repeat(64), SANA)));
        when(modelo.huellaDe("{\"cambiada\":true}")).thenReturn("b".repeat(64));

        Informe informe = verificacion.verificar();

        assertThat(informe.fallos()).containsExactly(new Fallo(3L,
                "La versión 1 del proceso 10 ya no tiene la huella con que se publicó."));
        assertThat(informe.versiones()).isEqualTo(2);
    }

    @Test
    @DisplayName("Una version que no se puede leer es un fallo, no un error que pare la verificacion")
    void definicionIlegible() {
        versiones.put(3L, List.of(new Version(12, 3, "a".repeat(64), "{roto")));
        versiones.put(8L, List.of(new Version(13, 1, "c".repeat(64), SANA)));
        when(modelo.huellaDe("{roto")).thenThrow(new IllegalArgumentException("JSON roto"));

        Informe informe = verificacion.verificar();

        assertThat(informe.fallos()).containsExactly(
                new Fallo(3L, "La versión 3 del proceso 12 no se puede leer: JSON roto"),
                new Fallo(8L, "La versión 1 del proceso 13 ya no tiene la huella con que se publicó."));
        assertThat(informe.versiones()).isEqualTo(2);
    }

    @Test
    @DisplayName("Sin tiendas no hay nada que revisar, y eso tambien esta bien")
    void sinTiendas() {
        when(jdbc.queryForList("select id from empresas order by id", Long.class)).thenReturn(List.of());

        Informe informe = verificacion.verificar();

        assertThat(informe.correcta()).isTrue();
        assertThat(informe.tiendas()).isZero();
        assertThat(consultadas).isEmpty();
    }

    private static ResultSet fila(Version version) throws SQLException {
        ResultSet fila = mock(ResultSet.class);
        when(fila.getLong("proceso_id")).thenReturn(version.procesoId());
        when(fila.getInt("numero")).thenReturn(version.numero());
        when(fila.getString("huella")).thenReturn(version.huella());
        when(fila.getString("definicion")).thenReturn(version.definicion());
        return fila;
    }
}
