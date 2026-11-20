# v152 — Arena persistente, Viboritas Neón y Ping Pong 3D Pro

## Células Arena: mundo persistente
- «ENTRAR A LA ARENA»: un mundo que no se detiene. Entras y sales cuando quieras y los demás de la sala se unen en cualquier momento.
- Un jugador cualquiera hace de servidor. El candado `games/agarLock` se decide con una transacción de Firebase (gana el primero que llega); el servidor late cada segundo. Si se va o se cae, otro jugador reclama el candado y continúa **con el mundo tal cual estaba** (células, comida, virus y bots) desde la última instantánea. Si nadie queda, el mundo queda guardado y el siguiente que entre lo retoma.
- Los jugadores que dejan de dar señales (8–10 s) o pulsan salir desaparecen de la arena.
- Se mantienen la práctica local (solo / con bots) y el modo espectador.

## Viboritas Neón (`snake/`)
7 modos: Clásico (se acelera), Sin paredes (portales), Todos contra todos, Supervivencia (zona que se achica), Equipos (rojo vs azul), Shooter (munición, disparos: al cuerpo corta la víbora, a la cabeza la elimina) y Carrera de manzanas (15). Bots con IA, multijugador en línea (el anfitrión simula, los demás giran/disparan), cruceta + deslizar el dedo, estética neón con efectos (partículas, sacudida, estela de balas).

## Ping Pong 3D Pro (`pong/`)
Mesa reglamentaria (2,74 × 1,525 m, red 15,25 cm) con perspectiva 3D, física completa (gravedad, resistencia del aire, efecto Magnus: topspin, backspin y lateral; rebote con fricción), saque y puntuación oficiales (a 7/11/21, diferencia de 2, saque alterno), IA de 4 niveles y duelo en línea (el anfitrión reta a un jugador; los demás pueden mirar). Pala con el dedo: la velocidad del movimiento da fuerza y el movimiento hacia arriba da topspin. Efectos: estela, brillo, ondas de bote, chispas, sacudida, confeti, pelota de partido.

## Cambios de integración
`MinigamesScreen.kt` (registro de snake y pong), `FirestoreCoupleRepository.kt` + `CoupleViewModel.kt` (`gameTransact`, transacción genérica con `createIfMissing`; `triviaTransact` sigue funcionando).

## Pruebas
`AgarEngineTest`, `SnakeEngineTest`, `PongEngineTest`. No se compiló ni se probó en dispositivos en este entorno.
