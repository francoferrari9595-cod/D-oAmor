# KOMBO ARENA — compilación y conexión

## Obtener el APK desde GitHub Actions
1. Sube estos archivos al repositorio de GitHub conservando la carpeta `.github/workflows`.
2. Abre **Actions** y ejecuta **Android APK** (Run workflow), o haz un push a `main`/`master`.
3. Entra en la ejecución más reciente. Si termina en verde, baja a **Artifacts** y descarga `KOMBO-ARENA-debug-apk`.
4. Descomprime el ZIP descargado y usa el APK en ambos teléfonos.

El flujo compila un APK **debug** para pruebas y lo conserva durante 14 días. No publica una versión firmada de producción.

## Configuración Firebase obligatoria para conectar dos teléfonos
Se agregó `app/google-services.json` del proyecto `d-oamor-24510` (paquete `com.aistudio.duoamor.wkqz`). Además, el inicio de sesión depende de Firebase Authentication/Google y el repositorio de mensajes depende de Cloud Firestore. Por eso, que el APK compile no significa que la comunicación entre teléfonos ya esté conectada.

1. El archivo `app/google-services.json` incluido corresponde al proyecto Firebase `sini-joss-nuevo` y al paquete `com.aistudio.duoamor.wkqz`. El alias `.firebaserc` y el workflow de publicación de reglas también apuntan a `sini-joss-nuevo`.
2. En Firebase Console, habilita el proveedor de Authentication que se vaya a utilizar. El JSON actual no contiene OAuth clients, por lo que Google Sign-In puede requerir SHA-1/SHA-256 y volver a descargar el JSON.
4. Habilita **Authentication → Google** y Cloud Firestore en el proyecto Firebase.
5. Crea/verifica la base de datos Firestore con el ID configurado en `app/src/main/res/values/firebase_applet_config.xml`.
6. Publica las reglas de `firestore.rules` en esa base de datos y verifica la lógica de emparejamiento/autorización antes de usarla con datos reales.

## Si Actions falla
Abre la ejecución fallida y mira el primer paso rojo. El log de Gradle contiene el error real; no basta con mirar la pestaña Artifacts, porque los artefactos solo aparecen cuando la compilación genera el APK.

## Limitación detectada en el proyecto recibido
El proyecto todavía contiene un código de pareja fijo (`AMOR-7429`) y datos de demostración. Agregar `google-services.json` no implementa el emparejamiento real. Para producción hay que desarrollar y probar el flujo de invitación de un solo uso, la persistencia del vínculo, autenticación y reglas de Firestore seguras. No debe considerarse resuelto solo por compilar.


## Correcciones de compilación (octubre de 2026)

- El workflow crea automáticamente `debug.keystore` para que la firma debug no falle.
- `AuthManager` busca `default_web_client_id` dinámicamente, por lo que el APK puede compilar sin el archivo de Firebase.
- Para que Google Sign-In y la sincronización Firebase funcionen, configura el secreto de GitHub `FIREBASE_GOOGLE_SERVICES_JSON` con el contenido real del archivo `google-services.json` de tu proyecto Firebase. Un APK que compila sin ese archivo no implica que la autenticación o la conexión estén configuradas.

## Correcciones de estabilidad y presentación

- La aplicación usa el nombre visible **KOMBO ARENA** y el workflow publica el archivo **KOMBO-ARENA.apk** dentro del artefacto **KOMBO-ARENA-APK**.
- Se agregó un icono propio de corazón para los iconos adaptativos y las versiones antiguas de Android.
- Si falta `app/google-services.json`, la aplicación ya no debe cerrarse solo por intentar crear `FirebaseAuth`/Firestore: entra en modo sin conexión y la sincronización en la nube queda deshabilitada. Para usar inicio de sesión y sincronización real entre dos dispositivos, hay que agregar la configuración auténtica del proyecto Firebase.
- La compilación de GitHub Actions debe probarse luego de subir estos cambios. Si aún se cierra en el teléfono, hace falta el error `FATAL EXCEPTION` de Logcat para identificar el siguiente fallo concreto.


Se incluye `CONFIGURACION-FIREBASE-PENDIENTE.txt` con los siguientes pasos de configuración y las limitaciones actuales.


## v128: reglas y presencia
Las reglas (`firestore.rules`) deben estar publicadas en `sini-joss-nuevo`, base `(default)`. Se publican solas al hacer push si existe el secreto `FIREBASE_SERVICE_ACCOUNT_SINI_JOSS_NUEVO`; si no, pégalas en Firebase Console. Detalle en `CAMBIOS-v128-PRESENCIA-REAL-REGLAS.md`.
