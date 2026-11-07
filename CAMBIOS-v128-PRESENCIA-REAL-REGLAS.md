# KOMBO ARENA v128 — presencia real, reglas nuevas, sin coins de amor

Base: v127.

## Por qué siempre aparecían «conectados» y los dispositivos no se veían
1. El «conectado» de la pareja estaba fijo en `true` (`CoupleUser.isOnline = true`): nunca reflejaba nada real.
2. La presencia de las salas guardaba la hora del reloj de cada teléfono (`t`) y la comparaba con el reloj del
   teléfono que mira. Si dos teléfonos difieren unos minutos, uno desaparecía para el otro. Además, la lista contaba
   escrituras locales aún no confirmadas, y en salas libres se entraba aunque el servidor no hubiera confirmado nada.
3. Los latidos de todos se escribían en el mismo documento de la sala que las jugadas (`games`), con el límite de
   ~1 escritura/s por documento.

## Presencia real (nueva)
- Un documento por persona: `lobbyPresence/{uid}` (salas públicas) y `roomPresence/{uid}` (pareja/invitados), con
  `lastSeen = serverTimestamp()` (hora del servidor; las reglas obligan a que sea `request.time`).
- Solo cuenta como conectado quien tiene un latido **confirmado por el servidor** con menos de 75 s. Una escritura
  local pendiente (sin conexión) no cuenta, ni para mí ni para los demás.
- Todas las comparaciones usan la hora del servidor: el teléfono se calibra con `clock/{uid}` (escribe un sello,
  lo lee de vuelta y calcula la desviación), al abrir la lista y cada 6 latidos. El reloj del teléfono ya no importa.
- Latido cada ~25 s (+0–4 s aleatorio); si falla reintenta a 5 s, 10 s… hasta 30 s. Un solo bucle para sala pública,
  pareja e invitados. Al volver la red manda un latido inmediato. Cada segundo se reevalúa quién sigue conectado
  (los que cierran la app sin salir desaparecen solos en ≤ 75 s; los que salen con el botón, al instante).
- Lista de salas: un solo escuchador sobre `lobbyPresence` (cuenta por sala). Dentro de la sala: `guestUids` y
  nombres salen de la presencia real; el documento de la sala ya no recibe latidos.
- Chip visible arriba en la sala: **Conectado · N en la sala** (verde, solo con confirmación del servidor),
  **Conectando…** (amarillo), **Sin conexión** (rojo) o **Reglas sin publicar** (rojo, si Firestore responde
  PERMISSION_DENIED).
- Chat de pareja: el punto verde del compañero ahora depende de su presencia real (antes era fijo).

## Reglas de Firestore (`firestore.rules`)
- Nuevas: `lobbyPresence`, `roomPresence` (solo escribe su dueño, campos y tamaños validados, `lastSeen == request.time`,
  lobby vs. no-lobby separados; cualquiera puede borrar latidos de más de 10 min para que no se acumulen) y `clock`.
- Se conservan las reglas existentes de salas, contraseñas (`joins`/`private`), chat, llamadas, carreras y pareja, para
  no romper nada: la edición de `lobbies/{id}` sigue limitada a `games`, `players`, `guestUids`, `inviteCode`.
- **Hay que publicarlas.** Ahora se publican solas con cada push que cambie `firestore.rules` si existe el secreto
  `FIREBASE_SERVICE_ACCOUNT_SINI_JOSS_NUEVO`. Si no existe: Firebase Console → Firestore Database → Reglas → pegar el
  contenido de `firestore.rules` → Publicar. Sin esto la app mostrará «Reglas sin publicar».
- Proyecto: `sini-joss-nuevo`, base `(default)` (la app usa `FirebaseFirestore.getInstance()`).

## Coins de amor
- Se quitó la tarjeta «💞 pareja 🪙» (monedas de la pareja) de la billetera y el flujo `partnerCredits`. Queda solo
  «🪙 Mis coins».

## Actualización obligatoria al abrir la versión anterior
- `update.json` lleva `"forceThisRelease": true`. Al terminar la compilación con éxito, GitHub Actions publica el APK,
  fija `latestVersionCode` y `minimumSupportedVersionCode` al código de esta compilación y apaga la bandera.
  Resultado: al abrir cualquier versión anterior aparece «Actualizar ahora» sin «Más tarde», descarga dentro de la
  app y abre el instalador de Android.
- Si el teléfono tenía un APK firmado con otra clave, Android pedirá desinstalar una sola vez (ver v122).

## Verificación y límites
- No se pudo compilar ni probar en este entorno (no hay Android SDK ni emulador de Firebase). Falta: ejecutar Actions,
  publicar reglas y probar con dos teléfonos en la misma sala.
- Prueba sugerida: dos teléfonos en «Sala 1» → ambos deben mostrar «Conectado · 2 en la sala» y el contador de la
  lista debe marcar 2; poner uno en modo avión → en ≤ 75 s el otro baja a 1 y el del avión muestra «Sin conexión».

## v129 — «se queda en Conectando…»
Con v128 el latido nunca fue confirmado ni rechazado por el servidor (sin error): las escrituras quedaban en cola. Sin logs no se puede saber la causa exacta desde aquí, así que:
- **Diagnóstico dentro de la app:** tocar el chip «Conectando… (toca)» ejecuta 5 pruebas reales contra Firebase (sesión anónima, escribir, leer del servidor, presencia, borrar) con límite de 10 s cada una e indica la causa: reglas sin publicar (PERMISSION_DENIED), base (default) inexistente (NOT_FOUND), sin salida a Firebase / API de Firestore deshabilitada (UNAVAILABLE o «sin respuesta»), o Authentication → Anónimo deshabilitado.
- Caché de Firestore solo en memoria y `enableNetwork()` al arrancar: una cola de escrituras vieja en disco no puede bloquear las nuevas.

## v130
La compilación de Kotlin de v129 pasó; solo falló `ExampleRobolectricTest` (plantilla vieja que sigue en tu repositorio al copiar encima). Ahora Gradle la excluye aunque el archivo exista. Si quieres, bórrala también de `app/src/test/java/com/example/`.

## v131
- `firestore.rules`: las colecciones nuevas (`lobbyPresence`, `roomPresence`, `clock`) usan reglas mínimas (sesión + solo tu propio documento), sin funciones ni validaciones que pudieran impedir la publicación. El resto de reglas no cambió.
- El diagnóstico distingue «lecturas OK pero escrituras sin respuesta» (cuota diaria de escrituras del plan Spark agotada, o reglas en otra base) de PERMISSION_DENIED.

## v132
Con las reglas ya publicadas, las lecturas funcionan y las escrituras del SDK no responden (sin error). El diagnóstico añade el paso 6: la misma escritura por REST, que devuelve el código y el mensaje reales de Firebase (cuota agotada, API deshabilitada, permisos…).
