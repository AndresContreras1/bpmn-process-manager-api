package com.facimus.procesos.gestion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Un POST hecho con una Idempotency-Key y lo que respondio. Si el cliente lo reintenta con la misma clave, recibe esta
 * respuesta en vez de crear otro recurso. Mientras la primera peticion sigue en curso, estado esta vacio.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "claves_idempotencia")
public class ClaveIdempotencia extends EntidadEmpresa {

    /** Tamano maximo de una respuesta guardada; una mas grande no se guarda y el reintento vuelve a ejecutarse. */
    public static final int CUERPO_MAXIMO = 16000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 100)
    private String clave;

    /** SHA-256 del metodo, la ruta y el cuerpo de la peticion. */
    @Column(nullable = false, length = 64)
    private String huella;

    private Integer estado;

    @Column(length = CUERPO_MAXIMO)
    private String cuerpo;

    @Column(length = 500)
    private String ubicacion;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;
}
