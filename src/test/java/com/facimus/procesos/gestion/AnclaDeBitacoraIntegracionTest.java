package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.facimus.procesos.common.auditoria.SucesoAuditable;
import com.facimus.procesos.common.auditoria.SucesoAuditable.TipoDeActor;
import com.facimus.procesos.gestion.dto.response.VerificacionDeBitacoraResponse;
import com.facimus.procesos.gestion.service.AnclaDeBitacoraService;
import com.facimus.procesos.gestion.service.BitacoraService;
import com.facimus.procesos.gestion.service.EmpresaService;

/**
 * PR 61: el ancla diaria contra la bitacora de verdad. La cadena que cierra por dentro tiene que coincidir ademas con
 * la ultima ancla del almacen, y si el ancla falta o dice otra cosa, la verificacion lo dice.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnclaDeBitacoraIntegracionTest {

    private static final Path ALMACEN = carpeta();
    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);
    private static int tiendas;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("almacen.directorio", ALMACEN::toString);
    }

    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private BitacoraService bitacora;
    @Autowired
    private AnclaDeBitacoraService anclas;
    @Autowired
    private JdbcTemplate jdbc;

    private Long empresaId;

    private static Path carpeta() {
        try {
            return Files.createTempDirectory("almacen-anclas-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void registrar() {
        int numero = ++tiendas;
        String correo = "ana" + numero + "@anclas.test";
        empresaId = empresaService.registrar("Anclas " + numero, "9621" + numero + "000-1", correo, "Ana", correo,
                "un-faro-sobre-la-bahia-quieta").id();
    }

    private void anotar(String accion) {
        bitacora.anotar(new SucesoAuditable(empresaId, TipoDeActor.SISTEMA, null, accion, "PROCESO", 1L, true,
                Map.of(), null, null, null, null, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)));
    }

    private Path ancla(LocalDate dia) {
        return ALMACEN.resolve("anclas/" + empresaId + "/" + dia);
    }

    @Test
    @DisplayName("anclada, la bitacora verifica contra el ancla del dia; anclar otra vez el mismo dia no repite")
    void anclada() {
        anotar("PROCESO_CREADO");
        anotar("PROCESO_PUBLICADO");

        anclas.anclar(HOY);
        anclas.anclar(HOY);

        assertThat(jdbc.queryForObject("select count(*) from anclas_bitacora where empresa_id = ?", Integer.class,
                empresaId)).isEqualTo(1);
        assertThat(ancla(HOY)).exists();
        VerificacionDeBitacoraResponse verificacion = bitacora.verificar(empresaId);
        assertThat(verificacion.integra()).isTrue();
        assertThat(verificacion.anclaVerificada()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("lo que se anota despues del ancla no la rompe; el dia siguiente se ancla el ultimo eslabon nuevo")
    void despues() {
        anotar("PROCESO_CREADO");
        anclas.anclar(HOY);
        anotar("PROCESO_EDITADO");

        assertThat(bitacora.verificar(empresaId).integra()).isTrue();
        anclas.anclar(HOY.plusDays(1));
        assertThat(bitacora.verificar(empresaId).anclaVerificada()).isEqualTo(HOY.plusDays(1));
        assertThat(jdbc.queryForList("select secuencia from anclas_bitacora where empresa_id = ? order by dia",
                Long.class, empresaId)).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("sin nada nuevo desde la ultima ancla, el dia siguiente no se vuelve a anclar")
    void sinCambios() {
        anotar("PROCESO_CREADO");
        anclas.anclar(HOY);

        anclas.anclar(HOY.plusDays(1));

        assertThat(jdbc.queryForObject("select count(*) from anclas_bitacora where empresa_id = ?", Integer.class,
                empresaId)).isEqualTo(1);
    }

    @Test
    @DisplayName("un ancla que dice otro hash para su eslabon delata una bitacora reescrita")
    void reescrita() throws IOException {
        anotar("PROCESO_CREADO");
        anclas.anclar(HOY);
        Path archivo = ancla(HOY);
        long secuencia = jdbc.queryForObject("select secuencia from anclas_bitacora where empresa_id = ?", Long.class,
                empresaId);
        archivo.toFile().setWritable(true);
        Files.writeString(archivo, "{\"empresaId\":" + empresaId + ",\"dia\":\"" + HOY + "\",\"secuencia\":"
                + secuencia + ",\"hash\":\"" + "0".repeat(64) + "\"}");

        VerificacionDeBitacoraResponse verificacion = bitacora.verificar(empresaId);

        assertThat(verificacion.integra()).isFalse();
        assertThat(verificacion.rotaEn()).isEqualTo(secuencia);
        assertThat(verificacion.motivo()).isEqualTo("No coincide con el ancla del " + HOY + ".");
    }

    @Test
    @DisplayName("un ancla que ya no esta en el almacen tambien es una rotura")
    void sinArchivo() throws IOException {
        anotar("PROCESO_CREADO");
        anclas.anclar(HOY);
        Path archivo = ancla(HOY);
        archivo.toFile().setWritable(true);
        Files.delete(archivo);

        VerificacionDeBitacoraResponse verificacion = bitacora.verificar(empresaId);

        assertThat(verificacion.integra()).isFalse();
        assertThat(verificacion.motivo()).isEqualTo("El ancla del " + HOY + " ya no está en el almacén.");
    }

    @Test
    @DisplayName("sin anclas todavia, la verificacion es la de siempre y no nombra ninguna")
    void sinAnclas() {
        anotar("PROCESO_CREADO");

        VerificacionDeBitacoraResponse verificacion = bitacora.verificar(empresaId);

        assertThat(verificacion.integra()).isTrue();
        assertThat(verificacion.anclaVerificada()).isNull();
    }
}
