# D-oAmor — estado de integración

## Cambios de código incluidos en esta entrega
- Chat con selección de fotos y grabación/reproducción de notas de voz usando Firebase Storage.
- Eventos de calendario guardados y escuchados en Firestore por pareja.
- Estado compartido de tres en raya mediante `couples/{coupleId}/games/tictactoe`.
- Reglas Firestore añadidas para subcolecciones `games` y `calls`.
- Las llamadas ya no se marcan automáticamente como conectadas: la pantalla muestra el intento y finaliza el intento sin fingir que existe audio remoto.
- Reglas de Storage limitan acceso a miembros autenticados de la pareja según `memberUids`.

## Requisitos externos para poder validar en teléfonos
1. Publicar `firestore.rules` y `storage.rules` en el proyecto Firebase que usa `app/google-services.json`.
2. Confirmar que Authentication, Cloud Firestore y Firebase Storage estén habilitados y que los dos usuarios estén incluidos en `memberUids` del documento de pareja.
3. Compilar con Android SDK y Gradle disponibles. Este entorno no tiene `gradle` instalado ni `gradle-wrapper.jar`, por lo que no se puede afirmar que se haya compilado aquí.
4. Probar con dos teléfonos y dos cuentas diferentes. Las pruebas de red no se pueden sustituir por una inspección del ZIP.

## Funciones que no se deben considerar terminadas
- **Llamadas reales de voz/vídeo:** requieren integrar y probar un motor WebRTC, señalización de oferta/respuesta/ICE, permisos, audio route, ciclo de vida y un servidor TURN para redes restrictivas. Este ZIP todavía no incluye ese motor; la señalización aislada no equivale a una llamada real.
- **Notificaciones de zumbido cuando el proceso está detenido:** requieren validar permisos de notificación, restricciones de ejecución en segundo plano y, para entrega fiable, push (FCM).
- **Dibujos/pantalla bloqueada:** una aplicación normal no puede dibujar sobre la pantalla de bloqueo de forma universal; debe implementarse como actividad/notificación permitida y probarse por fabricante/versión de Android.
- **Todos los minijuegos y ruleta:** el tres en raya ahora tiene estado remoto; otros minijuegos y ruleta todavía no están sincronizados de forma completa.

No desplegar estas reglas sin revisar que la estructura real de documentos y `memberUids` coincide con la vinculación actual.

## Ajustes adicionales de esta revisión
- La trivia publica y escucha el índice de pregunta compartido en `couples/{coupleId}/games/trivia`.
- La ruleta publica y escucha el premio seleccionado en `couples/{coupleId}/games/wheel`; la selección del premio se refleja en ambos clientes.
- La nota del salvapantallas escucha cambios del documento de pareja y actualiza el estado local cuando llega una nota remota.
- Estos cambios requieren que ambos usuarios estén autenticados y que el documento de pareja contenga ambos UID en `memberUids`.

## No considerar terminado todavía
- La llamada de voz/vídeo aún no usa WebRTC y por eso no transmite audio/vídeo entre teléfonos.
- El salvapantallas sincroniza el contenido, pero Android no permite que una app normal dibuje libremente sobre la pantalla de bloqueo; la experiencia con el dispositivo bloqueado requiere diseño mediante notificación/actividad autorizada.
- El zumbido en primer plano tiene lógica de sincronización; la entrega fiable cuando Android detiene el proceso necesita FCM y configuración del servicio push.
- No se pudo ejecutar una compilación en este entorno: el repositorio no incluye `gradle-wrapper.jar` y aquí no hay Gradle instalado. Las pruebas entre dos dispositivos siguen pendientes.

## Avance adicional integrado (15/10/2026)

Se aplicaron estas correcciones sobre esta misma base:

- La pantalla inicial ya no considera completa una pareja si el documento contiene solo un integrante. Cuando el creador vuelve a abrir la app, intenta recuperar el código de invitación pendiente que aún no venció.
- El envío de mensajes a Firestore ya no inventa `senderId = user_1` cuando no existe sesión Firebase: devuelve un error explícito. Esto evita escribir mensajes que incumplen las reglas de seguridad y causan `PERMISSION_DENIED`.
- El zumbido remoto ya no publica un UID ficticio si la sesión no está autenticada.
- Las reglas de Storage ahora limitan subidas a integrantes de la pareja, archivos menores de 20 MiB y tipos `image/*` o `audio/*`.
- Se eliminó la declaración duplicada de reglas para `couples/{coupleId}/calls/{callId}`; se conserva el bloque que contempla la subcolección de señales.

## Límites que siguen pendientes de validación real

Este entorno no dispone de Gradle ni del `gradle-wrapper.jar`, por lo que no se pudo compilar aquí. Las reglas de Firestore/Storage deben publicarse en el proyecto Firebase y probarse con dos instalaciones reales. Las llamadas de voz/vídeo aún no cuentan con el motor WebRTC completo; no deben considerarse funcionales por la sola existencia de la pantalla o de documentos de señalización. La entrega fiable de zumbidos cuando Android detiene el proceso requiere integrar y probar FCM/notificaciones. La vista de salvapantallas dentro de la app no equivale a mostrar contenido sobre la pantalla de bloqueo del sistema, que tiene restricciones de Android.
