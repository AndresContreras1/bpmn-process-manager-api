package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Documento;
import com.facimus.procesos.modelado.model.EstadoDocumento;
import com.facimus.procesos.modelado.model.VersionDocumento;
import com.facimus.procesos.modelado.repository.DocumentoRepository;
import com.facimus.procesos.modelado.repository.VersionDocumentoRepository;

/** PR 69: que documento sirve como respuesta de un campo ARCHIVO. */
@ExtendWith(MockitoExtension.class)
class ArchivosDeFormulariosEnDocumentosTest {

    private static final Long TIENDA = 1L;
    private static final Long PROCESO = 100L;
    private static final Long DOCUMENTO = 40L;

    @Mock
    private DocumentoRepository documentoRepository;
    @Mock
    private VersionDocumentoRepository versionDocumentoRepository;

    @InjectMocks
    private ArchivosDeFormulariosEnDocumentos archivos;

    private Empresa empresa;
    private Documento documento;

    @BeforeEach
    void unDocumentoEnSuTerceraVersion() {
        empresa = Empresa.builder().id(TIENDA).nombre("Demo Store").build();
        documento = Documento.builder().id(DOCUMENTO).empresa(empresa)
                .proceso(Proceso.builder().id(PROCESO).empresa(empresa).nombre("Refunds").build())
                .nombre("invoice.pdf").versionActual(3).build();
    }

    private void conUltimaVersion(EstadoDocumento estado) {
        when(documentoRepository.findByIdAndEmpresaId(DOCUMENTO, TIENDA)).thenReturn(Optional.of(documento));
        when(versionDocumentoRepository.findByDocumento_IdAndNumeroAndEmpresaId(DOCUMENTO, 3, TIENDA))
                .thenReturn(Optional.of(VersionDocumento.builder().empresa(empresa).documento(documento).numero(3)
                        .estado(estado).build()));
    }

    @Test
    @DisplayName("Un documento del proceso cuya ultima version paso el antivirus sirve")
    void disponible() {
        conUltimaVersion(EstadoDocumento.DISPONIBLE);

        assertThat(archivos.disponible(TIENDA, PROCESO, DOCUMENTO)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = EstadoDocumento.class, names = "DISPONIBLE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Uno que sigue en revision o quedo retenido, no")
    void noDisponible(EstadoDocumento estado) {
        conUltimaVersion(estado);

        assertThat(archivos.disponible(TIENDA, PROCESO, DOCUMENTO)).isFalse();
    }

    @Test
    @DisplayName("Uno de otro proceso no sirve, aunque este listo")
    void deOtroProceso() {
        when(documentoRepository.findByIdAndEmpresaId(DOCUMENTO, TIENDA)).thenReturn(Optional.of(documento));

        assertThat(archivos.disponible(TIENDA, 200L, DOCUMENTO)).isFalse();
        verify(versionDocumentoRepository, never()).findByDocumento_IdAndNumeroAndEmpresaId(anyLong(), anyInt(),
                anyLong());
    }

    @Test
    @DisplayName("Uno que no es de la tienda, o que no existe, no sirve")
    void deOtraTienda() {
        when(documentoRepository.findByIdAndEmpresaId(DOCUMENTO, 2L)).thenReturn(Optional.empty());

        assertThat(archivos.disponible(2L, PROCESO, DOCUMENTO)).isFalse();
    }

    @Test
    @DisplayName("Sin la version que dice ser la ultima, no sirve")
    void sinSuVersion() {
        when(documentoRepository.findByIdAndEmpresaId(DOCUMENTO, TIENDA)).thenReturn(Optional.of(documento));
        when(versionDocumentoRepository.findByDocumento_IdAndNumeroAndEmpresaId(DOCUMENTO, 3, TIENDA))
                .thenReturn(Optional.empty());

        assertThat(archivos.disponible(TIENDA, PROCESO, DOCUMENTO)).isFalse();
    }
}
