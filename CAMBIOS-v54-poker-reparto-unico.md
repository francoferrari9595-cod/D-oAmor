# SINI-JOSS v54 — reparto único de póker

Base exclusiva: v53. Se conservan los cambios previos.

## Cambios
- El primer reparto, las nuevas mesas, la siguiente mano y el cierre de la mesa quedan limitados al anfitrión determinista del juego (primer UID de la lista de jugadores compartida).
- El segundo dispositivo ve un mensaje de espera, evitando que ambos dispositivos creen mazos diferentes y se sobrescriban mutuamente al iniciar o reiniciar una mano.
- Las acciones de apuesta durante la mano siguen habilitadas solo para quien tiene el turno.

## Validación
- Revisión estática de los cuatro puntos de entrada que generan/cambian una mano.
- No se declara compilación ni prueba de Firestore real: no están presentes `gradlew` ni `gradle-wrapper.jar`, y no hay dependencias Android/Compose instaladas en el entorno.
