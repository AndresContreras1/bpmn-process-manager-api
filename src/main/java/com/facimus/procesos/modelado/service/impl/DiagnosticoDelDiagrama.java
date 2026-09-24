package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.service.DiagnosticoDelModelo;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.model.Severidad;

import lombok.RequiredArgsConstructor;

/**
 * R-44: lo que impide publicar son los errores del diagnostico, los mismos que ve quien edita el diagrama. Las
 * advertencias no impiden nada: un proceso con una decision sin salida por defecto se publica, y el aviso sigue ahi.
 *
 * <p>Arma el diagrama por su cuenta en vez de pedirselo a DiagnosticoService, que lo pediria a la puerta de lectura
 * de gestion: gestion ya comprobo que el proceso es suyo, y volver a entrar por ahi cerraria un circulo.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosticoDelDiagrama implements DiagnosticoDelModelo {

    private final ArmadoDelDiagrama armado;

    @Override
    public List<String> errores(Long empresaId, ProcesoResponse proceso) {
        return DiagnosticoServiceImpl.revisar(armado.armar(proceso, false, empresaId)).hallazgos().stream()
                .filter(hallazgo -> hallazgo.severidad() == Severidad.ALTA)
                .map(DiagnosticoDelDiagrama::redactar)
                .toList();
    }

    /** El codigo y el elemento van en el texto: quien recibe el 409 no tiene el diagnostico delante. */
    private static String redactar(HallazgoDiagnosticoResponse hallazgo) {
        return hallazgo.codigo() + " " + hallazgo.elemento() + ": " + hallazgo.problema();
    }
}
