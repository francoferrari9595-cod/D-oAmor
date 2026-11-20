# SINI-JOSS v56 — anfitrión consistente y all-in de póker

Base: SINI-JOSS v55. No se recuperaron archivos de versiones antiguas.

## Cambios
- `GameCtx` recibe el UID de anfitrión calculado por la sala compartida.
- Truco y póker usan el mismo UID de anfitrión que controla la sala, en lugar de inferirlo por la posición del primer jugador de cada lista. Esto evita que un invitado y un miembro puedan creer ambos que les toca iniciar/repartir.
- En póker, cuando un jugador queda all-in y las apuestas aportadas son desiguales, se devuelve la parte no igualada al jugador que la puso antes de resolver el bote. Se evita que el bote incluya una apuesta que el rival no podía cubrir.

## Validación
- Se revisaron los puntos de modificación y se comprobó la integridad del ZIP generado.
- No se declara compilación Android exitosa: el proyecto de origen no incluye `gradlew` ni `gradle-wrapper.jar`, y el entorno no tiene Gradle instalado ni acceso a la distribución remota.
- Aún se requiere probar partidas reales entre dos dispositivos y validar reglas/economía/calls del proyecto completo.
