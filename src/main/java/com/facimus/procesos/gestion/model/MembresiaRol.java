package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * D13: que roles de proceso tiene una persona, para que pueda pedir solo su bandeja. No sustituye al rol de acceso
 * ni da permisos: una tarea la completa cualquier administrador o editor de la tienda, tenga el rol o no. Es un
 * filtro, no una puerta.
 *
 * <p>No extiende EntidadEditable: no se edita una membresia, se reemplaza la lista entera de una persona, y quien
 * la cambio queda en el historial de la tienda.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "membresias_rol")
public class MembresiaRol extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_proceso_id", nullable = false, updatable = false)
    private RolProceso rolProceso;
}
