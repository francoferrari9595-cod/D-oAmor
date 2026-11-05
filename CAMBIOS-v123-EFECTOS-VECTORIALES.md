# KOMBO ARENA v123 — Efectos vectoriales acumulativos

Base: v122-sincronizacion-actualizaciones. No se recuperan archivos de versiones anteriores.

## Cambio
- Se incorpora el efecto visual `portal` con anillos luminosos, partículas y centro radial, dibujado con Canvas/Compose y sin depender de emojis para sus elementos principales.
- Se conserva el motor actual de efectos, sus preferencias de usuario, la cola de eventos y la vista previa de la tienda.
- Se preservan las modificaciones de conexión, nombre por dispositivo, Virus, carreras, fichas de Monopoly, `update.json`, publicación del APK y firma fija presentes en v122.

## Validación
- Se verificó la edición del archivo fuente y la integridad del ZIP resultante.
- No se pudo ejecutar Gradle en este entorno; el APK no se declara compilado ni probado. La compilación de GitHub Actions sigue siendo la validación obligatoria antes de distribuirlo.
- El efecto `portal` queda disponible en el motor de efectos; para ofrecerlo como artículo seleccionable en la tienda debe existir también su registro correspondiente en el catálogo de efectos.
