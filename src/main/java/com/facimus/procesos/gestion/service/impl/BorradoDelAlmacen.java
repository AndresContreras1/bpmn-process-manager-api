package com.facimus.procesos.gestion.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.common.almacen.ClaveDeAlmacen;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.service.impl.BorradoDeTiendas.TiendaBorrada;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 61: borra del almacen lo que guardaba una tienda borrada: sus documentos, con todas sus versiones, sus manuales y
 * sus exportaciones. El ancla de la bitacora no: va con la bitacora, que sobrevive a su tienda, y el almacen no la
 * dejaria borrar. Borrar dos veces el mismo prefijo no hace nada, asi que la cola lo puede reintentar sin cuidado.
 * <p>
 * PR 66: despues emite el certificado de borrado: un JSON que dice que tienda (solo su id), cuando, cuantas filas se
 * borraron de cada tabla, cuantos objetos del almacen y la huella de su ultima exportacion, sin un dato de ninguna
 * persona. Va al almacen inmutable, junto a las anclas (en S3, el bucket con Object Lock), donde nadie lo cambia ni lo
 * borra durante su retencion, y su SHA-256 queda en la bitacora de la tienda, que tambien la sobrevive. Lo guarda el
 * operador: la tienda ya no tiene a nadie que entre. Un certificado ya emitido no se emite otra vez.
 */
@Component
public class BorradoDelAlmacen implements ManejadorDeTrabajo {

    /** La version del formato del certificado. */
    static final int FORMATO = 1;

    /** Lo que sigue en la base despues del borrado: la lapida de la tienda, su bitacora y las anclas de la bitacora. */
    static final List<String> CONSERVADAS = List.of("empresas", "bitacora", "anclas_bitacora");

    private final Almacen almacen;
    private final Auditoria auditoria;
    private final JsonMapper json;
    private final Clock reloj;
    private final Duration retencion;

    public BorradoDelAlmacen(Almacen almacen, Auditoria auditoria, JsonMapper json, Clock reloj,
            @Value("${tiendas.certificado.retencion:3650d}") Duration retencion) {
        this.almacen = almacen;
        this.auditoria = auditoria;
        this.json = json;
        this.reloj = reloj;
        this.retencion = retencion;
    }

    @Override
    public String tipo() {
        return BorradoDeTiendas.BORRAR_ARCHIVOS;
    }

    @Override
    public void ejecutar(Trabajo trabajo) {
        TiendaBorrada tienda = json.readValue(trabajo.datos(), TiendaBorrada.class);
        String clave = ClaveDeAlmacen.deCertificado(tienda.empresaId());
        if (yaEmitido(clave)) {
            return;
        }
        String prefijo = ClaveDeAlmacen.deEmpresa(tienda.empresaId());
        int objetos = almacen.borrarPrefijo(prefijo);
        byte[] certificado = json.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(certificado(tienda, prefijo, objetos));
        String huella = Huella.de(certificado);
        boolean inmutable = almacen.guardarInmutable(clave, certificado, reloj.instant().plus(retencion));
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("certificado", huella);
        detalle.put("clave", clave);
        detalle.put("inmutable", inmutable);
        detalle.put("filas", tienda.totalDeFilas());
        detalle.put("objetos", objetos);
        auditoria.registrar(tienda.empresaId(), null, "TIENDA_BORRADA", "EMPRESA", tienda.empresaId(), detalle);
    }

    /** Lo que dice el certificado, en este orden. */
    private Map<String, Object> certificado(TiendaBorrada tienda, String prefijo, int objetos) {
        Map<String, Object> almacenado = new LinkedHashMap<>();
        almacenado.put("prefijo", prefijo);
        almacenado.put("objetosBorrados", objetos);
        Map<String, Object> certificado = new LinkedHashMap<>();
        certificado.put("formato", FORMATO);
        certificado.put("tipo", "certificado-de-borrado");
        certificado.put("tienda", tienda.empresaId());
        certificado.put("bajaSolicitadaEn", texto(tienda.bajaSolicitadaEn()));
        certificado.put("borradaEn", texto(tienda.borradaEn()));
        certificado.put("emitidoEn", texto(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS)));
        certificado.put("zonaHoraria", "UTC");
        certificado.put("filas", tienda.filas());
        certificado.put("totalDeFilas", tienda.totalDeFilas());
        certificado.put("almacen", almacenado);
        certificado.put("ultimaExportacion", tienda.ultimaExportacion() == null ? null
                : ultimaExportacion(tienda.ultimaExportacion()));
        certificado.put("conservadas", CONSERVADAS);
        return certificado;
    }

    private static Map<String, Object> ultimaExportacion(BorradoDeTiendas.UltimaExportacion ultima) {
        Map<String, Object> exportacion = new LinkedHashMap<>();
        exportacion.put("id", ultima.id());
        exportacion.put("sha256", ultima.sha256());
        exportacion.put("listaEn", texto(ultima.listaEn()));
        return exportacion;
    }

    private static String texto(LocalDateTime momento) {
        return momento == null ? null : momento.toString();
    }

    /** Si el certificado ya esta en el almacen inmutable: un reintento de la cola no emite otro. */
    private boolean yaEmitido(String clave) {
        try (InputStream _ = almacen.abrirInmutable(clave)) {
            return true;
        } catch (ArchivoNoEncontradoException _) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + clave, e);
        }
    }
}
