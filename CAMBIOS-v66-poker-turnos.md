# v66 — Orden de acción post-flop en póker

Base: SINI-JOSS v65.

## Corrección
En Texas Hold'em cara a cara, el botón (que también pone la ciega pequeña) actúa primero pre-flop y después del flop. El avance de calles enviaba incorrectamente el turno al rival tras cada ronda. Ahora el turno post-flop vuelve al botón, manteniendo el orden correcto para una mesa de dos jugadores.

## Validación
- Revisión estática del avance de calles y el reparto de ciegas.
- ZIP verificado con `unzip -t`.
- No se ha probado una partida en la aplicación compilada.
