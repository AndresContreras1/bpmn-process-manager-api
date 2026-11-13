package com.facimus.procesos.common.almacen;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * PR 61: el almacen en un bucket S3, de AWS o de cualquier servicio compatible (SeaweedFS, el de cada nube). Los
 * documentos van en un bucket; el ancla de la bitacora, en otro creado con Object Lock, donde una version no se puede
 * borrar ni sobrescribir hasta su fecha, ni siquiera con las credenciales de la API. Sin ese segundo bucket, el ancla
 * va al primero sin bloqueo, y el metodo lo dice.
 */
public class AlmacenS3 implements Almacen {

    private static final int POR_LOTE = 1000;

    private final S3Client s3;
    private final String bucket;
    private final String bucketDeAnclas;
    private final boolean cifrar;

    /**
     * @param bucketDeAnclas el bucket con Object Lock, o vacio si no hay
     * @param cifrar pedir cifrado del lado del servidor (SSE-S3), si el servicio lo ofrece
     */
    public AlmacenS3(S3Client s3, String bucket, String bucketDeAnclas, boolean cifrar) {
        this.s3 = s3;
        this.bucket = bucket;
        this.bucketDeAnclas = bucketDeAnclas == null ? "" : bucketDeAnclas;
        this.cifrar = cifrar;
    }

    @Override
    public void guardar(String clave, Path archivo, String tipo) {
        s3.putObject(peticion -> {
            peticion.bucket(bucket).key(ClaveDeAlmacen.comprobar(clave)).contentType(tipo);
            if (cifrar) {
                peticion.serverSideEncryption(ServerSideEncryption.AES256);
            }
        }, RequestBody.fromFile(archivo));
    }

    @Override
    public InputStream abrir(String clave) {
        try {
            return s3.getObject(peticion -> peticion.bucket(bucket).key(ClaveDeAlmacen.comprobar(clave)));
        } catch (NoSuchKeyException _) {
            throw new ArchivoNoEncontradoException(clave);
        }
    }

    @Override
    public int borrarPrefijo(String prefijo) {
        int borrados = 0;
        String token = null;
        do {
            String siguiente = token;
            ListObjectsV2Response pagina = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket)
                    .prefix(prefijo).maxKeys(POR_LOTE).continuationToken(siguiente).build());
            List<ObjectIdentifier> claves = pagina.contents().stream()
                    .map(objeto -> ObjectIdentifier.builder().key(objeto.key()).build()).toList();
            if (!claves.isEmpty()) {
                s3.deleteObjects(peticion -> peticion.bucket(bucket).delete(borrado -> borrado.objects(claves)));
                borrados += claves.size();
            }
            token = Boolean.TRUE.equals(pagina.isTruncated()) ? pagina.nextContinuationToken() : null;
        } while (token != null);
        return borrados;
    }

    @Override
    public boolean guardarInmutable(String clave, byte[] contenido, Instant hasta) {
        boolean bloqueado = !bucketDeAnclas.isBlank();
        s3.putObject(peticion -> {
            peticion.bucket(bloqueado ? bucketDeAnclas : bucket).key(ClaveDeAlmacen.comprobar(clave))
                    .contentType("application/json");
            if (bloqueado) {
                peticion.objectLockMode(ObjectLockMode.COMPLIANCE).objectLockRetainUntilDate(hasta);
            }
        }, RequestBody.fromBytes(contenido));
        return bloqueado;
    }

    /** PR 66: del bucket con Object Lock si lo hay, que es donde {@link #guardarInmutable} lo dejo. */
    @Override
    public InputStream abrirInmutable(String clave) {
        String donde = bucketDeAnclas.isBlank() ? bucket : bucketDeAnclas;
        try {
            return s3.getObject(peticion -> peticion.bucket(donde).key(ClaveDeAlmacen.comprobar(clave)));
        } catch (NoSuchKeyException _) {
            throw new ArchivoNoEncontradoException(clave);
        }
    }

    /**
     * Crea los buckets que falten: el de anclas, con Object Lock, que solo se puede activar al crearlo. Es para el
     * SeaweedFS de compose.yaml; en AWS los buckets se crean antes, con sus politicas, y esto no se usa.
     */
    public void crearBucketsQueFalten() {
        if (!existe(bucket)) {
            s3.createBucket(peticion -> peticion.bucket(bucket));
        }
        if (!bucketDeAnclas.isBlank() && !existe(bucketDeAnclas)) {
            s3.createBucket(peticion -> peticion.bucket(bucketDeAnclas).objectLockEnabledForBucket(true));
        }
    }

    private boolean existe(String nombre) {
        try {
            s3.headBucket(peticion -> peticion.bucket(nombre));
            return true;
        } catch (NoSuchBucketException _) {
            return false;
        }
    }

    @Override
    public String descripcion() {
        return "S3, bucket " + bucket + (bucketDeAnclas.isBlank() ? "" : ", anclas en " + bucketDeAnclas);
    }
}
