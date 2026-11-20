# v154 — Víboras reales, Ping Pong arreglado, FPS, Fútbol, Carrera, Trivias y micrófono

## Plataforma
- El botón del micrófono ahora está en la fila de arriba, a la izquierda de «Cambiar de sala» (GuestScreens.kt).

## Viboritas Neón (rehecho)
- Movimiento continuo: la víbora gira con velocidad limitada (curvas suaves) y el cuerpo sigue el camino de la cabeza.
- Controles nuevos y grandes: joystick flotante (apoyas el dedo en cualquier parte y arrastras), botón TURBO (100 dp) y DISPARO (92 dp) en el shooter.
- Cámara que sigue a tu víbora, arenas más grandes, tubo con escamas y bandas, ojos con pupila, lengua bífida, resplandor de turbo, minuto de zona roja en Supervivencia.
- Bots con sensores (esquivan paredes y cuerpos). Portales sin costuras en «Sin paredes».
- Red: instantáneas compactas (cabeza + desplazamientos) a 15 por segundo; los invitados envían ángulo, turbo y disparo.

## Ping Pong
- Causa del error: el saque siempre chocaba con la red. Ahora el saque y cada golpe se CALCULAN con la misma física del juego (bote en su mitad, supera la red con margen, cae en la mitad rival).
- La pala golpea dentro de una ventana (no en un plano exacto) y las personas tienen más margen.
- IA con errores por nivel (apunta fuera) en lugar de fallos absurdos.
- Visual: franja de luz sobre la mesa, líneas reglamentarias, postes de red.

## Trivias
- Si tu respuesta queda en revisión manual, NO la apruebas tú: la decide otro jugador (voto que aplica el anfitrión). Solo en práctica de un jugador puede decidir él.

## Arena FPS
- Correr (joystick al máximo): más velocidad, FOV mayor y arma baja.
- Asistencia de puntería suave al apuntar a un enemigo.
- Efectos: brillo del fogonazo, estelas con halo, chispas/sangre en el impacto, casquillos y humo, indicador de daño direccional, ladrillos y luces de techo.

## Fútbol
- Cámara de transmisión lateral (como FIFA/PES): cancha en horizontal, tu equipo siempre ataca hacia la derecha.
- Joystick adaptado: derecha = hacia el arco rival, arriba = lado lejano.

## Carrera
- Viñeta de velocidad (efecto túnel) y estelas de neón laterales; destello al activar el nitro.

> Nota: esta versión se editó sin poder compilar en el entorno; conviene compilar y probar en el teléfono.
