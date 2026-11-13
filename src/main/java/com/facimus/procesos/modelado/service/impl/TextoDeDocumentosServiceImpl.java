package com.facimus.procesos.modelado.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.archivos.ArchivosTemporales;
import com.facimus.procesos.common.archivos.ArchivosTemporales.Temporal;
import com.facimus.procesos.common.archivos.TipoDeArchivo;
import com.facimus.procesos.modelado.model.EstadoDocumento;
import com.facimus.procesos.modelado.repository.VersionDocumentoRepository;
import com.facimus.procesos.modelado.service.TextoDeDocumentosService;
import com.facimus.procesos.modelado.service.impl.ExtractorDeTexto.Resultado;

/**
 * PR 65: leer el texto de un archivo es mirar si hace falta, bajarlo del almacen a un archivo de paso, pasarlo por el
 * extractor y guardar lo que salga. Solo mirar y guardar tocan la base, cada uno en su transaccion corta: leer un
 * archivo puede tardar, y la base corta al minuto una transaccion abierta que no hace nada.
 * <p>Si el almacen no responde, el trabajo falla y la cola lo reintenta; si el archivo no se deja leer, eso no cambia
 * al reintentar: queda sin texto, con el motivo en el registro.
 */
@Service
public class TextoDeDocumentosServiceImpl implements TextoDeDocumentosService {

    /** El tipo de trabajo de la cola que lee el texto de cada version disponible. */
    public static final String TRABAJO_DE_LECTURA = "documentos.leer-texto";

    private static final Logger log = LoggerFactory.getLogger(TextoDeDocumentosServiceImpl.class);

    private final VersionDocumentoRepository versionRepository;
    private final Almacen almacen;
    private final ArchivosTemporales temporales;
    private final ExtractorDeTexto extractor;
    private final Clock reloj;
    private final TransactionTemplate lectura;
    private final TransactionTemplate escritura;

    public TextoDeDocumentosServiceImpl(VersionDocumentoRepository versionRepository, Almacen almacen,
            ArchivosTemporales temporales, ExtractorDeTexto extractor, Clock reloj,
            PlatformTransactionManager transacciones) {
        this.versionRepository = versionRepository;
        this.almacen = almacen;
        this.temporales = temporales;
        this.extractor = extractor;
        this.reloj = reloj;
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
        this.escritura = new TransactionTemplate(transacciones);
    }

    /** Lo que hace falta saber de una version para leerla. */
    private record Archivo(String clave, String tipo) {
    }

    @Override
    public void leer(Long empresaId, Long versionId) {
        Optional<Archivo> pendiente = lectura.execute(estado -> versionRepository.findByIdAndEmpresaId(versionId,
                empresaId).filter(version -> version.getEstado() == EstadoDocumento.DISPONIBLE
                        && version.getTextoExtraidoEn() == null)
                .map(version -> new Archivo(version.getClave(), version.getTipo())));
        pendiente.ifPresent(archivo -> guardar(empresaId, versionId, leer(archivo)));
    }

    private Resultado leer(Archivo archivo) {
        return TipoDeArchivo.deMime(archivo.tipo()).filter(TipoDeArchivo::tieneTexto)
                .map(tipo -> extraer(archivo.clave(), tipo))
                .orElseGet(() -> new Resultado(null, "Un archivo de tipo " + archivo.tipo() + " no tiene texto."));
    }

    /** Lo que se leyo, con la hora, aunque no haya texto: asi no se vuelve a intentar. */
    private void guardar(Long empresaId, Long versionId, Resultado resultado) {
        String motivo = resultado.motivo();
        if (resultado.texto() == null) {
            log.warn("La version {} de la tienda {} se busca solo por su nombre: {}", versionId, empresaId, motivo);
        } else if (motivo != null) {
            log.info("La version {} de la tienda {} se busca por una parte de su texto: {}", versionId, empresaId,
                    motivo);
        }
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
        escritura.executeWithoutResult(
                estado -> versionRepository.guardarTexto(empresaId, versionId, resultado.texto(), ahora));
    }

    /** El archivo se lee de una copia local: el parser de un PDF o de un ZIP necesita ir y volver por el. */
    private Resultado extraer(String clave, TipoDeArchivo tipo) {
        try (Temporal copia = temporales.nuevo("texto-"); InputStream contenido = almacen.abrir(clave)) {
            Files.copy(contenido, copia.ruta(), StandardCopyOption.REPLACE_EXISTING);
            return extractor.extraer(copia.ruta(), tipo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo bajar " + clave + " del almacen", e);
        }
    }
}
