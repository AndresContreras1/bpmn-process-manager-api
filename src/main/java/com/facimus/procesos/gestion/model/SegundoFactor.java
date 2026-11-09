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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 49: el TOTP de una persona. El secreto va cifrado; hasta que la persona lo confirma con un codigo bueno, el login
 * no lo pide. ultimoPaso es el paso del ultimo codigo aceptado: uno igual o anterior ya no entra.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "segundos_factores")
public class SegundoFactor extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "secreto_cifrado", nullable = false, length = 200)
    private String secretoCifrado;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "confirmado_en")
    private LocalDateTime confirmadoEn;

    @Column(name = "ultimo_paso", nullable = false)
    @Builder.Default
    private long ultimoPaso = 0;

    public boolean estaConfirmado() {
        return confirmadoEn != null;
    }
}
