// Prueba de carga: un pico de pedidos que entran por mensaje, se atienden desde la bandeja y los mueve el reloj.
// El otro guion (pico-de-ventas.js) carga la lectura del modelo; este carga la ejecucion, que es lo que toca la
// base de verdad: abre casos, escribe bandejas, bloquea filas.
//   docker run --rm -i --network host -e BASE_URL=http://localhost:8080 grafana/k6 run - < k6/pico-de-pedidos.js
// PASOS alarga la cadena del proceso con tantas tareas mas: sirve para medir lo que cuesta un diagrama grande
// -armar su grafo es una busqueda en anchura por nodo- con la operacion sin cambiar. Por defecto, cero.
import http from 'k6/http';
import { check, group } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const CLAVE = 'carga12345';
const PASOS = Number(__ENV.PASOS || 0);

export const options = {
  // El pico: diez personas atendiendo pedidos a la vez, sostenido y bajando.
  stages: [
    { duration: '10s', target: 5 },
    { duration: '20s', target: 10 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    // Si una de estas se rompe, k6 termina con error y el paso del CI falla.
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
    // Mandar un mensaje abre o mueve un caso con su fila bloqueada: es la operacion que mas cuesta.
    'http_req_duration{operacion:mensaje}': ['p(95)<2000'],
    'http_req_duration{operacion:tablero}': ['p(95)<1500'],
    checks: ['rate>0.99'],
  },
};

const json = (token) => ({
  headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
});

// Una tienda propia de la corrida, con el proceso mas corto que se puede ejecutar: entra un pedido por mensaje,
// ventas lo revisa y termina. Lo demas del motor ya lo cubren las pruebas; aqui lo que se mide es el peso.
export function setup() {
  const sufijo = `${Date.now()}`.slice(-9);
  const correo = `pedidos${sufijo}@k6.test`;
  const registro = http.post(`${BASE}/api/v1/empresas`, JSON.stringify({
    nombreEmpresa: `Tienda de pedidos ${sufijo}`,
    nit: `8${sufijo}-1`,
    correoContacto: correo,
    nombreAdmin: 'Administradora',
    emailAdmin: correo,
    passwordAdmin: CLAVE,
  }), json());
  check(registro, { 'la tienda queda registrada': (r) => r.status === 201 });

  const login = http.post(`${BASE}/api/v1/auth/login`,
    JSON.stringify({ email: correo, password: CLAVE }), json());
  check(login, { 'la administradora entra': (r) => r.status === 200 });
  const token = login.json('accessToken');

  const proceso = http.post(`${BASE}/api/v1/procesos`, JSON.stringify({
    nombre: 'Order fulfillment',
    descripcion: 'De la compra a la entrega',
    categoria: 'Fulfillment',
  }), json(token));
  check(proceso, { 'el proceso queda creado': (r) => r.status === 201 });
  const procesoId = proceso.json('id');

  const tienda = http.get(`${BASE}/api/v1/procesos/${procesoId}/pools`, json(token)).json('0.id');
  const cliente = http.post(`${BASE}/api/v1/procesos/${procesoId}/pools`, JSON.stringify({
    nombre: 'Customer', tipoParticipante: 'CLIENTE', cajaNegra: true, integracion: 'CLIENTE',
  }), json(token)).json('id');
  const rol = http.post(`${BASE}/api/v1/roles`,
    JSON.stringify({ nombre: 'Sales', descripcion: 'Ventas' }), json(token)).json('id');
  const lane = http.post(`${BASE}/api/v1/pools/${tienda}/lanes`,
    JSON.stringify({ nombre: 'Sales', rolProcesoId: rol }), json(token)).json('id');

  const inicio = http.post(`${BASE}/api/v1/lanes/${lane}/eventos`, JSON.stringify({
    nombre: 'Order received', tipoEvento: 'MENSAJE_INICIO', posicionX: 20, posicionY: 80,
  }), json(token)).json('id');
  const revisar = http.post(`${BASE}/api/v1/lanes/${lane}/actividades`, JSON.stringify({
    nombre: 'Receive order', descripcion: 'Revisar el pedido', tipoActividad: 'USUARIO',
    posicionX: 160, posicionY: 80,
  }), json(token)).json('id');
  // La cadena de pasos de mas, si se pidio: el pedido pasa por todas, una por iteracion.
  let ultimo = revisar;
  for (let paso = 1; paso <= PASOS; paso++) {
    const siguiente = http.post(`${BASE}/api/v1/lanes/${lane}/actividades`, JSON.stringify({
      nombre: `Step ${paso}`, descripcion: 'Un paso mas de la cadena', tipoActividad: 'USUARIO',
      posicionX: 320 + paso * 160, posicionY: 80,
    }), json(token)).json('id');
    http.post(`${BASE}/api/v1/arcos`, JSON.stringify({ origenId: ultimo, destinoId: siguiente }), json(token));
    ultimo = siguiente;
  }
  const fin = http.post(`${BASE}/api/v1/lanes/${lane}/eventos`, JSON.stringify({
    nombre: 'Order handled', tipoEvento: 'FIN', posicionX: 480 + PASOS * 160, posicionY: 80,
  }), json(token)).json('id');
  http.post(`${BASE}/api/v1/arcos`, JSON.stringify({ origenId: inicio, destinoId: revisar }), json(token));
  http.post(`${BASE}/api/v1/arcos`, JSON.stringify({ origenId: ultimo, destinoId: fin }), json(token));

  const mensaje = http.post(`${BASE}/api/v1/procesos/${procesoId}/mensajes`, JSON.stringify({
    nombre: 'Order placed', contenido: 'Lo que el cliente compro', poolOrigenId: cliente, poolDestinoId: tienda,
    nodoDestinoId: inicio, origenExterno: true, variable: 'order',
    campos: [{ nombre: 'orderId', tipo: 'TEXTO' }],
  }), json(token));
  check(mensaje, { 'el mensaje de inicio queda creado': (r) => r.status === 201 });
  http.put(`${BASE}/api/v1/mensajes/${mensaje.json('id')}/correlacion`, JSON.stringify({
    criterio: 'orderId', campo: 'orderId', sinCaso: 'INICIAR_CASO',
  }), json(token));

  const publicado = http.patch(`${BASE}/api/v1/procesos/${procesoId}`,
    JSON.stringify({ estado: 'PUBLICADO', version: proceso.json('version') }), json(token));
  check(publicado, { 'el proceso queda publicado': (r) => r.status === 200 });

  return { token, procesoId, rol };
}

// Lo que hace cada persona del pico: manda un pedido, mira su bandeja, completa lo que encuentre y mira el tablero.
export default function (datos) {
  const cabeceras = json(datos.token);
  const referencia = `K6-${__VU}-${__ITER}`;

  group('operar', () => {
    const entrante = http.post(`${BASE}/api/v1/procesos/${datos.procesoId}/mensajes-entrantes`, JSON.stringify({
      nombre: 'Order placed',
      cuerpo: { orderId: referencia },
      claveExterna: referencia,
    }), { ...cabeceras, tags: { operacion: 'mensaje' } });
    check(entrante, {
      'el pedido entra': (r) => r.status === 200,
      'el pedido abre un caso': (r) => r.json('resultado') === 'CASO_NUEVO',
    });

    const bandeja = http.get(
      `${BASE}/api/v1/tareas?rolProcesoId=${datos.rol}&pagina=0&tamano=5`,
      { ...cabeceras, tags: { operacion: 'bandeja' } });
    check(bandeja, { 'la bandeja responde 200': (r) => r.status === 200 });

    const tareas = bandeja.json('content') || [];
    if (tareas.length > 0) {
      // Puede que otra persona del pico la haya completado entre la consulta y esto: un 409 no es un fallo,
      // es exactamente lo que la regla de una tarea por persona tiene que responder. Hay que decirselo tambien a
      // http_req_failed, que por su cuenta cuenta como fallo cualquier respuesta de 400 para arriba: con diez
      // personas peleando por la primera tarea de la bandeja, esos 409 son la mayoria de las respuestas.
      const completar = http.post(`${BASE}/api/v1/tareas/${tareas[0].id}/completar`,
        JSON.stringify({}), {
          ...cabeceras,
          tags: { operacion: 'completar' },
          responseCallback: http.expectedStatuses(200, 409),
        });
      check(completar, { 'completar responde 200 o 409': (r) => r.status === 200 || r.status === 409 });
    }

    const tablero = http.get(`${BASE}/api/v1/procesos/${datos.procesoId}/tablero`,
      { ...cabeceras, tags: { operacion: 'tablero' } });
    check(tablero, {
      'el tablero responde 200': (r) => r.status === 200,
      'el tablero cuenta pedidos': (r) => r.json('casos') > 0,
    });
  });
}

// Al final, mover el reloj una vez: entrega lo que quedara pendiente y deja la tienda en un estado que se
// puede mirar, que es como termina una demo.
export function teardown(datos) {
  const tick = http.post(`${BASE}/api/v1/simulacion/tick`, JSON.stringify({ ticks: 1 }), json(datos.token));
  check(tick, { 'el reloj se mueve al final': (r) => r.status === 200 });
}
