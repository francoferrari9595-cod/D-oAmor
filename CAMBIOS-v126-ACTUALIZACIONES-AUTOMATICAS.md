# KOMBO ARENA v126 — Avisos automáticos de actualización

Base: KOMBO ARENA v125.

## Cambios
- La aplicación compara `latestVersionCode` con la versión instalada y muestra un aviso de actualización disponible cuando existe una versión más nueva.
- El aviso permite elegir «Actualizar ahora» o «Más tarde».
- Se conserva el comportamiento de actualización obligatoria mediante `minimumSupportedVersionCode`, que no permite cerrar el aviso.
- La consulta de `update.json` evita reutilizar una respuesta cacheada y mantiene el funcionamiento normal si no hay conexión.
- GitHub Actions incrementa `versionCode` y `versionName` en cada publicación exitosa.
- Después de publicar el APK, GitHub Actions actualiza `update.json` en `main`, conservando el mínimo obligatorio y publicando la versión que acaba de generar.

## Verificación
- JSON de actualización validado.
- Sintaxis YAML del workflow validada.
- Lógica de incremento de versión comprobada con la configuración actual.
- No se pudo ejecutar Gradle en este entorno porque no hay Gradle ni `gradlew` disponibles. La compilación final debe confirmarse en GitHub Actions.
