package com.facimus.procesos.ejecucion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * La bitacora de un caso: por que esta donde esta. Cada decision, cada tarea y cada error dejan una linea, y las
 * lineas solo se insertan: nada de lo que paso se corrige despues.
 *
 * <p>No extiende EntidadEditable porque no se edita; su fecha y su autor son columnas suyas, no auditoria.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "eventos_caso")
public class EventoCaso extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_id", nullable = false, updatable = false)
    private Caso caso;

    /** El tick del reloj de la tienda en que paso. Hasta que exista el reloj, todos son 0. */
    @Column(nullable = false)
    private int tick;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEventoCaso tipo;

    @Column(nullable = false, length = 2000)
    private String detalle;

    /** El usuario que lo provoco; vacio cuando fue el motor. */
    @Column(name = "autor_id")
    private Long autorId;
}
