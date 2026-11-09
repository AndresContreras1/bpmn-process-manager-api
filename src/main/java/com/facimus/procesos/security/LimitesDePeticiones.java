package com.facimus.procesos.security;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import io.github.bucket4j.postgresql.PostgreSQLSelectForUpdateBasedProxyManager;

/**
 * PR 32: cubos de fichas (Bucket4j) con su estado en PostgreSQL. Cada peticion gasta una ficha del cubo de su clase y
 * de quien la hace, y el cubo se rellena de a poco hasta su capacidad. Como el estado vive en la base, todas las
 * instancias gastan del mismo cubo: repartir las peticiones entre ellas no da fichas de mas. Bucket4j toma la fila
 * con {@code SELECT ... FOR UPDATE}, asi que dos peticiones a la vez no gastan la misma ficha.
 */
@Component
public class LimitesDePeticiones {

    /** Lo que se guarda un cubo despues de llenarse: ya no hace falta, pero no se borra en cuanto se llena. */
    private static final Duration RESERVA = Duration.ofMinutes(5);

    private final boolean activos;
    private final PostgreSQLSelectForUpdateBasedProxyManager<String> cubos;
    private final Map<ClaseDeLimite, BucketConfiguration> configuraciones = new EnumMap<>(ClaseDeLimite.class);

    @SuppressWarnings("java:S107") // un limite por clase, cada uno por su nombre
    public LimitesDePeticiones(DataSource dataSource, @Value("${limites.activos}") boolean activos,
            @Value("${limites.autenticacion-por-minuto}") long autenticacion,
            @Value("${limites.registro-por-hora}") long registro,
            @Value("${limites.correo-por-hora}") long correo,
            @Value("${limites.escritura-por-minuto}") long escritura) {
        this.activos = activos;
        this.cubos = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table("cubos_de_peticiones")
                .idColumn("id")
                .stateColumn("estado")
                .expiresAtColumn("vence_en")
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(RESERVA))
                .build();
        configuraciones.put(ClaseDeLimite.AUTENTICACION, cubo(autenticacion, Duration.ofMinutes(1)));
        configuraciones.put(ClaseDeLimite.REGISTRO, cubo(registro, Duration.ofHours(1)));
        configuraciones.put(ClaseDeLimite.CORREO, cubo(correo, Duration.ofHours(1)));
        configuraciones.put(ClaseDeLimite.ESCRITURA, cubo(escritura, Duration.ofMinutes(1)));
    }

    /**
     * Gasta una ficha del cubo de esa clase y de esa clave. Vacio si habia; si no, cuanto falta para la proxima, que es
     * lo que dice el Retry-After.
     */
    public Optional<Duration> gastar(ClaseDeLimite clase, String clave) {
        if (!activos) {
            return Optional.empty();
        }
        ConsumptionProbe intento = cubos.builder()
                .build(clase.name().toLowerCase(Locale.ROOT) + ":" + clave, () -> configuraciones.get(clase))
                .tryConsumeAndReturnRemaining(1);
        if (intento.isConsumed()) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofNanos(intento.getNanosToWaitForRefill()));
    }

    /** D20: los cubos que ya se llenaron no hacen falta. Devuelve cuantos se borraron. */
    public int olvidarVencidos() {
        int total = 0;
        int borrados;
        do {
            borrados = cubos.removeExpired(1000);
            total += borrados;
        } while (borrados > 0);
        return total;
    }

    /** Lleno al empezar, y se rellena de a una ficha a lo largo del periodo: no de golpe al acabarlo. */
    private static BucketConfiguration cubo(long capacidad, Duration periodo) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(capacidad).refillGreedy(capacidad, periodo).build())
                .build();
    }
}
