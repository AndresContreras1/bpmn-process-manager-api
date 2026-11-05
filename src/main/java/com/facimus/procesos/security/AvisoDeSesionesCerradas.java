package com.facimus.procesos.security;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.facimus.procesos.gestion.event.SesionesCerradas;

/**
 * D34: avisa a cada instancia de la API, con NOTIFY de PostgreSQL, que se cerraron sesiones. El aviso sale dentro de
 * la transaccion que las cierra, y PostgreSQL solo lo entrega si esa transaccion se confirma: una sesion que al final
 * sigue abierta no se revoca en ninguna parte. Lo recibe {@code config.EscuchaDeSesionesCerradas} en cada instancia,
 * tambien en la que lo envio.
 */
@Component
public class AvisoDeSesionesCerradas {

    /** El canal de PostgreSQL por el que viajan los codigos de las sesiones cerradas, separados por comas. */
    public static final String CANAL = "sesiones_cerradas";

    /** Un aviso lleva hasta 8000 bytes: cien codigos de 36 caracteres caben con holgura. */
    static final int CODIGOS_POR_AVISO = 100;

    private final JdbcTemplate jdbc;

    public AvisoDeSesionesCerradas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void avisar(SesionesCerradas evento) {
        List<String> codigos = evento.codigos();
        for (int desde = 0; desde < codigos.size(); desde += CODIGOS_POR_AVISO) {
            String lote = String.join(",", codigos.subList(desde, Math.min(desde + CODIGOS_POR_AVISO, codigos.size())));
            jdbc.query("select pg_notify(?, ?)", resultado -> null, CANAL, lote);
        }
    }
}
