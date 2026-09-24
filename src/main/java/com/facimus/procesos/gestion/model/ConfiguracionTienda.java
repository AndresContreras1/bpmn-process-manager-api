package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * D16: lo que cada tienda decide sobre si misma. Hoy solo quien puede tocar la estructura de los diagramas; cuando
 * la plataforma simule la ejecucion, aqui viviran tambien sus parametros. Una fila por tienda, creada con ella.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "configuracion_tienda")
public class ConfiguracionTienda extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_estructura", nullable = false, length = 30)
    @Builder.Default
    private PoliticaEstructura politicaEstructura = PoliticaEstructura.ADMINISTRADOR_Y_EDITOR;
}
