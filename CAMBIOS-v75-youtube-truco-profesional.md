# SINI-JOSS v75 — sincronización de YouTube y Truco argentino

Base de trabajo: `SINI-JOSS-v74-ajedrez-piezas-transparentes.zip`. Los cambios son acumulativos; no se recuperaron archivos de versiones anteriores.

## YouTube compartido
- El sondeo de posición pasa de 500 ms a 1000 ms y se limita a actualizar información de posición/estado.
- La sincronización de seek/play/pause se ejecuta cuando cambia la secuencia de una acción compartida, evitando mandar correcciones repetidas que traben o corten el audio.
- Los cambios de play/pausa hechos con los controles nativos del reproductor por el anfitrión se publican una sola vez al detectar el cambio real.
- Se conserva el controlador y WebView compartidos existentes para mantener la reproducción al navegar entre pantallas.

## Truco argentino
- Las cartas jugadas permanecen visibles sobre la mesa; al completarse la baza, se mantienen durante 8 segundos para mostrar el resultado antes de limpiar.
- Durante la pausa visual se bloquea la jugada siguiente y la acción automática del bot, evitando que se mezclen bazas.
- Las cartas de la mesa son más grandes y entran con una animación suave de desplazamiento y aparición.
- Se agrega un sonido de carta sobre mesa WAV a 44,1 kHz, con golpe suave y textura de papel, disparado al agregar una carta.

## Verificación
- ZIP de origen validado antes de editar.
- Se comprobó integridad del ZIP de entrega y existencia de los 32 PNG transparentes conservados desde la base.
- No se pudo ejecutar compilación Android en este entorno: el proyecto no contiene `gradlew` ni hay Gradle instalado. Compilar y probar en Android Studio antes de publicar.
