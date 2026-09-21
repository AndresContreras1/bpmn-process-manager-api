package com.facimus.procesos.gestion.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Raiz de la tenencia multiempresa. No extiende EntidadEmpresa: es la
 * entidad a la que todas las demas se acotan.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "empresas")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String nit;

    @Column(name = "correo_contacto", nullable = false)
    private String correoContacto;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;
}
