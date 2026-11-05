# v146 — Música, ¡Basta!, foto de perfil y tableros

## Música (Ajustes)
- Nueva tarjeta **🎵 Música** con: interruptor, **control de volumen** (0–100 %) y botón **⏭ Siguiente canción**.
  Está en los ajustes de la sala y en los ajustes de pareja (`MusicControlsCard`).
- **Audios recibidos** (walkie-talkie y notas de voz): la música ya no se pausa, **baja con suavidad al 5 %**
  de su volumen, suena el audio y **vuelve a subir sola** al terminar (`BackgroundMusic.duckForVoice / releaseDuck`).
  Al grabar o en llamadas se sigue pausando para que el micrófono no capte la música.

## ¡Basta!
- Tiempo por turno: **10 s → 30 s**, con 1,5 s de gracia por la latencia de la red.
- **Cierre de la app corregido**: los asientos se fijan al empezar la ronda (si alguien se desconecta un instante ya no
  se desarman los índices), se sanean las listas y el árbitro está protegido contra excepciones.
- Explicación clara: tarjeta «Cómo se juega» y 3 pasos con tilde en tu turno (palabra → letra → botón central).
- Los bots ahora piensan entre 2,5 y 12 s.

## Foto de perfil
- En la pantalla de inicio (junto al nombre) cada usuario elige su foto; también se cambia en Ajustes → Tu perfil.
- Se publica como miniatura en la sala (`avatars.<uid>`) y se ve en el chat de la sala.

## Tableros sin franja vacía
- **Ludo**: el tablero ocupa todo el ancho y todo el alto libre; jugadores, estado y dado quedan compactos y pegados abajo. Sin deslizar.
- **Monopoly**: se quitó la franja vacía de abajo; el tablero crece y el panel y los botones quedan abajo.
