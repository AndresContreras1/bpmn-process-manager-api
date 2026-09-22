package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** El criterio que indica a que caso concreto del proceso corresponde un mensaje. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "correlaciones")
// Baja logica: borrar marca la fila como inactiva, y ninguna consulta ve las filas inactivas.
@SQLDelete(sql = "update correlaciones set activo = false where id = ? and version = ?")
@SQLRestriction("activo = true")
public class Correlacion extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String criterio;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mensaje_id", nullable = false, unique = true)
    private Mensaje mensaje;
}
