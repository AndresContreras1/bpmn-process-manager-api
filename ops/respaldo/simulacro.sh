#!/bin/sh
# PR 67: el simulacro de restauracion, contra la pila de compose.yaml ya levantada. Mide el RPO, restaura el ultimo
# respaldo en un contenedor aparte con la base vacia, compara las filas de cada tabla con las que conto el respaldo,
# verifica con la API la cadena de la bitacora de cada tienda y la huella de cada version publicada, y mide el RTO.
#
#   ops/respaldo/simulacro.sh [-p proyecto] [--hasta '2026-11-12 10:00:00+00' | --ultimo] [--sin-origen]
#
#   -p proyecto     el nombre del proyecto de Compose, si la pila se levanto con uno
#   --hasta         restaura hasta ese momento en vez de hasta el punto del ultimo respaldo; las filas no se comparan
#   --ultimo        restaura hasta el ultimo segmento de WAL del repositorio; las filas tampoco se comparan
#   --sin-origen    la base de origen ya no esta (un desastre de verdad): no mide el RPO
#
# Sale con 0 si cada comprobacion pasa y con 1 si alguna no; lo que no cuadro queda escrito. Se corre desde cualquier
# carpeta, en Linux, macOS o Git Bash, con Docker y Docker Compose.
set -eu

cd "$(dirname "$0")/../.."
# Git Bash convierte en rutas de Windows los argumentos que parecen rutas, tambien las de dentro de los contenedores
MSYS_NO_PATHCONV=1
export MSYS_NO_PATHCONV

proyecto=
hasta=
origen=si
while [ $# -gt 0 ]; do
    case "$1" in
        -p)
            proyecto=${2:?falta el nombre del proyecto}
            shift 2
            ;;
        --hasta)
            hasta=${2:?falta el momento}
            shift 2
            ;;
        --ultimo)
            hasta=ultimo
            shift
            ;;
        --sin-origen)
            origen=no
            shift
            ;;
        *)
            sed -n '6,11p' "$0" | sed 's/^# \{0,1\}//' >&2
            exit 2
            ;;
    esac
done

# El compose lo lee al crear el contenedor que restaura; tiene que valer lo mismo en cada orden de este simulacro
RESPALDO_RESTAURAR_HASTA=$hasta
export RESPALDO_RESTAURAR_HASTA

compose() {
    if [ -n "$proyecto" ]; then
        docker compose -p "$proyecto" --profile simulacro "$@"
    else
        docker compose --profile simulacro "$@"
    fi
}

ahora() {
    date +%s
}

decir() {
    printf 'simulacro: %s\n' "$*"
}

registro=$(mktemp)
limpiar() {
    compose rm -sfv restauracion >/dev/null 2>&1 || true
    rm -f "$registro"
}
trap limpiar EXIT
trap 'exit 1' INT TERM

# --- RPO: cuanto tarda un cambio en llegar al repositorio -------------------------------------------------------------

# El segmento de WAL donde escribe la base ahora: lo confirmado hasta este momento esta en el repositorio cuando ese
# segmento llega. Si nada se escribio desde el ultimo cambio de segmento, ya esta y no hay nada que perder.
rpo=-
if [ "$origen" = si ]; then
    # El usuario y la base, los del contenedor: las variables se expanden alla, no aqui
    origen_sql() {
        # shellcheck disable=SC2016
        compose exec -T db sh -c 'psql -XAtq -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"' sh "$1"
    }
    inicio=$(ahora)
    segmento=$(origen_sql "select pg_walfile_name(pg_current_wal_lsn())")
    decir "Esperando a que el segmento $segmento llegue al repositorio."
    until [ "$(origen_sql "select coalesce(last_archived_wal >= '$segmento', false) from pg_stat_archiver")" = t ]; do
        if [ $(($(ahora) - inicio)) -gt 900 ]; then
            decir "El segmento $segmento no llego al repositorio en 15 minutos: el archivo de WAL no funciona."
            exit 1
        fi
        sleep 2
    done
    rpo=$(($(ahora) - inicio))
fi

# --- Restaurar ----------------------------------------------------------------------------------------------------------

case "$hasta" in
    "") destino="el ultimo respaldo, hasta su punto de restauracion" ;;
    ultimo) destino="el ultimo respaldo y el WAL, hasta el ultimo segmento del repositorio" ;;
    *) destino="el respaldo y el WAL, hasta $hasta" ;;
esac
compose rm -sfv restauracion >/dev/null 2>&1 || true
decir "Restaurando $destino."
inicio=$(ahora)
if ! compose up -d --wait --wait-timeout 3600 restauracion; then
    compose logs --tail 60 restauracion
    decir "La restauracion fallo."
    exit 1
fi
restaurada=$(ahora)
restaurado=$(compose exec -T restauracion cat /tmp/restauracion)

fallos=0
if compose exec -T restauracion respaldo comparar; then
    # Con un destino distinto del punto del respaldo, la base tiene lo que paso despues de contarla
    filas=${hasta:+"no se comparan, se restauro mas alla del conteo"}
    filas=${filas:-iguales a las que conto el respaldo}
else
    filas="distintas de las que conto el respaldo"
    fallos=$((fallos + 1))
fi
comparada=$(ahora)

# --- Verificar con la API -------------------------------------------------------------------------------------------

if compose run --rm --no-deps verificacion >"$registro" 2>&1; then
    verificacion=correcta
else
    verificacion=fallida
    fallos=$((fallos + 1))
fi
fin=$(ahora)
grep -o 'Base restaurada[^"]*' "$registro" || {
    decir "La API no llego a verificar la base; lo ultimo que escribio:"
    tail -n 40 "$registro"
}

cat <<FIN

Simulacro de restauracion
  Restaurado:    $destino (respaldo ${restaurado#* })
  RPO medido:    ${rpo} s, lo que tardo el ultimo cambio en llegar al repositorio
  Restaurar:     $((restaurada - inicio)) s
  Filas:         $filas ($((comparada - restaurada)) s)
  Verificacion:  $verificacion ($((fin - comparada)) s)
  RTO medido:    $((fin - inicio)) s, desde que empezo a restaurar hasta la ultima comprobacion
FIN

if [ "$fallos" -gt 0 ]; then
    decir "$fallos comprobaciones fallaron: la copia no es confiable hasta saber por que (docs/runbook.md)."
    exit 1
fi
decir "Cada comprobacion paso."
