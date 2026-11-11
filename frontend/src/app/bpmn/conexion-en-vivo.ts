import { EventoEnVivo } from '../models/en-vivo.model';

/** Lo que hace falta de un EventSource: asi se prueba sin navegador. */
export interface FuenteDeEventos {
  onopen: (() => void) | null;
  onerror: (() => void) | null;
  addEventListener(tipo: string, oyente: (evento: MessageEvent<string>) => void): void;
  close(): void;
}

export interface PuertosDeConexion {
  abrir(): FuenteDeEventos;
  esperar(accion: () => void, ms: number): unknown;
  cancelar(marca: unknown): void;
  azar(): number;
}

const TIPOS = ['presencia', 'bloqueo', 'control-tomado', 'guardado', 'publicado'] as const;
export const ESPERA_INICIAL_MS = 1000;
export const ESPERA_MAXIMA_MS = 30000;

/**
 * Cuanto esperar antes de volver a conectar: el doble cada vez, hasta un tope, y solo hasta ahi al azar desde la mitad,
 * para que un servidor que vuelve no reciba a todos en el mismo instante.
 */
export function esperaDeReconexion(intentos: number, azar: () => number): number {
  const tope = Math.min(ESPERA_MAXIMA_MS, ESPERA_INICIAL_MS * 2 ** intentos);
  return Math.round(tope / 2 + (azar() * tope) / 2);
}

/**
 * Una conexion en vivo que se repone sola. Se cierra la del navegador y se abre otra aqui: la reconexion propia de
 * EventSource reintenta a ritmo fijo y se rinde ante cualquier respuesta que no sea un corte.
 */
export class ConexionEnVivo {
  private fuente: FuenteDeEventos | null = null;
  private marca: unknown = null;
  private intentos = 0;
  private detenida = false;

  constructor(
    private readonly puertos: PuertosDeConexion,
    private readonly avisar: (evento: EventoEnVivo) => void,
  ) {}

  iniciar(): void {
    this.detenida = false;
    this.conectar();
  }

  detener(): void {
    this.detenida = true;
    if (this.marca !== null) {
      this.puertos.cancelar(this.marca);
      this.marca = null;
    }
    this.fuente?.close();
    this.fuente = null;
  }

  private conectar(): void {
    const fuente = this.puertos.abrir();
    this.fuente = fuente;
    fuente.onopen = () => {
      this.intentos = 0;
      this.avisar({ tipo: 'conectado' });
    };
    fuente.onerror = () => this.cortada(fuente);
    TIPOS.forEach((tipo) =>
      fuente.addEventListener(tipo, (evento) => this.avisar({ tipo, datos: JSON.parse(evento.data) } as EventoEnVivo)),
    );
  }

  private cortada(fuente: FuenteDeEventos): void {
    fuente.close();
    if (this.detenida || fuente !== this.fuente) {
      return;
    }
    this.avisar({ tipo: 'desconectado' });
    const espera = esperaDeReconexion(this.intentos, () => this.puertos.azar());
    this.intentos++;
    this.marca = this.puertos.esperar(() => {
      this.marca = null;
      this.conectar();
    }, espera);
  }
}
