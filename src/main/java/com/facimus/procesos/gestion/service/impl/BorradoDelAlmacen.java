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
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.common.almacen.ClaveDeAlmacen;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.service.BitacoraService;
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
 * operador: la tienda ya no tiene a nadie que entre.
 * <p>
 * Un certificado ya emitido no se emite otra vez, pero un reintento comprueba que su huella este en la bitacora: si el
 * intento anterior lo guardo y fallo antes de anotarlo, lo anota ahora con la huella del que esta guardado. Lo que dice
 * cuantos objetos se borraron es lo que borro el intento que lo emitio; desde el segundo intento, uno anterior pudo
 * haber borrado otros, y el certificado dice en que intento salio.
 */
@Component
public class BorradoDelAlmacen implements ManejadorDeTrabajo {

    /** La version del formato del certificado. */
    static final int FORMATO = 1;

    /** Lo que sigue en la base despues del borrado: la lapida de la tienda, su bitacora y las anclas de la bitacora. */
    static final List<String> CONSERVADAS = List.of("empresas", "bitacora", "anclas_bitacora");

    static final String TIENDA_BORRADA = "TIENDA_BORRADA";

    private static final String OBJETOS = "objetos";

    private final Almacen almacen;
    private final Auditoria auditoria;
    private final BitacoraService bitacora;
    private final JsonMapper json;
    private final Clock reloj;
    private final Duration retencion;

    public BorradoDelAlmacen(Almacen almacen, Auditoria auditoria, BitacoraService bitacora, JsonMapper json,
            Clock reloj, @Value("${tiendas.certificado.retencion:3650d}") Duration retencion) {
        this.almacen = almacen;
        this.auditoria = auditoria;
        this.bitacora = bitacora;
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
        TiendaBorrada tienda = tienda(trabajo.datos());
        String clave = ClaveDeAlmacen.deCertificado(tienda.empresaId());
        Optional<byte[]> emitido = emitido(clave);
        if (emitido.isPresent()) {
            if (!anotado(tienda.empresaId())) {
                byte[] certificado = emitido.get();
                Map<String, Object> detalle = detalle(tienda, clave, certificado,
                        json.readTree(certificado).path("almacen").path("objetosBorrados").asInt());
                detalle.put("reanotado", true);
                auditoria.registrar(tienda.empresaId(), null, TIENDA_BORRADA, "EMPRESA", tienda.empresaId(), detalle);
            }
            return;
        }
        String prefijo = ClaveDeAlmacen.deEmpresa(tienda.empresaId());
        int objetos = almacen.borrarPrefijo(prefijo);
        byte[] certificado = json.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(certificado(tienda, prefijo, objetos, trabajo.intento()));
        boolean inmutable = almacen.guardarInmutable(clave, certificado, reloj.instant().plus(retencion));
        Map<String, Object> detalle = detalle(tienda, clave, certificado, objetos);
        detalle.put("inmutable", inmutable);
        auditoria.registrar(tienda.empresaId(), null, TIENDA_BORRADA, "EMPRESA", tienda.empresaId(), detalle);
    }

    /**
     * Lo que lleva el trabajo. Uno encolado antes del PR 66 lleva solo el id de la tienda: se borra igual, y el
     * certificado dice que de ese borrado no se contaron las filas.
     */
    private TiendaBorrada tienda(String datos) {
        String limpio = datos.strip();
        if (!limpio.isEmpty() && limpio.chars().allMatch(letra -> letra >= '0' && letra <= '9')) {
            return new TiendaBorrada(Long.valueOf(limpio), null, null, null, 0, null);
        }
        return json.readValue(datos, TiendaBorrada.class);
    }

    /** Lo que la bitacora anota del certificado: su huella, donde esta y cuanto se borro, sin nulos. */
    private static Map<String, Object> detalle(TiendaBorrada tienda, String clave, byte[] certificado, int objetos) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("certificado", Huella.de(certificado));
        detalle.put("clave", clave);
        if (tienda.filas() != null) {
            detalle.put("filas", tienda.totalDeFilas());
        }
        detalle.put(OBJETOS, objetos);
        return detalle;
    }

    /** Lo que dice el certificado, en este orden. */
    private Map<String, Object> certificado(TiendaBorrada tienda, String prefijo, int objetos, int intento) {
        Map<String, Object> almacenado = new LinkedHashMap<>();
        almacenado.put("prefijo", prefijo);
        almacenado.put("objetosBorrados", objetos);
        almacenado.put("intento", intento);
        Map<String, Object> certificado = new LinkedHashMap<>();
        certificado.put("formato", FORMATO);
        certificado.put("tipo", "certificado-de-borrado");
        certificado.put("tienda", tienda.empresaId());
        certificado.put("bajaSolicitadaEn", texto(tienda.bajaSolicitadaEn()));
        certificado.put("borradaEn", texto(tienda.borradaEn()));
        certificado.put("emitidoEn", texto(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS)));
        certificado.put("zonaHoraria", "UTC");
        certificado.put("filas", tienda.filas());
        certificado.put("totalDeFilas", tienda.filas() == null ? null : tienda.totalDeFilas());
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

    /** El certificado, si ya esta en el almacen inmutable: un reintento de la cola no emite otro. */
    private Optional<byte[]> emitido(String clave) {
        try (InputStream contenido = almacen.abrirInmutable(clave)) {
            return Optional.of(contenido.readAllBytes());
        } catch (ArchivoNoEncontradoException _) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + clave, e);
        }
    }

    /** Si la bitacora de la tienda ya tiene su borrado. */
    private boolean anotado(Long empresaId) {
        return !bitacora.buscar(empresaId, new BitacoraService.Filtro(TIENDA_BORRADA, null, null, null), null, 1)
                .registros().isEmpty();
    }
}
