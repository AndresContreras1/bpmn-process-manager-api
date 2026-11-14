package com.facimus.procesos.config;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.gestion.service.CuentaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.Limpieza;
import com.facimus.procesos.gestion.service.LimpiezaService;
import com.facimus.procesos.modelado.service.RevisionService;
import com.facimus.procesos.security.DesafiosWebAuthn;
import com.facimus.procesos.security.IntentosDeLogin;
import com.facimus.procesos.security.LimitesDePeticiones;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * El reloj de la purga (D20). Queda fuera del perfil {@code test} a proposito: una prueba no puede tener un trabajo
 * corriendo por detras que le borre filas a media prueba, y la purga se ejerce llamando al service.
 * <p>
 * {@code LIMPIEZA_CRON=-} la apaga sin recompilar, que es lo que querria quien prefiera barrer desde fuera.
 * <p>
 * Con varias instancias la corre una sola (D34): el candado se queda tomado cinco minutos aunque la purga termine
 * antes, por si el reloj de otra maquina le hace llegar a las 3:30 unos segundos despues.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
public class LimpiezaConfig {

    private final LimpiezaService limpiezaService;
    private final RevisionService revisionService;
    private final IntentosDeLogin intentosDeLogin;
    private final ColaDeTrabajos colaDeTrabajos;
    private final CuentaService cuentaService;
    private final EmpresaService empresaService;
    private final LimitesDePeticiones limitesDePeticiones;
    private final DesafiosWebAuthn desafiosWebAuthn;
    private final CasoService casoService;
    /** PR 68: cuanto se guarda una prueba del borrador antes de que la purga se la lleve con su caso. */
    private final Duration retencionDePruebas;
    private final Clock reloj;

    @SuppressWarnings("java:S107") // Cada pieza que se purga es una dependencia: la purga es la lista entera
    public LimpiezaConfig(LimpiezaService limpiezaService, RevisionService revisionService,
            IntentosDeLogin intentosDeLogin, ColaDeTrabajos colaDeTrabajos, CuentaService cuentaService,
            EmpresaService empresaService, LimitesDePeticiones limitesDePeticiones, DesafiosWebAuthn desafiosWebAuthn,
            CasoService casoService, @Value("${pruebas.retencion}") Duration retencionDePruebas, Clock reloj) {
        this.limpiezaService = limpiezaService;
        this.revisionService = revisionService;
        this.intentosDeLogin = intentosDeLogin;
        this.colaDeTrabajos = colaDeTrabajos;
        this.cuentaService = cuentaService;
        this.empresaService = empresaService;
        this.limitesDePeticiones = limitesDePeticiones;
        this.desafiosWebAuthn = desafiosWebAuthn;
        this.casoService = casoService;
        this.retencionDePruebas = retencionDePruebas;
        this.reloj = reloj;
    }

    @Scheduled(cron = "${limpieza.cron}")
    @SchedulerLock(name = "limpieza", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void limpiar() {
        // PR 51: la purga cruza tiendas a proposito.
        ContextoDeTienda.comoSistema(this::purgar);
    }

    private void purgar() {
        Limpieza limpieza = limpiezaService.limpiar();
        if (limpieza.total() > 0) {
            log.info("Limpieza: {} refresh tokens, {} sesiones y {} claves de idempotencia.",
                    limpieza.refreshTokens(), limpieza.sesiones(), limpieza.clavesIdempotencia());
        }
        int intentos = intentosDeLogin.olvidarVencidos();
        if (intentos > 0) {
            log.info("Limpieza: {} intentos de login que ya salieron de su ventana.", intentos);
        }
        int trabajos = colaDeTrabajos.olvidarTerminados();
        if (trabajos > 0) {
            log.info("Limpieza: {} trabajos terminados de la cola.", trabajos);
        }
        int enlaces = cuentaService.olvidarEnlacesVencidos();
        if (enlaces > 0) {
            log.info("Limpieza: {} enlaces del correo que vencieron hace mas de una semana.", enlaces);
        }
        int revisiones = revisionService.olvidarSuperadas();
        if (revisiones > 0) {
            log.info("Limpieza: {} revisiones con IA que ya tenian una mas nueva.", revisiones);
        }
        int cubos = limitesDePeticiones.olvidarVencidos();
        if (cubos > 0) {
            log.info("Limpieza: {} cubos del limite de peticiones que ya se habian llenado.", cubos);
        }
        int desafios = desafiosWebAuthn.olvidarVencidos();
        if (desafios > 0) {
            log.info("Limpieza: {} desafios de passkeys que vencieron sin usarse.", desafios);
        }
        int pruebas = casoService.olvidarPruebasAntesDe(LocalDateTime.now(reloj).minus(retencionDePruebas));
        if (pruebas > 0) {
            log.info("Limpieza: {} versiones de prueba del borrador, con sus casos.", pruebas);
        }
        int tiendas = empresaService.borrarLasDadasDeBaja();
        if (tiendas > 0) {
            log.info("Limpieza: {} tiendas que cumplieron la gracia de su baja, borradas.", tiendas);
        }
    }
}
