package com.facimus.procesos.modelado.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.gestion.model.Proceso;

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
 * PR 62: el manual de una version publicada, en HTML y en PDF, en el idioma de quien lo pidio. La cola lo arma y deja
 * los dos archivos en el almacen.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "manuales")
public class Manual extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false, updatable = false)
    private Proceso proceso;

    @Column(name = "version_numero", nullable = false, updatable = false)
    private int versionNumero;

    @Column(nullable = false, updatable = false, length = 10)
    private String idioma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoManual estado;

    @Column(name = "clave_html", length = 255)
    private String claveHtml;

    @Column(name = "clave_pdf", length = 255)
    private String clavePdf;

    @Column(length = 300)
    private String motivo;

    @Column(name = "pedido_por", updatable = false)
    private Long pedidoPor;

    @Column(name = "pedido_en", nullable = false, updatable = false)
    private LocalDateTime pedidoEn;

    @Column(name = "listo_en")
    private LocalDateTime listoEn;
}
