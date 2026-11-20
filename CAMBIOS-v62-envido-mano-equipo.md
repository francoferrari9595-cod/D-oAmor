# v62 — Desempate de envido entre compañeros

Base: SINI-JOSS v61.

## Corrección
En partidas por equipos, si los dos compañeros obtenían el mismo mejor puntaje de envido, el código anterior elegía al primero por índice de asiento, no al que era mano. Ahora se calcula el mejor puntaje de cada equipo y, si varios compañeros lo alcanzan, se toma el primero según el orden real de mano. El empate entre equipos usa ese asiento para decidir correctamente.

## Validación
- Revisión estática del cálculo por equipo y el desempate por orden de mano.
- ZIP verificado con `unzip -t`.
- Sigue pendiente la prueba de partidas reales y compilación Android.
