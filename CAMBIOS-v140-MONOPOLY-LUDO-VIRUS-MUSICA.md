# v140 — Monopoly, Ludo, Virus y música de fondo (sobre v139)

## Monopoly · «Mis propiedades»
- Panel nuevo a pantalla casi completa: cabecera con degradé y saldo animado, propiedades agrupadas por color (con «COMPLETO ✔»), indicador visual de casas/hotel y candado en hipotecadas.
- Botones grandes de colores (Casa/Hotel, Vender, Hipotecar/Levantar) con efecto al tocar, vibración y aviso verde con la jugada realizada.
- Los botones deshabilitados explican por qué (grupo incompleto, construir parejo, falta dinero, etc.).
- Corrección de toques: el panel se redibuja después de cada acción y ya no hay botones cortados en un scroll horizontal. También se puede gestionar mientras se decide una compra.

## Ludo / barra de juegos
- «← Juegos · Reglas · Reiniciar · Ajustes» van en una sola fila, sin hueco sobre el tablero (el juego gana altura).

## Virus
- Medicinas experimentales (dos pastillitas) inmunizan el órgano directamente.
- Un órgano con medicina multicolor puede ser atacado por un virus de cualquier color.
- Textos de ayuda y pruebas unitarias actualizados.

## Música
- Lista de reproducción (BackgroundMusic) con musica_elevator_jazz_loop; arranca al terminar la presentación y sigue en las salas y los juegos, volumen 40 %.
- Se desactiva en Ajustes → 🎵 Música. Se pausa al salir de la app.
- Para sumar canciones: copiar el mp3 a res/raw y agregarlo a BackgroundMusic.PLAYLIST.
