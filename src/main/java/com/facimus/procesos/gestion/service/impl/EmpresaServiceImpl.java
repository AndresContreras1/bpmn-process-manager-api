package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.EmpresaResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.mapper.EmpresaMapper;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.CuentaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.UsuarioService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmpresaServiceImpl implements EmpresaService {

    private static final Logger log = LoggerFactory.getLogger(EmpresaServiceImpl.class);
    private static final String NO_ENCONTRADA = "Empresa no encontrada.";

    private final EmpresaRepository empresaRepository;
    private final UsuarioService usuarioService;
    private final ConfiguracionTiendaService configuracionTiendaService;
    private final HistorialCambioService historialCambioService;
    private final CuentaService cuentaService;
    private final EmpresaMapper empresaMapper;
    private final BorradoDeTiendas borradoDeTiendas;

    @Override
    @Transactional
    public EmpresaResponse registrar(String nombre, String nit, String correoContacto,
            String nombreAdmin, String emailAdmin, String passwordAdmin) {
        if (empresaRepository.existsByNit(nit)) {
            throw new ReglaNegocioException("Ya existe una empresa registrada con el NIT " + nit + ".");
        }
        // El correo del administrador sera su usuario de login: se valida antes de crear la empresa.
        usuarioService.validarCorreoDisponible(emailAdmin);

        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nombre(nombre)
                .nit(nit)
                .correoContacto(correoContacto)
                .fechaRegistro(LocalDate.now())
                .build());

        // Sin autor: el primer administrador todavia no existe, y al crearse firma su propia alta.
        UsuarioResponse administrador = usuarioService.crearColaborador(empresa.getId(), null, nombreAdmin,
                emailAdmin, passwordAdmin, RolAcceso.ADMINISTRADOR);
        configuracionTiendaService.crearPara(empresa.getId());
        historialCambioService.registrarDeTienda(empresa.getId(), administrador.id(), RecursoDeHistorial.EMPRESA,
                empresa.getId(), "Tienda \"" + nombre + "\" registrada.");
        // Hasta que el administrador siga el enlace, la tienda no invita a nadie por correo.
        cuentaService.pedirVerificacion(empresa.getId(), administrador.id());
        return empresaMapper.toResponse(empresa);
    }

    @Override
    public EmpresaResponse obtener(Long empresaId, Long id) {
        if (!empresaId.equals(id)) {
            throw new RecursoNoEncontradoException(NO_ENCONTRADA);
        }
        return empresaMapper.toResponse(empresaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA)));
    }

    @Override
    @Transactional
    public EmpresaResponse pedirBaja(Long empresaId, Long autorId, String confirmacion) {
        // Bloqueada: dos administradores que la dan de baja a la vez no la dan de baja dos veces.
        Empresa empresa = empresaRepository.bloquear(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
        if (!empresa.getNombre().equals(confirmacion.strip())) {
            throw new SolicitudInvalidaException("Para confirmar la baja escribe el nombre de la tienda tal como es.");
        }
        if (empresa.getBajaSolicitadaEn() != null) {
            throw new ReglaNegocioException("La tienda ya está dada de baja: sus datos se borran el "
                    + empresa.getBorradoProgramadoPara().toLocalDate() + ".");
        }
        empresa.setBajaSolicitadaEn(LocalDateTime.now());
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.EMPRESA, empresaId,
                "Tienda dada de baja: hasta el " + empresa.getBorradoProgramadoPara().toLocalDate()
                        + " solo se puede consultar, y ese día se borran sus datos.");
        return empresaMapper.toResponse(empresaRepository.saveAndFlush(empresa));
    }

    @Override
    @Transactional
    public EmpresaResponse cancelarBaja(Long empresaId, Long autorId) {
        Empresa empresa = empresaRepository.bloquear(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
        if (empresa.getBajaSolicitadaEn() == null) {
            throw new ReglaNegocioException("La tienda no está dada de baja.");
        }
        empresa.setBajaSolicitadaEn(null);
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.EMPRESA, empresaId,
                "Baja de la tienda cancelada: vuelve a funcionar como antes.");
        return empresaMapper.toResponse(empresaRepository.saveAndFlush(empresa));
    }

    @Override
    public Optional<LocalDateTime> borradoProgramado(Long empresaId) {
        return empresaRepository.bajaSolicitadaEn(empresaId).map(pedida -> pedida.plus(Empresa.GRACIA_DE_LA_BAJA));
    }

    /** Cada tienda se borra en su propia transaccion: una que falla no deja a medias a las demas. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int borrarLasDadasDeBaja() {
        LocalDateTime ahora = LocalDateTime.now();
        int borradas = 0;
        for (Long empresaId : empresaRepository.conLaBajaAntesDe(ahora.minus(Empresa.GRACIA_DE_LA_BAJA))) {
            int filas = borradoDeTiendas.borrar(empresaId, ahora);
            log.info("Tienda {} borrada al cumplir la gracia de su baja: {} filas.", empresaId, filas);
            borradas++;
        }
        return borradas;
    }
}
