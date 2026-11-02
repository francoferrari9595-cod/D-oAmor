# D-oAmor — paquete para iniciar pruebas (v9)

Este ZIP contiene el **proyecto fuente**, no un APK ya compilado. No se afirma que compile ni que WebRTC funcione hasta completar las pruebas indicadas abajo.

## Antes de probar

1. Descomprime el ZIP en una carpeta local.
2. Abre la carpeta raíz `D-oAmor-main` con Android Studio actualizado.
3. Comprueba que `app/google-services.json` pertenece a Firebase `d-oamor-24510` y a la aplicación `com.aistudio.duoamor.wkqz`.
4. Espera a que termine la sincronización Gradle. Si marca un error, guarda el primer error completo y no continúes instalando.
5. Ejecuta `:app:assembleDebug` desde la ventana Gradle. El APK esperado es `app/build/outputs/apk/debug/app-debug.apk`.
6. Instala el APK en dos teléfonos Android. En ambos, acepta notificaciones y permisos de micrófono/cámara.
7. Vincula los dos teléfonos como una misma pareja usando el código de invitación dentro de la app.

## Pruebas de primera ronda

- [ ] Ambos teléfonos quedan vinculados y siguen vinculados al cerrar/reabrir.
- [ ] Chat de texto en ambos sentidos.
- [ ] Envío y reproducción de foto/audio, si esas funciones se utilizan.
- [ ] Zumbido con ambas apps abiertas.
- [ ] Zumbido con la app receptora en segundo plano; debe detenerse cuando el emisor suelte el botón.
- [ ] Llamada de voz saliente, aceptar, hablar en ambos sentidos, silenciar, altavoz y colgar.
- [ ] Videollamada, comprobar imagen local/remota y cambiar cámara.
- [ ] Repetir con la pantalla bloqueada y después usando datos móviles en uno de los teléfonos.

## Firebase

Las funciones de servidor no se activan por incluirlas en el ZIP. Desde la raíz del proyecto, con acceso al proyecto Firebase correcto, revisar primero las reglas y después ejecutar:

```bash
firebase use d-oamor-24510
firebase deploy --only functions
firebase deploy --only firestore:rules,storage
```

No publiques reglas sin revisar que el proyecto destino sea el correcto. Para conexiones WebRTC entre algunas redes móviles o NAT, puede ser necesario configurar TURN; los STUN públicos incluidos no garantizan conectividad universal.

## Qué información guardar si algo falla

- Primer error de sincronización/compilación Gradle completo.
- Modelo de los dos teléfonos y versión Android.
- Si falló audio, vídeo, llamada entrante, FCM o zumbido.
- Logcat filtrado por `D-oAmor`, `DuoAmorFCM` y `D-oAmor-WebRTC`.
- Errores recientes de Cloud Functions y Firestore.

No compartas claves privadas, tokens de acceso ni credenciales de cuentas en los registros.
