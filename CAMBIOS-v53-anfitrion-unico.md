# SINI-JOSS v53 — anfitrión único de sala

Base exclusiva: v52. No se restauraron archivos de versiones anteriores.

## Cambios
- El anfitrión de la sala se calcula de forma determinista con el UID ordenado de los participantes compartidos, para evitar que dos dispositivos se nombren anfitriones por elegir tarjetas a la vez.
- Solo ese anfitrión puede elegir el juego y publicar la sala de espera; el otro dispositivo muestra un aviso y espera la selección compartida.
- La pantalla de espera identifica al anfitrión y conserva el inicio controlado por él.

## Validación
- Revisión estática del flujo modificado y de las llamadas a `publishGame`.
- Integridad del ZIP comprobada después de empaquetar.
- No se declara compilación Android ni prueba con Firestore/dispositivos: el proyecto carece de `gradlew` y `gradle-wrapper.jar`, y este entorno no tiene las dependencias Android/Compose necesarias para compilar.

## Pendiente
- Confirmar la política de anfitrión deseada (actualmente se determina por UID ordenado, no por propietario de la pareja).
- Probar sincronización en dos dispositivos y revisar reglas Firestore.
- Completar y probar todas las reglas del Truco, economía de todos los juegos y llamadas en segundo plano.
