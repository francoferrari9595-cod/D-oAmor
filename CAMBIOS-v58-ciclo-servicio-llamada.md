# v58 — Ciclo de vida del servicio de llamada

Base: SINI-JOSS v57.

## Cambios
- El cierre local de llamada usa `stopService` en vez de intentar iniciar el servicio con una acción de parada, reduciendo el riesgo de excepción al cerrar desde segundo plano en Android moderno.
- El servicio de mantener viva la llamada devuelve `START_NOT_STICKY`: WebRTC no se reconstruye automáticamente tras la muerte del proceso, por lo que reiniciar solo la notificación podría mostrar una llamada falsa.

## Validación
- Revisión estática de las rutas de inicio/cierre.
- ZIP verificado con `unzip -t`.
- No se pudo probar una llamada real ni compilar Android en este entorno; requiere dispositivos, permisos y configuración Firebase/WebRTC reales.
