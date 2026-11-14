package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.service.impl.BorradoDeTiendas;
import com.facimus.procesos.gestion.service.impl.BorradoDelAlmacen;
import com.facimus.procesos.modelado.service.DocumentoService;

/** PR 61: los trabajos de la cola y la consulta que dice si un elemento existe. */
@ExtendWith(MockitoExtension.class)
class TrabajosDeDocumentosTest {

    @Mock
    private DocumentoService documentos;
    @Mock
    private ObjectProvider<DocumentoService> proveedor;
    @Mock
    private Almacen almacen;
    @Mock
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("la revision de una version va a la tienda del trabajo con el id de sus datos")
    void revision() {
        RevisionDeDocumentos revision = new RevisionDeDocumentos(proveedor);
        when(proveedor.getObject()).thenReturn(documentos);

        revision.ejecutar(new Trabajo(1, 7L, DocumentoServiceImpl.TRABAJO_DE_REVISION, "42", 1, 5));

        assertThat(revision.tipo()).isEqualTo("documentos.revisar");
        verify(documentos).revisar(7L, 42L);
    }

    @Test
    @DisplayName("borrar los archivos de una tienda borra su prefijo, y solo el suyo")
    void borrado() {
        BorradoDelAlmacen borrado = new BorradoDelAlmacen(almacen);

        borrado.ejecutar(new Trabajo(1, null, BorradoDeTiendas.BORRAR_ARCHIVOS, "12", 1, 5));

        assertThat(borrado.tipo()).isEqualTo("almacen.borrar-tienda");
        verify(almacen).borrarPrefijo("empresas/12/");
    }

    @Test
    @DisplayName("un elemento existe si alguna de las seis tablas lo tiene en ese proceso y esa tienda")
    void elementos() {
        ElementosDelProceso elementos = new ElementosDelProceso(jdbc);
        ArgumentCaptor<Object[]> parametros = ArgumentCaptor.forClass(Object[].class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), parametros.capture())).thenReturn(true, false, null);

        assertThat(elementos.existe(1L, 3L, "Lane_1")).isTrue();
        assertThat(elementos.existe(1L, 3L, "Lane_2")).isFalse();
        assertThat(elementos.existe(1L, 3L, "Lane_3")).isFalse();
        assertThat(parametros.getAllValues().getFirst()).containsExactly(3L, "Lane_1", 1L, 3L, "Lane_1", 1L, 3L,
                "Lane_1", 1L, 3L, "Lane_1", 1L, 3L, "Lane_1", 1L, 3L, "Lane_1", 1L);
    }
}
