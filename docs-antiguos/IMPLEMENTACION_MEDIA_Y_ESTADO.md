# Estado de implementación — D-oAmor

## Incluido en esta versión de trabajo
- Envío de imágenes seleccionadas desde la galería a Firebase Storage y publicación del mensaje con URL en Firestore.
- Grabación de nota de voz con `MediaRecorder`, subida a Storage y reproducción con `MediaPlayer`.
- Escucha de mensajes Firestore reactiva al cambio del código de pareja.
- Calendario: escritura y escucha de eventos en `couples/{coupleId}/calendarEvents`.
- Reglas de Firestore para mensajes, eventos, estados de juegos y señalización de llamadas, restringidas a miembros de la pareja.
- Corrección de la declaración duplicada de `onCleared()` y liberación de recursos de audio.
- La interfaz ya no declara conectada una llamada ficticia; el estado queda en intento hasta que se integre el transporte de audio real.

## Requisitos externos obligatorios
1. Habilitar Firebase Authentication (usuarios autenticados), Cloud Firestore y Firebase Storage en el proyecto correcto.
2. Publicar `firestore.rules` y `storage.rules` desde Firebase Console o Firebase CLI.
3. Probar envío de foto/audio entre dos instalaciones, con usuarios distintos que sean miembros del mismo documento de pareja.
4. Las llamadas de voz/video no están completas: requieren motor WebRTC integrado y probado, ICE servers/STUN/TURN y señalización bidireccional. La UI no debe anunciar conexión mientras no exista `PeerConnection` en estado conectado.
5. Notificaciones con la aplicación cerrada requieren FCM y un servicio/backend o Cloud Functions para avisar al dispositivo receptor. Firestore por sí solo no garantiza que Android despierte la aplicación en segundo plano.

## Validación
El entorno de edición no incluye `gradlew`, `gradle-wrapper.jar` ni una instalación local de Gradle; por tanto no se afirma que el APK compile. Ejecutar `./gradlew assembleDebug` en Android Studio/CI después de agregar el wrapper oficial de Gradle.
