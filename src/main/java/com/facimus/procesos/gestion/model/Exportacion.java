package com.facimus.procesos.gestion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 66: la exportacion completa de una tienda: un ZIP con cada cosa suya, armado en la cola y guardado en el almacen.
 * La fila dice donde esta, cuanto pesa, su huella y hasta cuando vale el enlace; la huella sobrevive al ZIP.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "exportaciones")
public class Exportacion extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoExportacion estado;

    @Column(name = "pedida_por", updatable = false)
    private Long pedidaPor;

    @Column(name = "pedida_en", nullable = false, updatable = false)
    private LocalDateTime pedidaEn;

    @Column(name = "lista_en")
    private LocalDateTime listaEn;

    /** Hasta cuando vale el enlace, en UTC y en segundos enteros: es lo que firma. */
    @Column(name = "vence_en")
    private LocalDateTime venceEn;

    @Column(length = 255)
    private String clave;

    @Column
    private Long tamano;

    @Column(length = 64)
    private String sha256;

    @Column
    private Integer archivos;

    @Column(length = 300)
    private String motivo;
}
