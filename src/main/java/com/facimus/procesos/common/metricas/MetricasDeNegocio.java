package com.facimus.procesos.common.metricas;

import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Los contadores de lo que pasa en el negocio, para quien opera la API: lo que les pasa a los casos (se abren, se
 * completan sus tareas, terminan), las versiones que se publican y los intentos de entrar que fallan. Se leen en
 * Actuator y en Prometheus, junto a los medidores de la operacion.
 *
 * <p>Cuentan lo que quedo guardado: dentro de una transaccion, el contador sube cuando la transaccion se confirma, y
 * una que se deshace no cuenta nada. Como los medidores, son de toda la instalacion y no llevan una etiqueta por
 * tienda (ver MetricasConfig): sus etiquetas son pocas y fijas.
 */
@Component
public class MetricasDeNegocio {

    private final MeterRegistry registro;

    public MetricasDeNegocio(MeterRegistry registro) {
        this.registro = registro;
    }

    /** Una linea de la bitacora de un caso: su tipo es la etiqueta, en minusculas (caso_abierto, tarea_completada). */
    public void eventoDeCaso(Enum<?> tipo) {
        contar(Counter.builder("casos.eventos")
                .description("Lo que les pasa a los casos: se abren, avanzan, se completan sus tareas y terminan")
                .tag("tipo", tipo.name().toLowerCase(Locale.ROOT)));
    }

    public void versionPublicada() {
        contar(Counter.builder("versiones.publicadas")
                .description("Versiones de procesos publicadas"));
    }

    /** Un login que no entro: por las credenciales, o sin mirarlas porque el correo ya junto demasiados fallos. */
    public void loginFallido(boolean bloqueado) {
        contar(Counter.builder("login.fallidos")
                .description("Intentos de entrar que fallaron")
                .tag("motivo", bloqueado ? "bloqueado" : "credenciales"));
    }

    private void contar(Counter.Builder contador) {
        Counter registrado = contador.register(registro);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    registrado.increment();
                }
            });
        } else {
            registrado.increment();
        }
    }
}
