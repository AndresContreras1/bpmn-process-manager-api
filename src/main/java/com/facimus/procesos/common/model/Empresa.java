package com.facimus.procesos.common.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Raiz de la tenencia multiempresa. No extiende EntidadEmpresa: es la
 * entidad a la que todas las demas se acotan.
 * <p>
 * Una tienda dada de baja pasa {@link #GRACIA_DE_LA_BAJA} en solo lectura, y despues se borra todo lo suyo: de esta
 * fila queda una lapida con el id y las fechas, sin su nombre, su NIT ni su correo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "empresas")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, unique = true, length = 20)
    private String nit;

    @Column(name = "correo_contacto", nullable = false, length = 254)
    private String correoContacto;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;

    /** Los dias que una tienda dada de baja se puede mirar, y su baja cancelar, antes de que se borre. */
    public static final Duration GRACIA_DE_LA_BAJA = Duration.ofDays(30);

    /** Cuando un administrador pidio la baja; vacio mientras la tienda siga, o si la baja se cancelo. */
    @Column(name = "baja_solicitada_en")
    private LocalDateTime bajaSolicitadaEn;

    /** Cuando se borraron sus datos: desde entonces la fila es solo una lapida. */
    @Column(name = "borrada_en")
    private LocalDateTime borradaEn;

    /** Cuando se borrara todo lo de la tienda; vacio si no esta dada de baja. */
    public LocalDateTime getBorradoProgramadoPara() {
        return bajaSolicitadaEn == null ? null : bajaSolicitadaEn.plus(GRACIA_DE_LA_BAJA);
    }
}
