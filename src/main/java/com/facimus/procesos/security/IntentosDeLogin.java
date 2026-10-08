package com.facimus.procesos.security;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-03 y D34: los intentos fallidos de login, en PostgreSQL. Todas las instancias de la API cuentan los mismos, asi
 * que repartir los intentos entre ellas no da intentos de mas. Una fila por fallo, con su clave (el correo en
 * minusculas y la IP) y su fecha; la purga nocturna se lleva lo que ya salio de la ventana.
 *
 * <p>Contar y anotar no van en una sola transaccion: dos fallos al mismo tiempo pueden pasar el maximo por uno. Para
 * un limite de intentos eso no cambia nada.
 */
public class IntentosDeLogin implements LimiteDeIntentos {

    private final JdbcTemplate jdbc;
    private final int maximo;
    private final Duration ventana;
    private final Clock reloj;

    public IntentosDeLogin(JdbcTemplate jdbc, int maximo, Duration ventana, Clock reloj) {
        this.jdbc = jdbc;
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    @Override
    public Optional<Duration> espera(String clave) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        return jdbc.queryForObject("select count(*), min(fecha) from intentos_login where clave = ? and fecha > ?",
                (fila, numero) -> {
                    if (fila.getLong(1) < maximo) {
                        return Optional.empty();
                    }
                    LocalDateTime masViejo = fila.getObject(2, LocalDateTime.class);
                    return Optional.of(Duration.between(ahora, masViejo.plus(ventana)));
                }, clave, ahora.minus(ventana));
    }

    @Override
    public void registrar(String clave) {
        jdbc.update("insert into intentos_login (clave, fecha) values (?, ?)", clave, LocalDateTime.now(reloj));
    }

    @Override
    public void reiniciar(String clave) {
        jdbc.update("delete from intentos_login where clave = ?", clave);
    }

    /** D20: un intento que salio de la ventana ya no cuenta para nadie. */
    public int olvidarVencidos() {
        return jdbc.update("delete from intentos_login where fecha <= ?", LocalDateTime.now(reloj).minus(ventana));
    }
}
