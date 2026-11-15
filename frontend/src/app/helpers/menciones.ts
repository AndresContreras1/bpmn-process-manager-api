import { MENCIONES_MAXIMAS, Persona } from '../models/comentario.model';

/**
 * PR F21: lo que sabe el selector de menciones, sin pantalla: las personas elegidas, lo que ofrece la API para el
 * texto escrito y cual de esas opciones esta activa. La pantalla solo lo pinta y le pasa las teclas.
 *
 * La API dice a quien se puede mencionar (personas activas que ven el proceso, sin quien pregunta); este estado no
 * vuelve a ofrecer a quien ya se eligio, ni deja pasar del tope de veinte, que la API responderia con un 400.
 */
export class SelectorDeMenciones {
  private elegidasAhora: Persona[] = [];
  private opcionesAhora: Persona[] = [];
  private activaAhora = -1;

  constructor(private readonly tope: number = MENCIONES_MAXIMAS) {}

  get elegidas(): readonly Persona[] {
    return this.elegidasAhora;
  }

  get opciones(): readonly Persona[] {
    return this.opcionesAhora;
  }

  /** La opcion activa, -1 si ninguna: la que elige Enter y la que anuncia aria-activedescendant. */
  get activa(): number {
    return this.activaAhora;
  }

  get abierto(): boolean {
    return this.opcionesAhora.length > 0;
  }

  get lleno(): boolean {
    return this.elegidasAhora.length >= this.tope;
  }

  /** Los ids que viajan a la API, en el orden en que se eligieron. */
  get ids(): number[] {
    return this.elegidasAhora.map((persona) => persona.id);
  }

  /** Empieza con las menciones que ya tenia un comentario que se edita. */
  reiniciar(elegidas: readonly Persona[] = []): void {
    this.elegidasAhora = elegidas.slice(0, this.tope);
    this.cerrar();
  }

  /** Lo que respondio la API para el texto escrito, sin quien ya esta elegido; la primera queda activa. */
  ofrecer(resultados: readonly Persona[]): void {
    if (this.lleno) {
      this.cerrar();
      return;
    }
    const elegidos = new Set(this.ids);
    this.opcionesAhora = resultados.filter((persona) => !elegidos.has(persona.id));
    this.activaAhora = this.opcionesAhora.length ? 0 : -1;
  }

  cerrar(): void {
    this.opcionesAhora = [];
    this.activaAhora = -1;
  }

  /** Las flechas recorren las opciones y dan la vuelta; Inicio y Fin van a los extremos. */
  mover(tecla: 'ArrowDown' | 'ArrowUp' | 'Home' | 'End'): void {
    const cuantas = this.opcionesAhora.length;
    if (!cuantas) {
      return;
    }
    switch (tecla) {
      case 'ArrowDown':
        this.activaAhora = (this.activaAhora + 1) % cuantas;
        break;
      case 'ArrowUp':
        this.activaAhora = (this.activaAhora - 1 + cuantas) % cuantas;
        break;
      case 'Home':
        this.activaAhora = 0;
        break;
      case 'End':
        this.activaAhora = cuantas - 1;
        break;
    }
  }

  /** Elige la opcion activa, o la del indice dicho; devuelve la persona elegida, o null si no habia ninguna. */
  elegir(indice: number = this.activaAhora): Persona | null {
    const persona = this.opcionesAhora[indice];
    if (!persona) {
      return null;
    }
    this.elegidasAhora = [...this.elegidasAhora, persona];
    this.cerrar();
    return persona;
  }

  quitar(id: number): void {
    this.elegidasAhora = this.elegidasAhora.filter((persona) => persona.id !== id);
  }
}
