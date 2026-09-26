// Prueba de carga: un pico de ventas en el que mucha gente consulta procesos publicados y unos pocos los editan.
// Se corre contra una instancia levantada (por ejemplo la del compose):
//   docker run --rm -i --network host -e BASE_URL=http://localhost:8080 grafana/k6 run - < k6/pico-de-ventas.js
import http from 'k6/http';
import { check, group } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const CLAVE = 'carga12345';

export const options = {
  // El pico: sube a 20 usuarios a la vez, se sostiene y baja.
  stages: [
    { duration: '10s', target: 10 },
    { duration: '20s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    // Si una de estas se rompe, k6 termina con error y el paso del CI falla.
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
    'http_req_duration{operacion:diagrama}': ['p(95)<1500'],
    checks: ['rate>0.99'],
  },
};

const json = (token) => ({
  headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
});

// Una tienda propia de la corrida, con un proceso publicado y su modelo: lo que los usuarios van a leer.
export function setup() {
  const sufijo = `${Date.now()}`.slice(-9);
  const correo = `carga${sufijo}@k6.test`;
  const registro = http.post(`${BASE}/api/v1/empresas`, JSON.stringify({
    nombreEmpresa: `Tienda de carga ${sufijo}`,
    nit: `9${sufijo}-1`,
    correoContacto: correo,
    nombreAdmin: 'Administradora',
    emailAdmin: correo,
    passwordAdmin: CLAVE,
  }), json());
  check(registro, { 'la tienda queda registrada': (r) => r.status === 201 });

  const login = http.post(`${BASE}/api/v1/auth/login`,
    JSON.stringify({ email: correo, password: CLAVE }), json());
  check(login, { 'el administrador entra': (r) => r.status === 200 });
  const token = login.json('accessToken');

  const proceso = http.post(`${BASE}/api/v1/procesos`, JSON.stringify({
    nombre: 'Order fulfillment',
    descripcion: 'De la compra a la entrega',
    categoria: 'Fulfillment',
  }), json(token));
  check(proceso, { 'el proceso queda creado': (r) => r.status === 201 });
  const procesoId = proceso.json('id');

  const pools = http.get(`${BASE}/api/v1/procesos/${procesoId}/pools`, json(token));
  const tienda = pools.json('0.id');
  const rol = http.post(`${BASE}/api/v1/roles`,
    JSON.stringify({ nombre: 'Warehouse', descripcion: 'Almacen' }), json(token));
  const lane = http.post(`${BASE}/api/v1/pools/${tienda}/lanes`,
    JSON.stringify({ nombre: 'Picking', rolProcesoId: rol.json('id') }), json(token)).json('id');

  // Un diagrama que se pueda publicar: inicio, la actividad y un fin, unidos. Hasta ahora esto era solo la
  // actividad suelta, el diagnostico lo rechazaba con E-02 y el check de abajo fallaba en cada corrida sin que
  // nadie lo viera, porque un check fallido entre miles deja el ratio por encima del umbral.
  const inicio = http.post(`${BASE}/api/v1/lanes/${lane}/eventos`,
    JSON.stringify({ nombre: 'Order received', tipoEvento: 'INICIO', posicionX: 10, posicionY: 20 }),
    json(token)).json('id');
  const recoger = http.post(`${BASE}/api/v1/lanes/${lane}/actividades`,
    JSON.stringify({ nombre: 'Pick items', descripcion: 'Recoger', posicionX: 160, posicionY: 20 }),
    json(token)).json('id');
  const fin = http.post(`${BASE}/api/v1/lanes/${lane}/eventos`,
    JSON.stringify({ nombre: 'Order picked', tipoEvento: 'FIN', posicionX: 320, posicionY: 20 }),
    json(token)).json('id');
  http.post(`${BASE}/api/v1/arcos`, JSON.stringify({ origenId: inicio, destinoId: recoger }), json(token));
  http.post(`${BASE}/api/v1/arcos`, JSON.stringify({ origenId: recoger, destinoId: fin }), json(token));

  const publicado = http.patch(`${BASE}/api/v1/procesos/${procesoId}`,
    JSON.stringify({ estado: 'PUBLICADO', version: proceso.json('version') }), json(token));
  check(publicado, { 'el proceso queda publicado': (r) => r.status === 200 });

  return { token, procesoId };
}

// Lo que hace cada usuario del pico: mirar sus procesos y abrir el diagrama de uno publicado.
export default function (datos) {
  const cabeceras = json(datos.token);

  group('consultar', () => {
    const listado = http.get(`${BASE}/api/v1/procesos?pagina=0&tamano=20`,
      { ...cabeceras, tags: { operacion: 'listado' } });
    check(listado, { 'el listado responde 200': (r) => r.status === 200 });

    const diagrama = http.get(`${BASE}/api/v1/procesos/${datos.procesoId}/diagrama`,
      { ...cabeceras, tags: { operacion: 'diagrama' } });
    check(diagrama, {
      'el diagrama responde 200': (r) => r.status === 200,
      'el diagrama trae sus pools': (r) => r.json('pools').length > 0,
    });
  });
}
