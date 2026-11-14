package com.facimus.procesos.modelado.model;

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
 * PR 61: un archivo subido a un documento. Lo que se sabe de el no cambia (nombre, tipo, tamano, huella, clave);
 * solo su estado, que la cola mueve una vez de {@code PENDIENTE} a {@code DISPONIBLE} o {@code BLOQUEADO}.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "versiones_documento")
public class VersionDocumento extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false, updatable = false)
    private Documento documento;

    @Column(nullable = false, updatable = false)
    private int numero;

    /** El nombre saneado, con la extension del tipo detectado. */
    @Column(nullable = false, updatable = false, length = 150)
    private String nombre;

    /** El tipo MIME que se detecto por su contenido. */
    @Column(nullable = false, updatable = false, length = 100)
    private String tipo;

    @Column(nullable = false, updatable = false)
    private long tamano;

    @Column(nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(nullable = false, updatable = false, length = 255)
    private String clave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDocumento estado;

    @Column(length = 300)
    private String motivo;

    /** Si paso por un antivirus de verdad; sin uno configurado, queda disponible sin revisar. */
    @Column(nullable = false)
    private boolean revisado;

    @Column(name = "subido_por", updatable = false)
    private Long subidoPor;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    /**
     * PR 65: cuando la cola intento leer su texto, lo tuviera o no; vacio mientras no lo haga. El texto no se mapea:
     * puede pesar un megabyte y solo lo lee la busqueda. Lo escribe {@code VersionDocumentoRepository.guardarTexto}.
     */
    @Column(name = "texto_extraido_en", insertable = false, updatable = false)
    private LocalDateTime textoExtraidoEn;
}
