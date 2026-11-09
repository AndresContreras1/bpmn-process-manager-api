package com.facimus.procesos.gestion.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.DominioResponse;
import com.facimus.procesos.gestion.model.DominioVerificado;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.repository.DominioVerificadoRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.service.ConsultaDns;
import com.facimus.procesos.gestion.service.DominioService;
import com.facimus.procesos.gestion.service.HistorialCambioService;

@Service
@Transactional(readOnly = true)
public class DominioServiceImpl implements DominioService {

    /** Lo que va delante del token en el registro TXT, para que no se confunda con el de otro servicio. */
    static final String PREFIJO_TXT = "bpmn-process-manager-verificacion=";

    private static final String NO_ENCONTRADO = "Dominio no encontrado.";
    private static final String DOMINIO = "Dominio ";

    private final DominioVerificadoRepository dominioRepository;
    private final EmpresaRepository empresaRepository;
    private final ConsultaDns dns;
    private final HistorialCambioService historialCambioService;
    private final SecureRandom aleatorio = new SecureRandom();

    public DominioServiceImpl(DominioVerificadoRepository dominioRepository, EmpresaRepository empresaRepository,
            ConsultaDns dns, HistorialCambioService historialCambioService) {
        this.dominioRepository = dominioRepository;
        this.empresaRepository = empresaRepository;
        this.dns = dns;
        this.historialCambioService = historialCambioService;
    }

    @Override
    public List<DominioResponse> listar(Long empresaId) {
        return dominioRepository.findAllByEmpresaIdOrderByDominioAsc(empresaId).stream()
                .map(DominioServiceImpl::respuesta)
                .toList();
    }

    @Override
    @Transactional
    public DominioResponse agregar(Long empresaId, Long autorId, String dominio, RolAcceso rolPorDefecto) {
        String limpio = dominio.strip().toLowerCase(Locale.ROOT);
        if (dominioRepository.existsByEmpresaIdAndDominio(empresaId, limpio)) {
            throw new ReglaNegocioException("La tienda ya tiene el dominio " + limpio + ".");
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        byte[] bytes = new byte[16];
        aleatorio.nextBytes(bytes);
        DominioVerificado guardado = dominioRepository.save(DominioVerificado.builder()
                .empresa(empresa)
                .dominio(limpio)
                .rolPorDefecto(rolPorDefecto)
                .token(HexFormat.of().formatHex(bytes))
                .creadoEn(LocalDateTime.now())
                .build());
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.EMPRESA, empresaId,
                DOMINIO + limpio + " agregado, por verificar.");
        return respuesta(guardado);
    }

    @Override
    @Transactional
    public DominioResponse verificar(Long empresaId, Long autorId, Long dominioId) {
        DominioVerificado dominio = dominioRepository.findByIdAndEmpresaId(dominioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
        if (dominio.getVerificadoEn() != null) {
            return respuesta(dominio);
        }
        String esperado = PREFIJO_TXT + dominio.getToken();
        if (dns.registrosTxt(dominio.getDominio()).stream().map(String::strip).noneMatch(esperado::equals)) {
            throw new SolicitudInvalidaException("El registro TXT \"" + esperado + "\" todavía no aparece en el DNS "
                    + "de " + dominio.getDominio() + ". Los cambios del DNS pueden tardar en verse.");
        }
        dominioRepository.findByDominioAndVerificadoEnIsNotNull(dominio.getDominio()).ifPresent(otro -> {
            throw new ReglaNegocioException("Otra tienda ya verificó el dominio " + dominio.getDominio() + ".");
        });
        dominio.setVerificadoEn(LocalDateTime.now());
        dominioRepository.saveAndFlush(dominio);
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.EMPRESA, empresaId,
                DOMINIO + dominio.getDominio() + " verificado: quien entre con Google o Microsoft desde él se "
                        + "une con rol " + dominio.getRolPorDefecto() + ".");
        return respuesta(dominio);
    }

    @Override
    @Transactional
    public void quitar(Long empresaId, Long autorId, Long dominioId) {
        DominioVerificado dominio = dominioRepository.findByIdAndEmpresaId(dominioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
        dominioRepository.delete(dominio);
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.EMPRESA, empresaId,
                DOMINIO + dominio.getDominio() + " quitado.");
    }

    private static DominioResponse respuesta(DominioVerificado dominio) {
        return new DominioResponse(dominio.getId(), dominio.getDominio(), dominio.getRolPorDefecto(),
                PREFIJO_TXT + dominio.getToken(), dominio.getVerificadoEn() != null, dominio.getVerificadoEn());
    }
}
