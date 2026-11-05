# SINI-JOSS v52 — sala de espera sincronizada

Base exclusiva: v51. Se conservaron las modificaciones acumuladas de las versiones anteriores.

## Cambios
- La sala compartida ahora muestra una pantalla de espera antes de abrir el juego.
- El estado compartido contiene el juego, el anfitrión, si se habilitó la entrada a la partida y la cantidad configurada de bots para Truco.
- Solo el anfitrión que figura en el estado compartido puede habilitar el comienzo/entrada a la partida.
- La opción de volver a la lista limpia el estado de la sala cuando la ejecuta el anfitrión, para que el otro dispositivo no quede atrapado en una selección vieja.
- La selección compartida de bots de Truco se pasa al contexto del juego como valor inicial; el propio juego todavía permite cambiarla en su pantalla de preparación.

## Validación
- Se comprobó la integridad del ZIP de origen v51 antes de modificarlo y la del ZIP v52 después de generarlo.
- Se hizo una comprobación sintáctica preliminar con kotlinc; no se pudo completar la compilación porque este entorno no dispone de las dependencias Android/Compose del proyecto.
- No se probó con dos teléfonos ni contra Firestore real.

## Pendiente real
Esta versión no certifica el funcionamiento total de todos los juegos. La entrada a cada juego sigue dependiendo de los controles de inicio propios de ese juego. No están completadas ni verificadas todas las reglas del Truco, la economía global de fichas/apuestas, ni la continuidad de llamadas en segundo plano. Para una validación real hace falta compilar en Android/Gradle y probar dos dispositivos con reglas y credenciales Firebase válidas.
