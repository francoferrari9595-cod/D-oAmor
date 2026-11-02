# KOMBO ARENA v120 — primera tanda acumulativa

Base: `KOMBO-ARENA-v119-corregido.zip`. No se recuperaron archivos de versiones antiguas.

## Incluido

- El nombre elegido para entrar a una sala pública también se guarda como `player_name`; al salir de una sala se conserva el nombre, y `roomDisplayName()` vuelve a usarlo aunque ya no exista `lobby_name` de la sala anterior.
- Se genera y conserva un identificador local UUID (`device_id`) que no cambia al cambiar de sala. La presencia de salas públicas lo publica junto al nombre para poder distinguir el dispositivo en los datos de presencia.
- `updateMyName()` persiste el nombre elegido y actualiza el latido si la persona está dentro de una sala pública.
- Virus 1 / Virus 2: corregido el Guante de látex. Quien lo juega conserva sus cartas; todos los demás descartan la mano completa. Al llegarles el turno, roban una mano nueva y pierden ese turno. El bot también puede jugar esta carta sin elegir un objetivo.
- Carreras: desactivados los obstáculos fijos (conos, barreras y pads) para dejar la pista limpia. El texto de ayuda fue actualizado.
- Monopoly: se ralentizó el movimiento visual ficha por ficha y se extendió la animación de los dados para que el resultado se lea mejor.

## Pendiente que requiere trabajo y verificación adicional

- Sincronización completa entre teléfonos: hay que verificar el proyecto Firebase real, Authentication, Firestore, las reglas desplegadas y el `google-services.json` usado por Actions. Un APK que compila no prueba que Firebase esté configurado. La configuración actual del workflow puede generar un APK aunque falte Firebase.
- Selección real de pistas/mapas/coches antes de la carrera y selección de ficha de Monopoly por cada jugador: todavía no están implementadas en esta tanda; necesitan guardar la elección compartida en el estado de sala para que todos los teléfonos vean la misma configuración.
- Reemplazo de todos los efectos de entrada basados en emojis por animaciones gráficas profesionales: todavía no está implementado en esta tanda.
- Actualización obligatoria instalada desde GitHub: todavía no está habilitada. El workflow actual genera una clave debug nueva en cada ejecución y eso impide que los APK se actualicen entre sí de forma fiable. Para actualizar instalaciones existentes se necesita la misma clave privada de firma, un `versionCode` creciente y un mecanismo de distribución/aviso. Android normalmente exige que el usuario confirme la instalación; una app común no puede instalar silenciosamente un APK por sí sola.

## Verificación

No se pudo ejecutar Gradle en este entorno (el ZIP no incluye `gradlew` y no hay instalación local de `gradle`). Subir esta versión a GitHub Actions y revisar el primer error rojo sigue siendo obligatorio antes de distribuir el APK.
