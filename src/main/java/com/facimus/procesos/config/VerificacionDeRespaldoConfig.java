package com.facimus.procesos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService.Fallo;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService.Informe;

/**
 * PR 67: con {@code RESPALDO_VERIFICAR=true} la API arranca solo para verificar la base a la que apunta, que es una
 * restaurada: el simulacro (ops/respaldo/simulacro.sh) la corre asi, sin servidor web, contra el contenedor donde
 * restauro el respaldo. Escribe lo que encontro y termina; si algo no cuadra, el arranque falla y el proceso sale con
 * un codigo distinto de cero. Cruza tiendas a proposito, como sistema.
 */
@Configuration
@ConditionalOnBooleanProperty("respaldo.verificar")
public class VerificacionDeRespaldoConfig {

    private static final Logger log = LoggerFactory.getLogger(VerificacionDeRespaldoConfig.class);

    @Bean
    public ApplicationRunner verificarLaBaseRestaurada(VerificacionDeRespaldoService verificacion) {
        return argumentos -> {
            Informe informe = ContextoDeTienda.comoSistema(verificacion::verificar);
            if (log.isErrorEnabled()) {
                for (Fallo fallo : informe.fallos()) {
                    log.error("Base restaurada, tienda {}: {}", fallo.empresaId(), fallo.motivo());
                }
            }
            if (log.isInfoEnabled()) {
                log.info("Base restaurada: {} tiendas, {} registros de bitácora y {} versiones revisados, {} fallos.",
                        informe.tiendas(), informe.registrosDeBitacora(), informe.versiones(),
                        informe.fallos().size());
            }
            if (!informe.correcta()) {
                throw new IllegalStateException("La base restaurada no pasa la verificación: "
                        + informe.fallos().size() + " fallos.");
            }
        };
    }
}
