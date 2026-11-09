package com.facimus.procesos.gestion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.common.model.RolAcceso;

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

/** PR 50: un dominio de correo de la tienda; verificado, quien entra con un proveedor desde el se une a ella. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "dominios_verificados")
public class DominioVerificado extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 253)
    private String dominio;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_por_defecto", nullable = false, length = 20)
    private RolAcceso rolPorDefecto;

    /** Lo que el registro TXT del dominio tiene que decir. */
    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "verificado_en")
    private LocalDateTime verificadoEn;
}
