package com.facimus.procesos.gestion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.common.notificaciones.EnvioPorCorreo;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;

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
 * PR 63: lo que una persona tiene que saber, como que la nombraron en un comentario. Es solo suya: nadie mas la ve. No
 * guarda nombres ni textos, solo ids: quien la causo y el proceso se leen al mostrarla, asi que anonimizar a alguien
 * cambia tambien como aparece aqui.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "notificaciones")
public class Notificacion extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A quien le llega. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoDeNotificacion tipo;

    /** Quien la causo; vacio si fue la API. */
    @Column(name = "autor_id", updatable = false)
    private Long autorId;

    @Column(name = "proceso_id", updatable = false)
    private Long procesoId;

    /** Lo que senala dentro del proceso: el comentario de una mencion o de una respuesta, el hilo resuelto. */
    @Column(name = "referencia_id", updatable = false)
    private Long referenciaId;

    /** Como iba a llegar por correo segun la preferencia que tenia al crearse. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private EnvioPorCorreo envio;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @Column(name = "leida_en")
    private LocalDateTime leidaEn;

    /** Cuando se resolvio su correo: salio, o ya no hacia falta. */
    @Column(name = "correo_en")
    private LocalDateTime correoEn;
}
