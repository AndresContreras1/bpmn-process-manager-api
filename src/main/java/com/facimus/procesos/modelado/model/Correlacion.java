package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Builder;
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

    /** El campo del cuerpo que lleva la clave; sin el, en ejecucion no habra con que correlacionar. */
    @Column(length = 80)
    private String campo;

    /** Que hacer con un mensaje que no corresponde a ningun caso abierto. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sin_caso", nullable = false, length = 20)
    @Builder.Default
    private PoliticaSinCaso sinCaso = PoliticaSinCaso.DESCARTAR;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mensaje_id", nullable = false, unique = true)
    private Mensaje mensaje;
}
