# D-oAmor — integración de vídeo y señalización (v9 de trabajo)

## Cambios incorporados

- Dependencias de Firebase Messaging y WebRTC.
- Motor WebRTC con pista de audio, captura de cámara, SDP offer/answer, candidatos ICE, mute y cambio de cámara.
- Flujo de llamada saliente/entrante, aceptar/rechazar, estados, temporizador y cierre de recursos en ViewModel.
- Renderizado de vídeo local/remoto en Compose.
- Funciones Firebase para avisos de llamadas entrantes y zumbidos.
- FCM Android para llamadas y zumbidos, incluyendo un nuevo mensaje `buzz_stop` para detener el sonido remoto al soltar el botón.
- Registro del token FCM y permisos Android.
- Reglas Firestore para miembros de pareja, llamadas y candidatos ICE.

## Estado honesto

**Es una versión de desarrollo lista para iniciar una ronda de pruebas de compilación; no es un APK verificado.** La señalización y los componentes de interfaz están conectados en el código, pero no se ha podido ejecutar Gradle en este entorno y no se ha probado con dos teléfonos. No se puede asegurar compatibilidad final de APIs WebRTC, permisos en cada fabricante ni conectividad de audio/vídeo hasta compilar y probar.

## Pendiente para aprobar

1. Compilar `:app:assembleDebug` y corregir cualquier error real de Kotlin/Gradle.
2. Desplegar y verificar Cloud Functions y reglas en el proyecto Firebase correcto.
3. Probar chat, zumbido start/stop, llamadas de voz/vídeo y pantalla bloqueada con dos dispositivos.
4. Probar Wi-Fi y datos móviles. Configurar TURN autenticado si STUN no conecta.
5. Revisar errores de Logcat/Cloud Functions y corregirlos antes de declarar el proyecto terminado.

Ver `PRUEBA-INICIAL-ANTES-DE-INSTALAR.md` para instrucciones y checklist.
