package com.facimus.procesos.gestion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
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
 * PR 49b: una passkey de una persona. La API guarda solo la clave publica (COSE) y verifica con ella lo que el
 * dispositivo firma; el contador de firmas solo sube, y uno que baja delata una copia de la clave.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "passkeys")
public class Passkey extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "credencial_id", nullable = false, updatable = false)
    private byte[] credencialId;

    @Column(name = "clave_publica", nullable = false, updatable = false)
    private byte[] clavePublica;

    @Column(name = "contador_de_firmas", nullable = false)
    private long contadorDeFirmas;

    @Column(name = "uv_inicializado", nullable = false)
    private boolean uvInicializado;

    /** Los transportes que dijo el navegador (internal, hybrid, usb...), separados por comas. */
    @Column(name = "transportes", nullable = false, length = 100)
    private String transportes;

    @Column(name = "respaldo_posible", nullable = false)
    private boolean respaldoPosible;

    @Column(name = "respaldada", nullable = false)
    private boolean respaldada;

    @Column(name = "objeto_de_atestacion", nullable = false, updatable = false)
    private byte[] objetoDeAtestacion;

    @Column(name = "datos_del_cliente", nullable = false, updatable = false)
    private byte[] datosDelCliente;

    @Column(name = "etiqueta", nullable = false, length = 64)
    private String etiqueta;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @Column(name = "ultimo_uso_en")
    private LocalDateTime ultimoUsoEn;
}
