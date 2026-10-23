# SINI-JOSS v104 — Rediseño visual + Virus arcoíris

- Nueva carta `card_21_virus_multicolor.jpg` (la de tu captura). Antes el virus multicolor (código 14) se mostraba con la imagen del virus rojo.
- Regla `virusMatches`: el virus arcoíris infecta órganos de cualquier color; un virus de color solo su color o el comodín. Test: `VirusRulesTest`.
- Virus: mesa nueva, mazo/descarte, manos rivales en abanico con balanceo lentísimo, cartas pegadas a los órganos, halos (rojo infectado / cian inmune / dorado objetivo válido), animación de carta jugada sincronizada (campos `lc`, `lb`, `ls` en el estado; no cambian conexiones).
- Componentes base: GlassCard, GradientButton, SoftButton con rebote al presionar y brillos; fondo con orbes y polvo de estrellas.
- Menú: barra inferior con píldora deslizante, transición direccional entre pestañas, tarjetas de juegos nuevas (`GameTile`), bienvenida con anillos orbitales.

## Pendiente de validar
- No se pudo compilar en este entorno: compilar en Android Studio / GitHub Actions y probar en dos teléfonos.
