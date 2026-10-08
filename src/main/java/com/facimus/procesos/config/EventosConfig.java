package com.facimus.procesos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Los receptores {@code @ApplicationModuleListener} corren despues del commit, cada uno en su hilo virtual y en su
 * propia transaccion: lo que hace un modulo cuando otro le avisa (mandar un correo, anotar en la bitacora) no frena ni
 * deshace lo que el otro ya confirmo. El evento queda en el outbox hasta que el receptor termina (D34).
 */
@Configuration
@EnableAsync
public class EventosConfig {
}
