package com.facimus.procesos.gestion.service.impl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.CarpetaResponse;
import com.facimus.procesos.gestion.model.Carpeta;
import com.facimus.procesos.gestion.repository.CarpetaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.CarpetaService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarpetaServiceImpl implements CarpetaService {

    /** Un arbol mas hondo que esto no se recorre a ojo. */
    private static final int PROFUNDIDAD_MAXIMA = 8;

    private final CarpetaRepository carpetaRepository;
    private final EmpresaRepository empresaRepository;
    private final ProcesoRepository procesoRepository;

    @Override
    public List<CarpetaResponse> listar(Long empresaId) {
        Map<Long, Long> cuantos = new HashMap<>();
        procesoRepository.contarPorCarpeta(empresaId)
                .forEach(fila -> cuantos.put(fila.carpetaId(), fila.procesos()));
        return carpetaRepository.findAllByEmpresaIdOrderByNombreAsc(empresaId).stream()
                .map(carpeta -> respuesta(carpeta, cuantos.getOrDefault(carpeta.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public CarpetaResponse crear(Long empresaId, String nombre, Long padreId) {
        List<Carpeta> todas = carpetaRepository.findAllByEmpresaIdOrderByNombreAsc(empresaId);
        comprobarPadre(todas, padreId, null);
        comprobarNombre(todas, nombre, padreId, null);
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Carpeta carpeta = carpetaRepository.saveAndFlush(Carpeta.builder().empresa(empresa).nombre(nombre)
                .padreId(padreId).build());
        return respuesta(carpeta, 0L);
    }

    @Override
    @Transactional
    public CarpetaResponse editar(Long empresaId, Long carpetaId, String nombre, Long padreId, Long version) {
        List<Carpeta> todas = carpetaRepository.findAllByEmpresaIdOrderByNombreAsc(empresaId);
        Carpeta carpeta = buscar(empresaId, carpetaId);
        carpeta.verificarVersion(version);
        comprobarPadre(todas, padreId, carpetaId);
        comprobarNombre(todas, nombre, padreId, carpetaId);
        carpeta.setNombre(nombre);
        carpeta.setPadreId(padreId);
        carpeta = carpetaRepository.saveAndFlush(carpeta);
        return respuesta(carpeta, procesoRepository.countByCarpetaIdAndEmpresaIdAndActivoTrue(carpetaId, empresaId));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long carpetaId) {
        Carpeta carpeta = buscar(empresaId, carpetaId);
        if (carpetaRepository.existsByEmpresaIdAndPadreId(empresaId, carpetaId)) {
            throw new ReglaNegocioException("La carpeta tiene subcarpetas: muévelas o bórralas primero.");
        }
        if (procesoRepository.countByCarpetaIdAndEmpresaIdAndActivoTrue(carpetaId, empresaId) > 0) {
            throw new ReglaNegocioException("La carpeta tiene procesos: muévelos primero.");
        }
        carpetaRepository.delete(carpeta);
    }

    @Override
    public Set<Long> conSubcarpetas(Long empresaId, Long carpetaId) {
        List<Carpeta> todas = carpetaRepository.findAllByEmpresaIdOrderByNombreAsc(empresaId);
        Set<Long> ids = new HashSet<>();
        Deque<Long> pendientes = new ArrayDeque<>();
        pendientes.add(carpetaId);
        while (!pendientes.isEmpty()) {
            Long actual = pendientes.poll();
            if (ids.add(actual)) {
                todas.stream().filter(carpeta -> actual.equals(carpeta.getPadreId()))
                        .forEach(carpeta -> pendientes.add(carpeta.getId()));
            }
        }
        return ids;
    }

    /** El padre existe, no es la carpeta ni algo de adentro suyo, y el arbol no queda mas hondo de la cuenta. */
    private void comprobarPadre(List<Carpeta> todas, Long padreId, Long carpetaId) {
        if (padreId == null) {
            return;
        }
        Map<Long, Carpeta> porId = new HashMap<>();
        todas.forEach(carpeta -> porId.put(carpeta.getId(), carpeta));
        if (!porId.containsKey(padreId)) {
            throw new RecursoNoEncontradoException("Carpeta no encontrada.");
        }
        int profundidad = 1;
        for (Long actual = padreId; actual != null; actual = porId.get(actual).getPadreId()) {
            if (actual.equals(carpetaId)) {
                throw new ReglaNegocioException("Una carpeta no puede quedar dentro de sí misma.");
            }
            profundidad++;
        }
        if (profundidad + alturaDe(todas, carpetaId) > PROFUNDIDAD_MAXIMA) {
            throw new ReglaNegocioException("Las carpetas no pueden tener más de " + PROFUNDIDAD_MAXIMA + " niveles.");
        }
    }

    /** Cuantos niveles hay debajo de una carpeta; una que todavia no existe no tiene ninguno. */
    private int alturaDe(List<Carpeta> todas, Long carpetaId) {
        if (carpetaId == null) {
            return 0;
        }
        return todas.stream().filter(carpeta -> carpetaId.equals(carpeta.getPadreId()))
                .mapToInt(carpeta -> 1 + alturaDe(todas, carpeta.getId())).max().orElse(0);
    }

    private static void comprobarNombre(List<Carpeta> todas, String nombre, Long padreId, Long carpetaId) {
        boolean repetido = todas.stream().anyMatch(carpeta -> !carpeta.getId().equals(carpetaId)
                && Objects.equals(carpeta.getPadreId(), padreId) && carpeta.getNombre().equalsIgnoreCase(nombre));
        if (repetido) {
            throw new ReglaNegocioException("Ya hay una carpeta con el nombre \"" + nombre + "\" en ese lugar.");
        }
    }

    private Carpeta buscar(Long empresaId, Long carpetaId) {
        return carpetaRepository.findByIdAndEmpresaId(carpetaId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Carpeta no encontrada."));
    }

    private static CarpetaResponse respuesta(Carpeta carpeta, long procesos) {
        return new CarpetaResponse(carpeta.getId(), carpeta.getNombre(), carpeta.getPadreId(), procesos,
                carpeta.getVersion());
    }
}
