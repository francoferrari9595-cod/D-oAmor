# KOMBO ARENA v122 — continuación acumulativa de v121

Base preservada: `KOMBO-ARENA-v121-pistas-coches-monopoly.zip`.

## Cambios
- Monopoly: cada jugador publica su ficha elegida y el anfitrión intenta respetar las elecciones recientes de todos al crear la partida; se evita asignar dos veces una misma ficha. La preferencia local sigue guardada en el dispositivo.
- Carreras: se guardan localmente la pista y el color del coche elegidos para la próxima sesión.
- Versionado: `versionCode=122`, `versionName=1.22`.
- Actualización obligatoria: la app consulta `update.json` en el repositorio y bloquea el acceso si el servidor marca un mínimo superior al instalado; si no hay conexión o no puede consultar el archivo, deja abrir la app para no bloquear por un fallo de red.
- Distribución: GitHub Actions publica el APK como asset de la versión `android-latest`; `update.json` apunta a esa descarga.
- Firma de depuración: se incluye una clave debug estable para que los APK futuros generados desde esta base usen la misma firma.

## Importante
- Para que el control de actualización funcione, `update.json` debe estar publicado en la rama `main` y el asset `KOMBO-ARENA.apk` debe existir en la release `android-latest`.
- Los APK anteriores se generaron con claves debug efímeras. Por eso esta clave estable no puede garantizar una actualización directa sobre todos los APK antiguos: puede ser necesario desinstalar una versión antigua y perder sus datos locales. Desde los APK instalados con esta base, los siguientes builds conservarán la firma mientras se mantenga `debug.keystore`.
- La selección remota de fichas depende de que cada jugador haya abierto Monopoly y publicado su preferencia recientemente antes de que el anfitrión inicie.
- La sincronización de partidas continúa dependiendo de la configuración real de Firebase/Firestore y sus reglas.
- No se pudo ejecutar la compilación Gradle en este entorno; hay que verificar `:app:testDebugUnitTest` y `:app:assembleDebug` en GitHub Actions antes de distribuir.
