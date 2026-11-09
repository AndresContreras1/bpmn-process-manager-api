package com.facimus.procesos.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Factor;

/**
 * Lo que dice un access token valido: quien es, con que factores entro y cuando los verifico por ultima vez. De aqui
 * salen las autoridades de la peticion: su rol y un {@link FactorGrantedAuthority} por factor, fechado en esa
 * verificacion, que es lo que mira una regla que pide una verificacion reciente.
 */
public record TokenDeAcceso(ApiPrincipal principal, Set<Factor> factores, Instant verificadoEn) {

    public List<GrantedAuthority> autoridades() {
        List<GrantedAuthority> autoridades = new ArrayList<>(principal.authorities());
        factores.forEach(factor -> autoridades.add(FactorGrantedAuthority.withAuthority(factor.autoridad())
                .issuedAt(verificadoEn)
                .build()));
        return autoridades;
    }
}
