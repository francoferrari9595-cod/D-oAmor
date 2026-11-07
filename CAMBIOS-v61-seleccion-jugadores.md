# v61 — Selección compartida de jugadores

Base: SINI-JOSS v60.

## Cambios
- El anfitrión puede elegir entre 1 y el máximo de humanos disponibles (hasta 4) en los juegos multijugador admitidos por la pantalla común.
- La cantidad elegida se publica en `minigameLobby` y los dispositivos construyen el mismo subconjunto de asientos, poniendo primero al anfitrión para mantener un orden determinista.
- En Truco, el control de bots se limita al número de asientos libres según la selección humana; el juego completa automáticamente una pareja cuando el número final sería impar.

## Validación
- Revisión estática del flujo de configuración compartida y construcción de `GameCtx`.
- ZIP verificado con `unzip -t`.
- No se ha validado con dispositivos reales ni compilado el proyecto en este entorno.
