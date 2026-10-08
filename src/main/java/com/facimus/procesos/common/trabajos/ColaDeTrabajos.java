package com.facimus.procesos.common.trabajos;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * D34: la cola de trabajos, en PostgreSQL. Encolar es una fila mas en la transaccion de quien lo pide: si esa
 * transaccion se deshace, el trabajo no existe. Tomar es un solo UPDATE sobre la fila que elige {@code FOR UPDATE SKIP
 * LOCKED}: dos trabajadores, de la misma instancia o de otra, nunca toman el mismo, y ninguno espera al otro.
 *
 * <p>Un trabajo que falla vuelve a la cola con una espera que se duplica en cada intento, hasta su maximo; despues
 * queda fallido, con su ultimo error, para que alguien lo mire. Una tienda no tiene mas de
 * {@code trabajos.maximo-en-curso-por-tienda} trabajos corriendo a la vez, asi que una exportacion grande no frena a
 * las demas. Ese tope se cuenta al tomar y sin bloquear: dos trabajadores al mismo tiempo pueden pasarlo por uno.
 */
@Component
public class ColaDeTrabajos {

    private static final Logger log = LoggerFactory.getLogger(ColaDeTrabajos.class);
    private static final int LARGO_DEL_ERROR = 2000;

    private static final String TOMAR = """
            update trabajos set estado = 'EN_CURSO', intentos = intentos + 1, tomado_por = ?, tomado_en = ?
            where id = (
                select t.id from trabajos t
                where t.estado = 'PENDIENTE' and t.disponible_desde <= ?
                  and (t.empresa_id is null or (select count(*) from trabajos c
                       where c.empresa_id = t.empresa_id and c.estado = 'EN_CURSO') < ?)
                order by t.disponible_desde, t.id
                limit 1
                for update skip locked)
            returning id, empresa_id, tipo, datos, intentos, maximo_intentos
            """;

    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final ReglasDeLaCola reglas;
    private final Map<String, ManejadorDeTrabajo> manejadores;
    private final String quienToma = ManagementFactory.getRuntimeMXBean().getName();

    public ColaDeTrabajos(JdbcTemplate jdbc, Clock reloj, ReglasDeLaCola reglas,
            ObjectProvider<ManejadorDeTrabajo> manejadores) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.reglas = reglas;
        this.manejadores = manejadores.orderedStream().collect(Collectors.toMap(ManejadorDeTrabajo::tipo,
                Function.identity(),
                (uno, otro) -> {
                    throw new IllegalStateException("Dos manejadores para el tipo de trabajo " + uno.tipo() + ".");
                }));
    }

    /** Encola un trabajo con los intentos de siempre, en la transaccion de quien lo pide. */
    public long encolar(Long empresaId, String tipo, String datos) {
        return encolar(empresaId, tipo, datos, reglas.maximoIntentos());
    }

    /** Encola un trabajo con sus propios intentos, en la transaccion de quien lo pide. */
    public long encolar(Long empresaId, String tipo, String datos, int maximoIntentos) {
        LocalDateTime ahora = ahora();
        Long id = jdbc.queryForObject("""
                insert into trabajos (empresa_id, tipo, datos, estado, intentos, maximo_intentos, disponible_desde,
                                      creado_en)
                values (?, ?, ?, 'PENDIENTE', 0, ?, ?, ?)
                returning id
                """, Long.class, empresaId, tipo, datos, maximoIntentos, ahora, ahora);
        return id == null ? 0 : id;
    }

    /** Toma un trabajo disponible y lo corre. Dice si habia alguno. */
    public boolean procesarUno() {
        Optional<Trabajo> tomado = tomar();
        tomado.ifPresent(this::correr);
        return tomado.isPresent();
    }

    Optional<Trabajo> tomar() {
        LocalDateTime ahora = ahora();
        return jdbc.query(TOMAR, (fila, numero) -> new Trabajo(fila.getLong("id"),
                        fila.getObject("empresa_id", Long.class), fila.getString("tipo"), fila.getString("datos"),
                        fila.getInt("intentos"), fila.getInt("maximo_intentos")),
                quienToma, ahora, ahora, reglas.maximoEnCursoPorTienda()).stream().findFirst();
    }

    void correr(Trabajo trabajo) {
        ManejadorDeTrabajo manejador = manejadores.get(trabajo.tipo());
        if (manejador == null) {
            terminarFallido(trabajo, "No hay quien sepa hacer trabajos de tipo " + trabajo.tipo() + ".");
            return;
        }
        try {
            manejador.ejecutar(trabajo);
            jdbc.update("update trabajos set estado = 'HECHO', terminado_en = ?, ultimo_error = null where id = ?",
                    ahora(), trabajo.id());
        } catch (RuntimeException fallo) {
            log.warn("El trabajo {} ({}) fallo en el intento {} de {}", trabajo.id(), trabajo.tipo(), trabajo.intento(),
                    trabajo.maximoIntentos(), fallo);
            if (trabajo.intento() >= trabajo.maximoIntentos()) {
                terminarFallido(trabajo, mensaje(fallo));
            } else {
                jdbc.update("""
                        update trabajos set estado = 'PENDIENTE', disponible_desde = ?, tomado_por = null,
                                            tomado_en = null, ultimo_error = ?
                        where id = ?
                        """, ahora().plus(espera(trabajo.intento())), mensaje(fallo), trabajo.id());
            }
        }
    }

    /** La espera antes del siguiente intento: la base, duplicada por cada intento ya fallido, hasta el techo. */
    Duration espera(int intentosFallidos) {
        Duration espera = reglas.esperaBase().multipliedBy(1L << Math.min(intentosFallidos - 1, 30));
        return espera.compareTo(reglas.esperaMaxima()) > 0 ? reglas.esperaMaxima() : espera;
    }

    /**
     * Un trabajo que una instancia tomo y no termino, por ejemplo porque se apago a medias, vuelve a la cola; si ya no
     * le quedan intentos, queda fallido. Devuelve cuantos encontro.
     */
    public int reabrirAtascados() {
        LocalDateTime limite = ahora().minus(reglas.atascadoTras());
        int fallidos = jdbc.update("""
                update trabajos set estado = 'FALLIDO', terminado_en = ?,
                                    ultimo_error = 'Se abandono a medias y no le quedan intentos.'
                where estado = 'EN_CURSO' and tomado_en < ? and intentos >= maximo_intentos
                """, ahora(), limite);
        int reabiertos = jdbc.update("""
                update trabajos set estado = 'PENDIENTE', tomado_por = null, tomado_en = null
                where estado = 'EN_CURSO' and tomado_en < ?
                """, limite);
        return fallidos + reabiertos;
    }

    /** D20: los trabajos terminados, hechos o fallidos, que ya pasaron su retencion. */
    public int olvidarTerminados() {
        return jdbc.update("delete from trabajos where estado in ('HECHO', 'FALLIDO') and terminado_en < ?",
                ahora().minus(reglas.retencion()));
    }

    private void terminarFallido(Trabajo trabajo, String error) {
        jdbc.update("update trabajos set estado = 'FALLIDO', terminado_en = ?, ultimo_error = ? where id = ?", ahora(),
                error, trabajo.id());
    }

    /** PostgreSQL guarda microsegundos: lo que se escribe es lo que se leera despues. */
    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }

    private static String mensaje(RuntimeException fallo) {
        String mensaje = fallo.getClass().getSimpleName() + ": " + fallo.getMessage();
        return mensaje.length() > LARGO_DEL_ERROR ? mensaje.substring(0, LARGO_DEL_ERROR) : mensaje;
    }
}
