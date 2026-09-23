package com.facimus.procesos.modelado.repository;

import java.time.LocalDate;

import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;

/**
 * Arma en la base los elementos BPMN que necesitan los slices de persistencia del modelado. Cada tienda tiene su
 * propia instancia, asi que lo que crea una nunca se mezcla con lo de la otra.
 */
class DiagramaDePrueba {

    private final TestEntityManager em;
    private final Empresa empresa;

    DiagramaDePrueba(TestEntityManager em, String nombreTienda) {
        this.em = em;
        this.empresa = em.persistFlushFind(Empresa.builder()
                .nombre(nombreTienda)
                .nit(Math.abs(nombreTienda.hashCode()) + "-1")
                .correoContacto("contacto@" + nombreTienda.replace(" ", "").toLowerCase() + ".com")
                .fechaRegistro(LocalDate.now())
                .build());
    }

    Empresa empresa() {
        return empresa;
    }

    Proceso proceso(String nombre) {
        return proceso(nombre, true);
    }

    Proceso proceso(String nombre, boolean activo) {
        return em.persistFlushFind(Proceso.builder()
                .empresa(empresa)
                .nombre(nombre)
                .descripcion("Proceso de prueba")
                .categoria("Pruebas")
                .activo(activo)
                .build());
    }

    RolProceso rol(String nombre) {
        return em.persistFlushFind(RolProceso.builder()
                .empresa(empresa)
                .nombre(nombre)
                .build());
    }

    Pool pool(Proceso proceso, String nombre, int orden) {
        return em.persistFlushFind(Pool.builder()
                .empresa(empresa)
                .proceso(proceso)
                .nombre(nombre)
                .tipoParticipante(TipoParticipante.EMPRESA)
                .orden(orden)
                .build());
    }

    Lane lane(Pool pool, RolProceso rol, String nombre, int orden) {
        return em.persistFlushFind(Lane.builder()
                .empresa(empresa)
                .pool(pool)
                .rolProceso(rol)
                .nombre(nombre)
                .orden(orden)
                .build());
    }

    Actividad actividad(Lane lane, String nombre) {
        return actividad(lane, nombre, TipoActividad.USUARIO);
    }

    Actividad actividad(Lane lane, String nombre, TipoActividad tipo) {
        return em.persistFlushFind(Actividad.builder()
                .empresa(empresa)
                .lane(lane)
                .nombre(nombre)
                .descripcion("Tarea de prueba")
                .tipoActividad(tipo)
                .posicionX(10)
                .posicionY(20)
                .build());
    }

    Evento evento(Lane lane, String nombre, TipoEvento tipo) {
        return em.persistFlushFind(eventoSinGuardar(lane, nombre, tipo));
    }

    /** Sin guardar, para las pruebas que esperan que la base rechace el evento. */
    Evento eventoSinGuardar(Lane lane, String nombre, TipoEvento tipo) {
        return Evento.builder()
                .empresa(empresa)
                .lane(lane)
                .nombre(nombre)
                .tipoEvento(tipo)
                .posicionX(50)
                .posicionY(60)
                .build();
    }

    Gateway gateway(Lane lane, String nombre, TipoGateway tipo) {
        return em.persistFlushFind(Gateway.builder()
                .empresa(empresa)
                .lane(lane)
                .nombre(nombre)
                .tipoGateway(tipo)
                .posicionX(30)
                .posicionY(40)
                .build());
    }

    Arco arco(Pool pool, NodoFlujo origen, NodoFlujo destino) {
        return em.persistFlushFind(arcoSinGuardar(pool, origen, destino));
    }

    /** Sin guardar, para las pruebas que esperan que la base rechace el arco. */
    Arco arcoSinGuardar(Pool pool, NodoFlujo origen, NodoFlujo destino) {
        return Arco.builder()
                .empresa(empresa)
                .pool(pool)
                .origen(origen)
                .destino(destino)
                .build();
    }

    Mensaje mensaje(Proceso proceso, Pool origen, Pool destino, String nombre) {
        return em.persistFlushFind(Mensaje.builder()
                .empresa(empresa)
                .proceso(proceso)
                .nombre(nombre)
                .contenido("Contenido de prueba")
                .poolOrigen(origen)
                .poolDestino(destino)
                .build());
    }

    Correlacion correlacion(Mensaje mensaje, String criterio) {
        return em.persistFlushFind(Correlacion.builder()
                .empresa(empresa)
                .mensaje(mensaje)
                .criterio(criterio)
                .build());
    }
}
