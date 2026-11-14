#!/bin/sh
# PR 67: los respaldos de la base con pgBackRest, en un repositorio S3 con Object Lock y cifrado con RESPALDO_CLAVE.
# Un solo script con cada tarea, para que la imagen lleve una sola cosa que mantener:
#
#   respaldo certificado        el certificado TLS del almacen, si falta: pgBackRest solo habla S3 por TLS
#   respaldo programar          el servicio respaldo del compose: prepara el repositorio y respalda cada dia
#   respaldo ahora [tipo]       un respaldo ya (full, diff o incr), con su conteo y su punto de restauracion
#   respaldo salud              sano si el ultimo respaldo tiene menos de 26 horas (la sonda del servicio)
#   respaldo restaurar [--hasta <momento> | --ultimo]
#                               en un contenedor vacio: restaura y arranca PostgreSQL sobre lo restaurado
#   respaldo comparar           en el restaurado: las filas de cada tabla contra las que conto el respaldo
#   respaldo contar             las filas de cada tabla de la base a la que apunta PGHOST
#
# pgBackRest se configura con sus variables PGBACKREST_* (compose.yaml); psql, con PGHOST, PGUSER y PGDATABASE.
set -eu

# El dia de la semana del respaldo completo (1 lunes ... 7 domingo) y la hora UTC desde la que se respalda cada dia
DIA_COMPLETO="${RESPALDO_DIA_COMPLETO:-7}"
HORA="${RESPALDO_HORA:-1}"
# Cada cuanto mira el programador si ya toca, en segundos
PAUSA="${RESPALDO_PAUSA:-300}"
# Un respaldo mas viejo que esto se repone en cuanto se puede, sin esperar la hora; y la sonda lo da por enfermo
VIEJO=$((26 * 3600))
# Cuantos dias nadie puede borrar lo que se escribe en el bucket de respaldos (Object Lock, modo compliance)
BLOQUEO_DIAS="${RESPALDO_BLOQUEO_DIAS:-21}"
# Donde guarda la restauracion lo que restauro, para que comparar lo sepa
RESTAURADO=/tmp/restauracion

# Las filas de cada tabla del esquema public, una por linea: tabla y numero. Una sola sentencia, una sola foto.
CONTEO="select tablename, (xpath('/row/n/text()', query_to_xml(format('select count(*) as n from public.%I',
    tablename), false, true, '')))[1]::text from pg_tables where schemaname = 'public' order by tablename"

registrar() {
    printf '%s respaldo: %s\n' "$(date -u +%FT%TZ)" "$*"
}

fallar() {
    registrar "$*" >&2
    exit 1
}

uso() {
    sed -n '5,12p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
}

# --- El bucket del repositorio --------------------------------------------------------------------------------------

# Una peticion firmada (SigV4) al bucket de respaldos. Las credenciales van por la entrada estandar, no en la linea
# de ordenes, donde cualquiera que liste los procesos las veria.
s3() {
    ruta=$1
    shift
    printf 'user = "%s:%s"\n' "$(escapar "$PGBACKREST_REPO1_S3_KEY")" "$(escapar "$PGBACKREST_REPO1_S3_KEY_SECRET")" |
        curl -sS -K - --aws-sigv4 "aws:amz:${PGBACKREST_REPO1_S3_REGION}:s3" \
            --cacert "$PGBACKREST_REPO1_STORAGE_CA_FILE" "$@" \
            "https://${PGBACKREST_REPO1_S3_ENDPOINT}:${PGBACKREST_REPO1_STORAGE_PORT:-443}/${PGBACKREST_REPO1_S3_BUCKET}${ruta}"
}

# Comillas y barras invertidas, escapadas para el archivo de configuracion de curl
escapar() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

# El bucket con Object Lock, que solo se puede activar al crearlo, y la retencion que se aplica a cada objeto nuevo.
# Es para el almacen del compose; en AWS el bucket se crea antes, en otra cuenta, y RESPALDO_CREAR_BUCKET va en false.
crear_bucket() {
    codigo=$(s3 "" -I -o /dev/null -w '%{http_code}') || codigo=000
    case "$codigo" in
        200) ;;
        404)
            registrar "Creando el bucket $PGBACKREST_REPO1_S3_BUCKET con Object Lock."
            s3 "" -f -X PUT -H 'x-amz-bucket-object-lock-enabled: true' -o /dev/null
            ;;
        *) fallar "El almacen respondio $codigo al buscar el bucket $PGBACKREST_REPO1_S3_BUCKET." ;;
    esac
    regla="<ObjectLockConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
    regla="$regla<ObjectLockEnabled>Enabled</ObjectLockEnabled><Rule><DefaultRetention><Mode>COMPLIANCE</Mode>"
    regla="$regla<Days>$BLOQUEO_DIAS</Days></DefaultRetention></Rule></ObjectLockConfiguration>"
    md5=$(printf '%s' "$regla" | openssl dgst -md5 -binary | openssl base64)
    s3 "?object-lock" -f -X PUT -H "Content-MD5: $md5" -H 'Content-Type: application/xml' \
        --data-binary "$regla" -o /dev/null
}

# --- Respaldar ------------------------------------------------------------------------------------------------------

esperar_base() {
    until pg_isready -q; do
        sleep 2
    done
}

# El repositorio listo: el bucket, la stanza y el archivo de WAL funcionando. La primera vez el archivo puede tardar:
# PostgreSQL reintenta los segmentos que fallaron mientras la stanza no existia.
preparar() {
    esperar_base
    if [ "${RESPALDO_CREAR_BUCKET:-false}" = true ]; then
        crear_bucket
    fi
    pgbackrest stanza-create
    intentos=0
    until pgbackrest check; do
        intentos=$((intentos + 1))
        [ "$intentos" -lt 5 ] || fallar "El archivo de WAL no llega al repositorio."
        sleep 30
    done
}

ultima_etiqueta() {
    pgbackrest info --output=json | jq -r '.[0].backup[-1].label // empty'
}

# Un valor anotado en un respaldo
anotacion() {
    pgbackrest info --set="$1" --output=json | jq -r --arg clave "$2" '.[0].backup[0].annotation[$clave] // empty'
}

# Un respaldo, y despues su conteo de filas y su punto de restauracion.
respaldar() {
    tipo=$1
    registrar "Respaldo $tipo."
    pgbackrest backup --type="$tipo" || return 1
    marcar "$(ultima_etiqueta)"
}

# Una transaccion: cada tabla bloqueada sin esperar, el conteo y el punto de restauracion, en ese orden
contar_en_pausa() {
    psql -X -q -v ON_ERROR_STOP=1 -At -F ' ' -v punto="$1" <<SQL
begin;
do \$\$
declare
    tabla text;
begin
    for tabla in select tablename from pg_tables where schemaname = 'public' order by tablename loop
        execute format('lock table public.%I in share mode nowait', tabla);
    end loop;
end
\$\$;
$CONTEO;
select '-punto', pg_create_restore_point(:'punto');
commit;
SQL
}

# Cuenta las filas con las escrituras en pausa y deja un punto de restauracion en ese mismo instante: restaurar hasta
# ese punto tiene que dar exactamente esas filas. Las tablas se bloquean en modo SHARE, que deja leer y no escribir,
# solo lo que tarda el conteo. Sin esperar a nadie (NOWAIT): si una escritura tiene una tabla, se suelta lo tomado y
# se intenta otra vez, asi el conteo nunca entra en un abrazo mortal con una migracion o una transaccion de la API.
# Si en diez intentos no lo consigue, el respaldo queda sin conteo (restaurarlo con --hasta o --ultimo sigue sirviendo).
marcar() {
    etiqueta=$1
    punto="respaldo-$etiqueta"
    intento=1
    until conteo=$(contar_en_pausa "$punto" 2>/dev/null); do
        if [ "$intento" -ge 10 ]; then
            registrar "No se pudo contar con las escrituras en pausa: el respaldo $etiqueta queda sin conteo."
            return 0
        fi
        intento=$((intento + 1))
        sleep 3
    done
    set -- "--annotation=punto=$punto" "--annotation=contado=$(date -u +%FT%TZ)"
    tablas=0
    while read -r tabla filas; do
        case "$tabla" in
            -punto | "") ;;
            *)
                set -- "$@" "--annotation=filas.$tabla=$filas"
                tablas=$((tablas + 1))
                ;;
        esac
    done <<EOF
$conteo
EOF
    # Cuantas tablas se contaron, tambien si son cero: distingue una base vacia de un respaldo sin conteo
    pgbackrest annotate --set="$etiqueta" "$@" "--annotation=tablas=$tablas" || return 1
    # El segmento con el punto de restauracion, al repositorio ya: check cambia de segmento y espera a que llegue
    pgbackrest check || return 1
    registrar "Respaldo $etiqueta: $tablas tablas contadas, punto de restauracion $punto."
}

# Cuando empezo y cuando termino el ultimo respaldo, en segundos desde 1970; nada si no hay ninguno
ultimo_respaldo() {
    pgbackrest info --output=json | jq -r '.[0].backup[-1] // empty | "\(.timestamp.start) \(.timestamp.stop)"'
}

# Toca si hoy no hubo respaldo y ya es la hora. Sin ninguno, o con el ultimo de hace mas de 26 horas (el contenedor
# estuvo parado, o fallo), toca ya: una base nueva queda protegida desde que arranca.
toca_respaldar() {
    ultimo=$(ultimo_respaldo)
    [ -n "$ultimo" ] || return 0
    inicio=${ultimo%% *}
    [ $(($(date +%s) - inicio)) -lt "$VIEJO" ] || return 0
    [ "$inicio" -lt "$(date -u -d "$(date -u +%F)" +%s)" ] && [ "$(date -u +%-H)" -ge "$HORA" ]
}

# Sano si el ultimo respaldo termino hace menos de 26 horas. docker compose ps lo muestra, y up --wait lo espera.
salud() {
    ultimo=$(ultimo_respaldo)
    [ -n "$ultimo" ] && [ $(($(date +%s) - ${ultimo##* })) -lt "$VIEJO" ]
}

tipo_de_hoy() {
    if [ "$(date -u +%u)" = "$DIA_COMPLETO" ]; then
        echo full
    else
        # pgBackRest lo convierte en completo si todavia no hay ninguno
        echo diff
    fi
}

# Un bucle y no cron: si el contenedor estuvo parado a la hora del respaldo, respalda en cuanto vuelve. Un respaldo
# que falla se intenta otra vez en la siguiente vuelta.
programar() {
    preparar
    registrar "Respaldo completo los dias $DIA_COMPLETO, diferencial los demas, desde las $HORA UTC."
    while :; do
        if toca_respaldar; then
            respaldar "$(tipo_de_hoy)" || registrar "El respaldo fallo; se intenta otra vez en $PAUSA s."
        fi
        sleep "$PAUSA"
    done
}

# --- Restaurar ------------------------------------------------------------------------------------------------------

# En un contenedor con PGDATA vacio. Sin opciones, el ultimo respaldo hasta su punto de restauracion, que es donde se
# contaron sus filas; con --hasta, el momento dado ('2026-11-12 10:00:00+00'); con --ultimo, hasta el ultimo segmento
# de WAL del repositorio. Sin opciones, RESPALDO_RESTAURAR_HASTA dice lo mismo: vacio, un momento o "ultimo", que es
# como lo pasa el compose. Despues arranca PostgreSQL sin archivar: lo restaurado no escribe en el repositorio.
restaurar() {
    if [ $# -eq 0 ]; then
        case "${RESPALDO_RESTAURAR_HASTA:-}" in
            "") ;;
            ultimo) set -- --ultimo ;;
            *) set -- --hasta "$RESPALDO_RESTAURAR_HASTA" ;;
        esac
    fi
    modo=punto
    case "${1:-}" in
        "") ;;
        --hasta)
            modo=hasta
            destino=${2:?falta el momento: --hasta "2026-11-12 10:00:00+00"}
            ;;
        --ultimo) modo=ultimo ;;
        *) uso ;;
    esac
    etiqueta=$(ultima_etiqueta)
    [ -n "$etiqueta" ] || fallar "No hay respaldos en el repositorio."
    case "$modo" in
        punto)
            punto=$(anotacion "$etiqueta" punto)
            [ -n "$punto" ] || fallar "El respaldo $etiqueta no tiene punto de restauracion: use --hasta o --ultimo."
            registrar "Restaurando $etiqueta hasta su punto $punto."
            set -- --set="$etiqueta" --type=name --target="$punto" --target-action=promote
            ;;
        hasta)
            registrar "Restaurando hasta $destino."
            set -- --type=time --target="$destino" --target-action=promote
            ;;
        ultimo)
            registrar "Restaurando hasta el ultimo segmento del repositorio."
            set --
            ;;
    esac
    # pgBackRest elige el respaldo cuando se le da un momento; lo que eligio queda en lo que escribe
    if ! pgbackrest restore "$@" >/tmp/restaurar.log 2>&1; then
        cat /tmp/restaurar.log >&2
        fallar "La restauracion fallo."
    fi
    cat /tmp/restaurar.log
    elegido=$(sed -n 's/.*restore backup set \([^, ]*\).*/\1/p' /tmp/restaurar.log | head -n 1)
    printf '%s %s\n' "$modo" "${elegido:-$etiqueta}" >"$RESTAURADO"
    exec postgres -c shared_preload_libraries=pg_stat_statements
}

contar() {
    psql -X -q -v ON_ERROR_STOP=1 -At -F ' ' -c "$CONTEO"
}

# Las filas de lo restaurado contra las que conto el respaldo. Solo cuando se restauro hasta su punto: con --hasta o
# --ultimo la base tiene lo que paso despues, y solo se muestran.
comparar() {
    read -r modo etiqueta <"$RESTAURADO" || fallar "Este contenedor no restauro nada."
    actuales=$(contar | sort)
    if [ "$modo" != punto ]; then
        registrar "Restaurado con --$modo: las filas no se comparan con las del respaldo $etiqueta."
        printf '%s\n' "$actuales"
        return 0
    fi
    [ -n "$(anotacion "$etiqueta" tablas)" ] || fallar "El respaldo $etiqueta no tiene conteo de filas."
    esperadas=$(pgbackrest info --set="$etiqueta" --output=json | jq -r '.[0].backup[0].annotation // {}
        | to_entries[] | select(.key | startswith("filas.")) | "\(.key[6:]) \(.value)"' | sort)
    if [ "$esperadas" != "$actuales" ]; then
        registrar "Las filas no coinciden con las del respaldo $etiqueta (< respaldo, > restaurado):" >&2
        printf '%s\n' "$esperadas" >/tmp/esperadas
        printf '%s\n' "$actuales" >/tmp/actuales
        diff /tmp/esperadas /tmp/actuales >&2 || true
        exit 1
    fi
    registrar "Filas: las $(printf '%s\n' "$actuales" | wc -l) tablas tienen las mismas que conto el respaldo $etiqueta."
}

# --- El certificado del almacen -------------------------------------------------------------------------------------

# Autofirmado, para el nombre del almacen dentro de la red del compose. Quien habla con el (pgBackRest) lo tiene como
# su unica autoridad: no confia en ningun otro certificado para ese nombre. La clave queda en su propia carpeta, que
# solo monta el almacen; SeaweedFS corre con otro usuario, asi que esa clave se puede leer dentro de la carpeta. Lo que
# montan los demas es solo el certificado.
certificado() {
    carpeta=${ALMACEN_TLS:-/etc/almacen-tls}
    publica=${ALMACEN_CA:-/etc/almacen-ca}
    nombre=${ALMACEN_TLS_NOMBRE:-almacen}
    if [ ! -s "$carpeta/almacen.crt" ] || [ ! -s "$carpeta/almacen.key" ]; then
        openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -days 3650 -subj "/CN=$nombre" \
            -addext "subjectAltName=DNS:$nombre" -keyout "$carpeta/almacen.key.nuevo" \
            -out "$carpeta/almacen.crt.nuevo" 2>/dev/null
        chmod 0644 "$carpeta/almacen.key.nuevo" "$carpeta/almacen.crt.nuevo"
        mv "$carpeta/almacen.key.nuevo" "$carpeta/almacen.key"
        mv "$carpeta/almacen.crt.nuevo" "$carpeta/almacen.crt"
        registrar "Certificado del almacen creado para $nombre, diez anos."
    fi
    cp "$carpeta/almacen.crt" "$publica/almacen.crt.nuevo"
    mv "$publica/almacen.crt.nuevo" "$publica/almacen.crt"
}

orden=${1:-}
[ $# -eq 0 ] || shift
case "$orden" in
    certificado) certificado ;;
    programar) programar ;;
    ahora)
        esperar_base
        respaldar "${1:-diff}"
        ;;
    salud) salud ;;
    restaurar) restaurar "$@" ;;
    comparar) comparar ;;
    contar) contar ;;
    *) uso ;;
esac
