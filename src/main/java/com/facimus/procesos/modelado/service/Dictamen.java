package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.HallazgoResponse;

/** Lo que contesto el revisor: una frase sobre el proceso y los hallazgos que encontro. */
public record Dictamen(String resumen, List<HallazgoResponse> hallazgos) {
}
