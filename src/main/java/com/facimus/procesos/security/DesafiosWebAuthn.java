package com.facimus.procesos.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.Huella;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 49b: el desafio de cada ceremonia de WebAuthn vive en PostgreSQL, como las peticiones OIDC: la API no tiene sesion
 * HTTP y la respuesta puede llegar a otra instancia. El navegador recibe un id de 256 bits del que solo se guarda el
 * SHA-256. Cada desafio sirve una vez, para su proposito y su persona, y vence a los cinco minutos, lo que tambien le
 * dice al navegador que espere.
 */
@Component
public class DesafiosWebAuthn {

    /** Lo que se espera a que la persona toque su dispositivo. */
    static final Duration VIGENCIA = Duration.ofMinutes(5);

    private static final int BYTES_DEL_ID = 32;

    /** Para que sirve un desafio: uno de entrar no sirve para registrar, ni al reves. */
    enum Proposito {
        REGISTRO,
        VERIFICACION,
        ENTRADA
    }

    /**
     * Lo que hace falta para rehacer las opciones al verificar: el desafio, la persona del registro y las credenciales
     * que se le ofrecieron al dispositivo, todo en base64url.
     */
    record Guardado(String desafio, String handle, String nombre, String nombreVisible, List<String> credenciales) {
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final SecureRandom aleatorio = new SecureRandom();

    public DesafiosWebAuthn(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Guarda un desafio y devuelve su id, el que el navegador manda de vuelta con la respuesta del dispositivo. */
    String guardar(Proposito proposito, Long usuarioId, Guardado guardado) {
        byte[] bytes = new byte[BYTES_DEL_ID];
        aleatorio.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("insert into desafios_webauthn (id_hash, proposito, usuario_id, opciones, creado_en) "
                + "values (?, ?, ?, ?, ?)", Huella.de(id), proposito.name(), usuarioId,
                json.writeValueAsString(guardado), ahora());
        return id;
    }

    /**
     * El desafio, si es de este proposito y de esta persona y no vencio. Se borra al tomarlo: de dos respuestas con el
     * mismo id, solo una lo encuentra.
     */
    Optional<Guardado> tomar(String id, Proposito proposito, Long usuarioId) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        List<String> opciones = jdbc.queryForList("delete from desafios_webauthn where id_hash = ? and proposito = ? "
                + "and usuario_id is not distinct from ? and creado_en >= ? returning opciones", String.class,
                Huella.de(id.strip()), proposito.name(), usuarioId, ahora().minus(VIGENCIA));
        return opciones.stream().findFirst().map(guardado -> json.readValue(guardado, Guardado.class));
    }

    /** D20: lo que vencio sin que nadie lo usara. Lo corre la purga de la noche. */
    public int olvidarVencidos() {
        return jdbc.update("delete from desafios_webauthn where creado_en < ?", ahora().minus(VIGENCIA));
    }

    /** Como guarda Hibernate las fechas: en UTC. */
    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
