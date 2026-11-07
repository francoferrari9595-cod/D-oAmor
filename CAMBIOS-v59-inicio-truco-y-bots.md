# v59 — Inicio único de Truco y autoridad de bots

Base: SINI-JOSS v58.

## Cambios
- El botón compartido «Comenzar partida» ahora inicia también el reparto de la primera mano de Truco desde el anfitrión; ya no debería requerir un segundo toque en «Repartir cartas».
- Las decisiones automáticas de los bots quedan restringidas al anfitrión determinista (`hostUid`) en lugar de depender del primer UID de la lista local, que podría variar entre dispositivos.
- El número de bots de la sala sigue siendo el valor recibido por el contexto compartido.

## Validación
- Revisión estática de las llamadas a `GameCtx` y del efecto de inicio.
- ZIP verificado con `unzip -t`.
- Falta probar sincronización real en dos dispositivos y compilar con Gradle/Android SDK.
