import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import {
  EnlaceDeNotificacion,
  Notificacion,
  enlaceDeNotificacion,
  textoDeNotificacion,
} from '../../models/notificacion.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { CampanaService } from '../../service/campana.service';

/**
 * PR F21: la campana del encabezado. El numero son los avisos sin leer, que llegan en vivo por SSE; al abrirla se
 * piden los ultimos. Es un boton que abre un panel (no un menu): adentro hay enlaces y botones, y se recorre con
 * Tab. Escape o un clic afuera lo cierran y el foco vuelve a la campana. Un aviso nuevo se anuncia en una region
 * aria-live aunque el panel este cerrado.
 */
@Component({
  selector: 'app-campana',
  imports: [RouterLink, FechaPipe],
  templateUrl: './campana.component.html',
  styleUrl: './campana.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:click)': 'clicFuera($event)', '(keydown.escape)': 'cerrar(true)' },
})
export class CampanaComponent implements OnInit, OnDestroy {
  readonly campana: CampanaService = inject(CampanaService);
  private readonly anfitrion: ElementRef<HTMLElement> = inject(ElementRef);

  readonly texto: (aviso: Notificacion) => string = textoDeNotificacion;
  readonly enlace: (aviso: Notificacion) => EnlaceDeNotificacion = enlaceDeNotificacion;
  abierta: boolean = false;

  /** Lo que dice el boton a un lector de pantalla, con el numero: el numero pintado no se lee. */
  readonly etiqueta = computed((): string => {
    const cuantas = this.campana.noLeidas();
    return cuantas === 0 ? $localize`Notifications, none unread` : $localize`Notifications, ${cuantas}:cuantas: unread`;
  });

  /** Mas de 99 no cabe en la insignia. */
  readonly numero = computed((): string => {
    const cuantas = this.campana.noLeidas();
    return cuantas > 99 ? '99+' : String(cuantas);
  });

  ngOnInit(): void {
    this.campana.iniciar();
  }

  ngOnDestroy(): void {
    this.campana.detener();
  }

  alternar(): void {
    if (this.abierta) {
      this.cerrar(false);
      return;
    }
    this.abierta = true;
    this.campana.cargar();
  }

  /** Cierra el panel; con Escape el foco vuelve a la campana, que es de donde se abrio. */
  cerrar(devolverFoco: boolean): void {
    if (!this.abierta) {
      return;
    }
    this.abierta = false;
    if (devolverFoco) {
      this.anfitrion.nativeElement.querySelector<HTMLElement>('#navbar-campana')?.focus();
    }
  }

  clicFuera(evento: MouseEvent): void {
    if (this.abierta && !this.anfitrion.nativeElement.contains(evento.target as Node)) {
      this.cerrar(false);
    }
  }

  /** Abrir un aviso lo da por leido y cierra el panel; el enlace lleva a lo que senala. */
  abrir(aviso: Notificacion): void {
    this.campana.marcarLeida(aviso);
    this.abierta = false;
  }
}
