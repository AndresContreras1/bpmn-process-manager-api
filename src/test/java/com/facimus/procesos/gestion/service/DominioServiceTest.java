package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.DominioResponse;
import com.facimus.procesos.gestion.model.DominioVerificado;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.repository.DominioVerificadoRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.service.impl.DominioServiceImpl;

/**
 * Los dominios de una tienda: se agregan con un token, se verifican cuando el TXT aparece en el DNS, y solo una tienda
 * puede tener verificado cada dominio.
 */
@ExtendWith(MockitoExtension.class)
class DominioServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Long AUTOR = 5L;
    private static final String PREFIJO = "bpmn-process-manager-verificacion=";

    @Mock
    private DominioVerificadoRepository dominioRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private ConsultaDns dns;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private DominioServiceImpl service;

    @Test
    @DisplayName("Agregar un dominio lo guarda en minusculas, con un token nuevo y sin verificar")
    void agregar() {
        when(empresaRepository.findById(EMPRESA)).thenReturn(Optional.of(empresa()));
        when(dominioRepository.save(any(DominioVerificado.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        ArgumentCaptor<DominioVerificado> guardado = ArgumentCaptor.forClass(DominioVerificado.class);

        DominioResponse primero = service.agregar(EMPRESA, AUTOR, "  Acme.COM ", RolAcceso.EDITOR);
        DominioResponse segundo = service.agregar(EMPRESA, AUTOR, "otra.com", RolAcceso.SOLO_LECTURA);

        verify(dominioRepository, times(2)).save(guardado.capture());
        DominioVerificado dominio = guardado.getAllValues().getFirst();
        assertThat(dominio.getDominio()).isEqualTo("acme.com");
        assertThat(dominio.getEmpresa().getId()).isEqualTo(EMPRESA);
        assertThat(dominio.getRolPorDefecto()).isEqualTo(RolAcceso.EDITOR);
        assertThat(dominio.getToken()).matches("[0-9a-f]{32}");
        assertThat(dominio.getCreadoEn()).isNotNull();
        assertThat(primero.registroTxt()).isEqualTo(PREFIJO + dominio.getToken());
        assertThat(primero.verificado()).isFalse();
        assertThat(primero.verificadoEn()).isNull();
        assertThat(segundo.registroTxt()).isNotEqualTo(primero.registroTxt());
        verify(historialCambioService).registrarDeTienda(EMPRESA, AUTOR, RecursoDeHistorial.EMPRESA, EMPRESA,
                "Dominio acme.com agregado, por verificar.");
    }

    @Test
    @DisplayName("Un dominio que la tienda ya tiene no se agrega dos veces")
    void agregarRepetido() {
        when(dominioRepository.existsByEmpresaIdAndDominio(EMPRESA, "acme.com")).thenReturn(true);

        assertThatThrownBy(() -> service.agregar(EMPRESA, AUTOR, "ACME.com", RolAcceso.EDITOR))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("La tienda ya tiene el dominio acme.com.");
        assertThatThrownBy(() -> service.agregar(8L, AUTOR, "acme.com", RolAcceso.EDITOR))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(dominioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Con el TXT en el DNS, el dominio queda verificado y se anota con su rol")
    void verificar() {
        DominioVerificado dominio = dominio(null);
        when(dominioRepository.findByIdAndEmpresaId(3L, EMPRESA)).thenReturn(Optional.of(dominio));
        when(dns.registrosTxt("acme.com")).thenReturn(List.of("v=spf1 -all", "  " + PREFIJO + "abc123  "));

        DominioResponse respuesta = service.verificar(EMPRESA, AUTOR, 3L);

        assertThat(respuesta.verificado()).isTrue();
        assertThat(respuesta.verificadoEn()).isNotNull().isEqualTo(dominio.getVerificadoEn());
        assertThat(respuesta.id()).isEqualTo(3L);
        assertThat(respuesta.dominio()).isEqualTo("acme.com");
        assertThat(respuesta.rolPorDefecto()).isEqualTo(RolAcceso.EDITOR);
        verify(dominioRepository).saveAndFlush(dominio);
        verify(historialCambioService).registrarDeTienda(EMPRESA, AUTOR, RecursoDeHistorial.EMPRESA, EMPRESA,
                "Dominio acme.com verificado: quien entre con Google o Microsoft desde él se une con rol EDITOR.");
        verify(auditoria).registrar(EMPRESA, AUTOR, "DOMINIO_VERIFICADO", "EMPRESA", EMPRESA, Map.of("rol", "EDITOR"));
    }

    @Test
    @DisplayName("Un dominio ya verificado no vuelve a mirar el DNS")
    void yaVerificado() {
        LocalDateTime antes = LocalDateTime.now().minusDays(1);
        when(dominioRepository.findByIdAndEmpresaId(3L, EMPRESA)).thenReturn(Optional.of(dominio(antes)));

        assertThat(service.verificar(EMPRESA, AUTOR, 3L).verificadoEn()).isEqualTo(antes);
        verify(dns, never()).registrosTxt(any());
        verify(dominioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Sin el TXT exacto no se verifica, y si otra tienda ya lo verifico tampoco")
    void verificarRechazos() {
        DominioVerificado dominio = dominio(null);
        when(dominioRepository.findByIdAndEmpresaId(3L, EMPRESA)).thenReturn(Optional.of(dominio));
        when(dns.registrosTxt("acme.com")).thenReturn(List.of(PREFIJO + "otro"), List.of(PREFIJO + "abc123"));

        assertThatThrownBy(() -> service.verificar(EMPRESA, AUTOR, 3L)).isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("\"" + PREFIJO + "abc123\"");

        when(dominioRepository.findByDominioAndVerificadoEnIsNotNull("acme.com"))
                .thenReturn(Optional.of(dominio(LocalDateTime.now())));
        assertThatThrownBy(() -> service.verificar(EMPRESA, AUTOR, 3L)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Otra tienda ya verificó el dominio acme.com.");
        assertThat(dominio.getVerificadoEn()).isNull();
        verify(dominioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Listar devuelve cada dominio con su registro TXT; quitar lo borra y lo anota")
    void listarYQuitar() {
        DominioVerificado dominio = dominio(null);
        when(dominioRepository.findAllByEmpresaIdOrderByDominioAsc(EMPRESA)).thenReturn(List.of(dominio));
        assertThat(service.listar(EMPRESA)).containsExactly(new DominioResponse(3L, "acme.com", RolAcceso.EDITOR,
                PREFIJO + "abc123", false, null));

        when(dominioRepository.findByIdAndEmpresaId(3L, EMPRESA)).thenReturn(Optional.of(dominio));
        service.quitar(EMPRESA, AUTOR, 3L);
        verify(dominioRepository).delete(dominio);
        verify(historialCambioService).registrarDeTienda(EMPRESA, AUTOR, RecursoDeHistorial.EMPRESA, EMPRESA,
                "Dominio acme.com quitado.");
        assertThatThrownBy(() -> service.quitar(EMPRESA, AUTOR, 4L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.verificar(EMPRESA, AUTOR, 4L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private static DominioVerificado dominio(LocalDateTime verificadoEn) {
        return DominioVerificado.builder()
                .id(3L)
                .empresa(empresa())
                .dominio("acme.com")
                .rolPorDefecto(RolAcceso.EDITOR)
                .token("abc123")
                .creadoEn(LocalDateTime.now())
                .verificadoEn(verificadoEn)
                .build();
    }

    private static Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        return empresa;
    }
}
