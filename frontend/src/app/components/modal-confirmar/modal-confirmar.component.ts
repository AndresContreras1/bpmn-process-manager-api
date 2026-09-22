import { Component, input, output } from '@angular/core';

/**
 * Modal de Bootstrap que pide confirmar una accion. Se abre con data-bs-toggle="modal" y data-bs-target="#<idModal>";
 * el boton de confirmar lo cierra y emite confirmar para que la pagina llame a la API.
 */
@Component({
  selector: 'app-modal-confirmar',
  imports: [],
  templateUrl: './modal-confirmar.component.html',
  styleUrl: './modal-confirmar.component.scss',
})
export class ModalConfirmarComponent {
  // No se llama id: un atributo id en <app-modal-confirmar> repetiria el id del modal en la pagina
  idModal = input.required<string>();
  titulo = input.required<string>();
  mensaje = input.required<string>();
  textoBoton = input.required<string>();
  peligro = input<boolean>(false);
  confirmar = output<void>();
}
