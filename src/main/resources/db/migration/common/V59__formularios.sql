-- PR 69: el formulario tipado de una actividad de usuario o de un evento de inicio: sus campos, con sus tipos, sus
-- validaciones y sus condiciones, en JSON. Al publicar se congela con el resto del diagrama en la definicion de la
-- version; lo que se contesta al completar una tarea va a las variables del caso, que ya son JSON y ya guardan cada
-- valor con su tipo.
alter table nodos_flujo add column formulario text;

-- Solo una persona llena un formulario: el de una tarea de usuario, o el de un inicio que se abre a mano. El servicio
-- no deja ponerlo en otro nodo, ni cambiarle el tipo a uno que lo tiene; esto lo sostiene si algo se le escapa.
alter table nodos_flujo add constraint ck_nodos_flujo_formulario
    check (formulario is null
        or tipo_nodo = 'ACTIVIDAD' and tipo_actividad = 'USUARIO'
        or tipo_nodo = 'EVENTO' and tipo_evento = 'INICIO');
