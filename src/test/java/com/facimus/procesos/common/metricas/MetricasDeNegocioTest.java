package com.facimus.procesos.common.metricas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Los contadores del negocio cuentan lo que quedo guardado, con etiquetas pocas y fijas. */
class MetricasDeNegocioTest {

    private enum Evento { CASO_ABIERTO, TAREA_COMPLETADA }

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MetricasDeNegocio metricas = new MetricasDeNegocio(registro);

    @AfterEach
    void cerrarLaTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Fuera de una transaccion, el contador sube en el acto")
    void sinTransaccion_cuentaEnElActo() {
        metricas.versionPublicada();

        assertThat(registro.get("versiones.publicadas").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Dentro de una transaccion, el contador sube cuando la transaccion se confirma, no antes")
    void conTransaccion_cuentaAlConfirmar() {
        TransactionSynchronizationManager.initSynchronization();

        metricas.eventoDeCaso(Evento.CASO_ABIERTO);
        assertThat(registro.get("casos.eventos").tag("tipo", "caso_abierto").counter().count()).isZero();

        TransactionSynchronizationUtils.triggerAfterCommit();
        assertThat(registro.get("casos.eventos").tag("tipo", "caso_abierto").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una transaccion que se deshace no cuenta nada")
    void transaccionDeshecha_noCuenta() {
        TransactionSynchronizationManager.initSynchronization();

        metricas.eventoDeCaso(Evento.TAREA_COMPLETADA);
        TransactionSynchronizationUtils.invokeAfterCompletion(TransactionSynchronizationManager.getSynchronizations(),
                TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(registro.get("casos.eventos").tag("tipo", "tarea_completada").counter().count()).isZero();
    }

    @Test
    @DisplayName("Un login fallido se cuenta por su motivo: las credenciales, o el bloqueo por intentos")
    void loginFallido_seCuentaPorMotivo() {
        metricas.loginFallido(false);
        metricas.loginFallido(false);
        metricas.loginFallido(true);

        assertThat(registro.get("login.fallidos").tag("motivo", "credenciales").counter().count()).isEqualTo(2);
        assertThat(registro.get("login.fallidos").tag("motivo", "bloqueado").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cada tipo de evento de un caso es una serie, con el nombre del tipo en minusculas")
    void eventosDeCaso_unaSeriePorTipo() {
        metricas.eventoDeCaso(Evento.CASO_ABIERTO);
        metricas.eventoDeCaso(Evento.TAREA_COMPLETADA);
        metricas.eventoDeCaso(Evento.TAREA_COMPLETADA);

        assertThat(registro.get("casos.eventos").counters()).hasSize(2);
        assertThat(registro.get("casos.eventos").tag("tipo", "tarea_completada").counter().count()).isEqualTo(2);
    }
}
