# KOMBO ARENA v121 — pistas, coches y ficha de Monopoly

Base acumulativa: `KOMBO-ARENA-v120-conexion-virus-carrera-monopoly.zip`. Se conservaron los cambios de v120 y se aplicaron cambios adicionales encima de esa base.

## Cambios de esta tanda

- Carreras: selector previo de tres trazados (Costa suave, Montaña técnica y Circuito urbano). Cada variante usa una geometría de curvas y desniveles distinta, manteniendo la longitud del circuito para no alterar el sistema de vueltas.
- Carreras: el anfitrión publica la pista elegida junto con el identificador de carrera; los teléfonos que reciben el inicio seleccionan el mismo trazado.
- Carreras: selector visual del color del coche antes de empezar. El color del coche se publica con los datos del piloto.
- Monopoly: selector de ficha antes de empezar y persistencia local de la preferencia. La ficha elegida se asigna al jugador que inicia la partida; el resto conserva asignación automática sin duplicados.
- Monopoly: dados con una animación más larga y pausada, y recorrido visual de las fichas más lento casillero por casillero.

## Pendiente

- La selección de ficha todavía no se comparte entre todos los jugadores antes del inicio: la selección persistida en este cambio se aplica al jugador que inicia la partida. La selección individual de cada participante requiere sincronizar las preferencias de preparación en Firestore antes de crear el estado inicial.
- La sincronización general de todos los juegos requiere comprobar el proyecto Firebase real, autenticación, reglas y configuración utilizada en GitHub Actions.
- La selección actual de coches permite elegir el color; la selección de modelos 3D distintos queda pendiente.
- Los efectos de entrada basados en emojis y el mecanismo de actualización obligatoria siguen pendientes.
- No se ha generado un APK en este entorno: no se incluye `gradlew` y no hay Gradle instalado. Es obligatorio verificar GitHub Actions antes de distribuir.
