// PR 32: el limite de peticiones se ve en el umbral y no antes. Registra tiendas desde la misma direccion hasta que
// la API responde 429: las que quedaban en el cubo entran todas con 201, la siguiente no entra, lleva Retry-After, y
// la de despues tampoco entra. CAPACIDAD es el limite de registros por hora de la API (LIMITE_REGISTRO_POR_HORA) y
// PREVIAS las tiendas que el CI registro antes desde la misma direccion, en la prueba del perfil prod y en el
// setup de las dos cargas.
import http from 'k6/http';
import { check, fail } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const CAPACIDAD = Number(__ENV.CAPACIDAD || 10);
const PREVIAS = Number(__ENV.PREVIAS || 0);
const CSRF = 'k6-csrf';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
  },
};

function registrar(numero) {
  const sufijo = `${Date.now()}${numero}`.slice(-9);
  const correo = `limite${sufijo}@k6.test`;
  return http.post(`${BASE}/api/v1/empresas`, JSON.stringify({
    nombreEmpresa: `Tienda del limite ${sufijo}`,
    nit: `8${sufijo}-1`,
    correoContacto: correo,
    nombreAdmin: 'Administradora',
    emailAdmin: correo,
    passwordAdmin: 'una frase larga para el limite',
  }), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': CSRF, Cookie: `XSRF-TOKEN=${CSRF}` },
    responseCallback: http.expectedStatuses(201, 429),
  });
}

export default function () {
  const quedan = CAPACIDAD - PREVIAS;
  if (quedan < 1) {
    fail(`No queda nada en el cubo: CAPACIDAD=${CAPACIDAD} y PREVIAS=${PREVIAS}`);
  }
  for (let numero = 1; numero <= quedan; numero++) {
    const registro = registrar(numero);
    check(registro, { 'antes del umbral, el registro entra': (r) => r.status === 201 });
  }
  const primeraDeMas = registrar(quedan + 1);
  check(primeraDeMas, {
    'en el umbral responde 429': (r) => r.status === 429,
    'y dice cuando volver': (r) => Number(r.headers['Retry-After']) > 0,
  });
  check(registrar(quedan + 2), { 'despues del umbral sigue en 429': (r) => r.status === 429 });
}
