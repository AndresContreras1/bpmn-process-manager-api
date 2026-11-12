package com.facimus.procesos.gestion.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.common.almacen.ClaveDeAlmacen;
import com.facimus.procesos.gestion.service.AnclaDeBitacoraService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 61: el ancla de cada tienda es un JSON pequeno (tienda, dia, secuencia y hash del ultimo eslabon) guardado con
 * Object Lock en modo compliance: ni la API ni quien tenga sus credenciales lo puede borrar antes de su fecha. La fila
 * de {@code anclas_bitacora} dice donde esta, pero la verificacion lee el archivo: una fila se puede cambiar.
 */
@Service
public class AnclaDeBitacoraServiceImpl implements AnclaDeBitacoraService {

    private static final Logger log = LoggerFactory.getLogger(AnclaDeBitacoraServiceImpl.class);

    /** El ultimo eslabon de cada tienda que no tiene ancla hoy ni una con ese mismo eslabon. */
    private static final String POR_ANCLAR = """
            select b.empresa_id, b.secuencia, b.hash from bitacora b
            where b.secuencia = (select max(u.secuencia) from bitacora u where u.empresa_id = b.empresa_id)
              and not exists (select 1 from anclas_bitacora a
                              where a.empresa_id = b.empresa_id and (a.dia = ? or a.secuencia = b.secuencia))
            order by b.empresa_id
            """;

    private final JdbcTemplate jdbc;
    private final Almacen almacen;
    private final JsonMapper json;
    private final Clock reloj;
    private final Duration retencion;

    public AnclaDeBitacoraServiceImpl(JdbcTemplate jdbc, Almacen almacen, JsonMapper json, Clock reloj,
            @Value("${bitacora.ancla.retencion:400d}") Duration retencion) {
        this.jdbc = jdbc;
        this.almacen = almacen;
        this.json = json;
        this.reloj = reloj;
        this.retencion = retencion;
    }

    @Override
    public int anclar(LocalDate dia) {
        List<Map<String, Object>> pendientes = jdbc.queryForList(POR_ANCLAR, dia);
        int ancladas = 0;
        for (Map<String, Object> pendiente : pendientes) {
            long empresaId = ((Number) pendiente.get("empresa_id")).longValue();
            try {
                anclar(empresaId, dia, ((Number) pendiente.get("secuencia")).longValue(),
                        (String) pendiente.get("hash"));
                ancladas++;
            } catch (RuntimeException e) {
                // Una tienda que falla no deja sin ancla a las demas; manana se intenta otra vez
                log.warn("No se pudo anclar la bitacora de la tienda {}: {}", empresaId, e.getMessage());
            }
        }
        return ancladas;
    }

    private void anclar(long empresaId, LocalDate dia, long secuencia, String hash) {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("empresaId", empresaId);
        contenido.put("dia", dia.toString());
        contenido.put("secuencia", secuencia);
        contenido.put("hash", hash);
        String clave = ClaveDeAlmacen.deAncla(empresaId, dia.toString());
        Instant hasta = reloj.instant().plus(retencion);
        boolean inmutable = almacen.guardarInmutable(clave,
                json.writeValueAsString(contenido).getBytes(StandardCharsets.UTF_8), hasta);
        jdbc.update("""
                insert into anclas_bitacora (empresa_id, dia, secuencia, hash, clave, inmutable, fecha)
                values (?, ?, ?, ?, ?, ?, ?)
                """, empresaId, dia, secuencia, hash, clave, inmutable,
                LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
    }

    @Override
    public Optional<Ancla> ultima(Long empresaId) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select dia, secuencia, clave from anclas_bitacora where empresa_id = ? order by dia desc limit 1
                """, empresaId);
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> fila = filas.getFirst();
        LocalDate dia = ((Date) fila.get("dia")).toLocalDate();
        long secuencia = ((Number) fila.get("secuencia")).longValue();
        try (InputStream contenido = almacen.abrir((String) fila.get("clave"))) {
            JsonNode ancla = json.readTree(contenido);
            return Optional.of(new Ancla(dia, ancla.get("secuencia").asLong(), ancla.get("hash").asString()));
        } catch (ArchivoNoEncontradoException e) {
            return Optional.of(new Ancla(dia, secuencia, null));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el ancla de la tienda " + empresaId, e);
        }
    }
}
