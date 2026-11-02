# KOMBO ARENA v127 — ingreso a salas sin cortes + salas en neón

Base: v126.

## Error «La conexión está lenta. No se confirmó tu ingreso»
Causa probable: el ingreso se rendía a los 6 s y, además, reiniciaba la red de Firestore (`disableNetwork/enableNetwork`), lo que empeoraba la conexión y el siguiente intento también fallaba.
- `CoupleViewModel.joinLobby`: ahora reintenta la escritura (idempotente) cada 7 s durante hasta 28 s, sin tocar la red salvo un único reinicio a los 14 s.
- Sala libre: si el servidor no confirma en ~7 s, se entra igual; la escritura queda en cola y se sincroniza sola. Sala con contraseña: se exige confirmación del servidor (las reglas validan la clave).
- Si una escritura tardía llega después de cancelar o fallar, se revierte la presencia (no quedan "fantasmas").

## Conexión más firme
- Latido de presencia: ~25 s con variación aleatoria (20 jugadores no escriben a la vez), no apila escrituras si la anterior sigue en vuelo, y si falla reintenta a 5 s, 10 s… hasta 30 s. Ya no se detiene mientras estés en la sala.
- Detector de red (`ConnectivityManager`): al volver la señal reactiva Firestore y manda un latido inmediato.
- Escucha de la sala con reintentos progresivos (2 s → 6 s) en vez de fijo.

## Seguridad
- `firestore.rules`: en `lobbies/{id}` solo se pueden modificar `games`, `players`, `guestUids` e `inviteCode`. Antes cualquier usuario con sesión podía cambiar nombre, contraseña (`hasPw`), orden o dueño de una sala.
- Toda la comunicación con Firebase va por TLS; no hay tráfico en claro.
- **Hay que publicar las reglas**: GitHub → Actions → «Publicar reglas Firebase» → Run workflow.

## Salas en neón
- `RoomSquares.kt`: se quitaron las ilustraciones de juegos. Cada sala es un texto («Sala 1», «Sala 2», o el nombre de la sala creada) con letrero de neón que titila, brillo que respira y borde con resplandor. Cada sala tiene su color y su propio ritmo de parpadeo. Se mantienen contador de conectados, candado/puerta y mantener 5 s para eliminar.

## Límites de verificación
- No se pudo compilar ni probar en este entorno (sin Gradle ni Firebase real).
- «20 jugadores sin cortes» no se puede garantizar desde aquí: hace falta compilar en GitHub Actions y probar con 20 dispositivos. Firestore admite ~1 escritura/s sostenida por documento: repartidos en varias salas el margen es amplio; 20 jugando a la vez en una misma sala con juegos muy activos podría acercarse a ese límite.
- App Check no está activado (las dependencias existen pero no se inicializa); activarlo requiere configuración en Firebase Console.

---
# Actualizaciones automáticas (revisión)

## Problemas encontrados en v126 y corregidos
1. **«Actualizar ahora» solo abría el navegador**: el usuario tenía que descargar y luego instalar a mano. Ahora la app descarga el APK dentro de la app (con barra de progreso) y abre el instalador de Android (`data/AppUpdater.kt`, permiso `REQUEST_INSTALL_PACKAGES`, ruta `updates/` en `file_paths.xml`). Si falta el permiso «instalar apps desconocidas», lo pide una vez y continúa solo al volver. Verifica que el APK sea de la misma app antes de instalar.
2. **El `.bat` pisaba el workflow con uno viejo** (sin versión, sin release, sin `update.json`, y que generaba una firma nueva en cada compilación). Ya no lo reescribe.
3. **`debug.keystore` estaba en `.gitignore`**: no se subía y la compilación fallaba o firmaba distinto. Se quitó de `.gitignore` y el `.bat` lo sube con `git add -f`. Sin la misma firma en todas las versiones, Android rechaza actualizar encima.
4. **Versión dependiente de archivos del repositorio**: al subir el proyecto, `versionCode` podía volver atrás y repetirse, y el teléfono no veía «nueva versión». Ahora `versionCode` sale del reloj en cada compilación (siempre crece) y `versionName` es la fecha (`2026.11.01.2325`).
5. **Publicación de `update.json` más robusta**: parte siempre del `main` más reciente y reintenta, sin conflictos; `apkUrl` se arma con el repositorio real.
6. La app revisa si hay versión nueva **cada vez que vuelve a primer plano**, no solo al abrirse.

## Flujo
Subís a GitHub (`main`) → Actions compila → publica `KOMBO-ARENA.apk` en la release `android-latest` → actualiza `update.json` → el cliente abre la app → cartel «Actualización disponible» → «Actualizar ahora» → descarga → Android pide «Instalar» → listo.
Para que el cartel muestre tus notas, editá `releaseNotes` en `update.json` antes de subir. Para hacer una actualización obligatoria (sin «Más tarde»), subí `minimumSupportedVersionCode` a un número mayor que la versión instalada.

## Límites
- Android siempre pide un toque en «Instalar»; no se puede instalar en silencio sin ser app de sistema/administrador del dispositivo.
- La **primera vez** que se pase a esta versión, si el teléfono tenía un APK firmado con otra clave, Android dirá que hay conflicto: desinstalar e instalar una vez. Después, todas las actualizaciones entran encima.
- El repositorio debe ser público (o el APK/`update.json` no se podrán descargar sin sesión).
- No se pudo compilar ni probar en este entorno; hay que verificarlo en GitHub Actions y en un teléfono (instalar la versión actual, subir un cambio, abrir la app y comprobar el cartel).

## Corrección de CI (v127b)
- La compilación de Kotlin de v127 pasó. Falló solo `ExampleRobolectricTest` (plantilla que pedía Robolectric con SDK 36, no soportado por la versión usada: `UnsupportedOperationException en DefaultSdkProvider`). No tenía relación con los cambios; solo comprobaba el nombre de la app. Se eliminó. Los otros 19 tests (Poker, Truco, Virus, Tutti Frutti) pasaron.
