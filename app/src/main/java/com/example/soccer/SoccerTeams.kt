package com.example.soccer

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Camisetas de selecciones y clubes. [pattern]: 0 lisa, 1 rayas verticales, 2 franja horizontal (Boca), 3 banda diagonal (River),
 * 4 aros horizontales, 5 mitades, 6 manga/franja lateral.
 */
internal class SKit(
  val name: String, val short: String, val club: Boolean,
  val shirt: Color, val shirt2: Color, val pattern: Int, val shorts: Color, val socks: Color, val gk: Color = Color(0xFF43A047)
) {
  /** Camiseta alternativa cuando los dos equipos se parecen mucho. */
  fun alt(): SKit {
    val light = (shirt.red + shirt.green + shirt.blue) / 3f > 0.62f
    return if (light) SKit(name, short, club, Color(0xFF14213D), shirt, 0, Color(0xFF14213D), Color(0xFF14213D), gk)
    else SKit(name, short, club, Color(0xFFFAFAFA), shirt, 6, Color(0xFFFAFAFA), Color(0xFFFAFAFA), gk)
  }
}

private fun c(v: Long) = Color(v)
private val W = c(0xFFFAFAFA); private val K = c(0xFF151515)

private fun nat(n: String, s: String, a: Long, b: Long, p: Int, sh: Long, so: Long) = SKit(n, s, false, c(a), c(b), p, c(sh), c(so))
private fun clb(n: String, s: String, a: Long, b: Long, p: Int, sh: Long, so: Long) = SKit(n, s, true, c(a), c(b), p, c(sh), c(so))

internal val ALL_KITS: List<SKit> = listOf(
  // ── Selecciones ──
  nat("Argentina", "ARG", 0xFF75AADB, 0xFFFAFAFA, 1, 0xFF151515, 0xFFFAFAFA),
  nat("México", "MEX", 0xFF006847, 0xFF006847, 0, 0xFFFAFAFA, 0xFF006847),
  nat("Brasil", "BRA", 0xFFFFDF00, 0xFF009C3B, 0, 0xFF1D3F94, 0xFFFAFAFA),
  nat("Uruguay", "URU", 0xFF6CB4EE, 0xFF6CB4EE, 0, 0xFF151515, 0xFF151515),
  nat("Colombia", "COL", 0xFFFCD116, 0xFF003893, 0, 0xFF003893, 0xFFCE1126),
  nat("Chile", "CHI", 0xFFD52B1E, 0xFFD52B1E, 0, 0xFF0B3D91, 0xFFFAFAFA),
  nat("Paraguay", "PAR", 0xFFD52B1E, 0xFFFAFAFA, 1, 0xFF0B3D91, 0xFFD52B1E),
  nat("Perú", "PER", 0xFFFAFAFA, 0xFFD91023, 3, 0xFFFAFAFA, 0xFFFAFAFA),
  nat("Ecuador", "ECU", 0xFFFFD100, 0xFF003893, 0, 0xFF003893, 0xFFFFD100),
  nat("Venezuela", "VEN", 0xFF7B1E3A, 0xFF7B1E3A, 0, 0xFF7B1E3A, 0xFF7B1E3A),
  nat("Bolivia", "BOL", 0xFF007A33, 0xFF007A33, 0, 0xFFFAFAFA, 0xFF007A33),
  nat("Estados Unidos", "USA", 0xFFFAFAFA, 0xFF0A3161, 6, 0xFF0A3161, 0xFFB31942),
  nat("Canadá", "CAN", 0xFFD52B1E, 0xFFD52B1E, 0, 0xFF151515, 0xFFD52B1E),
  nat("Costa Rica", "CRC", 0xFFCE1126, 0xFF002B7F, 0, 0xFF002B7F, 0xFFCE1126),
  nat("Panamá", "PAN", 0xFFDA121A, 0xFF072357, 0, 0xFF072357, 0xFFDA121A),
  nat("Honduras", "HON", 0xFFFAFAFA, 0xFF0073CF, 0, 0xFF0073CF, 0xFFFAFAFA),
  nat("Jamaica", "JAM", 0xFFFED100, 0xFF009B3A, 0, 0xFF151515, 0xFFFED100),
  nat("España", "ESP", 0xFFC60B1E, 0xFFFFC400, 0, 0xFF1B2A6B, 0xFFC60B1E),
  nat("Portugal", "POR", 0xFF8B0D1C, 0xFF006600, 0, 0xFF006600, 0xFF8B0D1C),
  nat("Francia", "FRA", 0xFF1B3A8C, 0xFFFAFAFA, 0, 0xFFFAFAFA, 0xFFC8102E),
  nat("Alemania", "ALE", 0xFFFAFAFA, 0xFF151515, 0, 0xFF151515, 0xFFFAFAFA),
  nat("Inglaterra", "ING", 0xFFFAFAFA, 0xFF1B2A6B, 0, 0xFF1B2A6B, 0xFFFAFAFA),
  nat("Italia", "ITA", 0xFF0F4FA8, 0xFF0F4FA8, 0, 0xFFFAFAFA, 0xFF0F4FA8),
  nat("Países Bajos", "NED", 0xFFFF6A13, 0xFFFF6A13, 0, 0xFFFF6A13, 0xFFFF6A13),
  nat("Bélgica", "BEL", 0xFFC8102E, 0xFFFFD100, 0, 0xFFC8102E, 0xFFC8102E),
  nat("Croacia", "CRO", 0xFFFAFAFA, 0xFFE4002B, 4, 0xFFFAFAFA, 0xFF0F4FA8),
  nat("Suiza", "SUI", 0xFFD52B1E, 0xFFFAFAFA, 0, 0xFFFAFAFA, 0xFFD52B1E),
  nat("Dinamarca", "DIN", 0xFFC8102E, 0xFFFAFAFA, 0, 0xFFFAFAFA, 0xFFC8102E),
  nat("Suecia", "SUE", 0xFFFECC00, 0xFF006AA7, 0, 0xFF006AA7, 0xFFFECC00),
  nat("Noruega", "NOR", 0xFFC8102E, 0xFF00205B, 0, 0xFF00205B, 0xFFC8102E),
  nat("Polonia", "POL", 0xFFFAFAFA, 0xFFDC143C, 0, 0xFFDC143C, 0xFFFAFAFA),
  nat("Serbia", "SRB", 0xFFC6363C, 0xFF0C4076, 0, 0xFF0C4076, 0xFFC6363C),
  nat("Ucrania", "UCR", 0xFFFFD500, 0xFF0057B8, 0, 0xFFFFD500, 0xFFFFD500),
  nat("Turquía", "TUR", 0xFFE30A17, 0xFFFAFAFA, 0, 0xFFFAFAFA, 0xFFE30A17),
  nat("Escocia", "ESC", 0xFF0B2E6B, 0xFF0B2E6B, 0, 0xFFFAFAFA, 0xFF0B2E6B),
  nat("Gales", "GAL", 0xFFC8102E, 0xFF00B140, 0, 0xFFFAFAFA, 0xFFC8102E),
  nat("Irlanda", "IRL", 0xFF169B62, 0xFF169B62, 0, 0xFFFAFAFA, 0xFF169B62),
  nat("Austria", "AUT", 0xFFED2939, 0xFFFAFAFA, 0, 0xFFFAFAFA, 0xFFED2939),
  nat("Rep. Checa", "CZE", 0xFFD7141A, 0xFF11457E, 0, 0xFFFAFAFA, 0xFFD7141A),
  nat("Grecia", "GRE", 0xFF0D5EAF, 0xFFFAFAFA, 0, 0xFFFAFAFA, 0xFF0D5EAF),
  nat("Rumania", "RUM", 0xFFFCD116, 0xFFCE1126, 0, 0xFFFCD116, 0xFFFCD116),
  nat("Hungría", "HUN", 0xFFCE2939, 0xFF477050, 0, 0xFFFAFAFA, 0xFF477050),
  nat("Marruecos", "MAR", 0xFFC1272D, 0xFF006233, 0, 0xFFC1272D, 0xFFC1272D),
  nat("Senegal", "SEN", 0xFFFAFAFA, 0xFF00853F, 0, 0xFF00853F, 0xFFFAFAFA),
  nat("Nigeria", "NGA", 0xFF008751, 0xFFFAFAFA, 0, 0xFF008751, 0xFF008751),
  nat("Egipto", "EGI", 0xFFCE1126, 0xFF151515, 0, 0xFFFAFAFA, 0xFF151515),
  nat("Camerún", "CMR", 0xFF007A5E, 0xFFCE1126, 0, 0xFFCE1126, 0xFFFCD116),
  nat("Ghana", "GHA", 0xFFFAFAFA, 0xFFCE1126, 0, 0xFFFAFAFA, 0xFFFAFAFA),
  nat("Argelia", "ALG", 0xFFFAFAFA, 0xFF006233, 0, 0xFF006233, 0xFFFAFAFA),
  nat("Túnez", "TUN", 0xFFE70013, 0xFFFAFAFA, 0, 0xFFFAFAFA, 0xFFE70013),
  nat("Costa de Marfil", "CIV", 0xFFFF8200, 0xFF009E60, 0, 0xFFFAFAFA, 0xFFFF8200),
  nat("Sudáfrica", "RSA", 0xFFFFB81C, 0xFF007749, 0, 0xFF007749, 0xFFFFB81C),
  nat("Japón", "JAP", 0xFF0B3C9D, 0xFF0B3C9D, 0, 0xFF0B3C9D, 0xFF0B3C9D),
  nat("Corea del Sur", "KOR", 0xFFCD2E3A, 0xFF0047A0, 0, 0xFF151515, 0xFFCD2E3A),
  nat("Australia", "AUS", 0xFFFFCD00, 0xFF00843D, 0, 0xFF00843D, 0xFFFFCD00),
  nat("Arabia Saudita", "KSA", 0xFFFAFAFA, 0xFF006C35, 0, 0xFFFAFAFA, 0xFFFAFAFA),
  nat("Irán", "IRN", 0xFFFAFAFA, 0xFFDA0000, 0, 0xFFFAFAFA, 0xFFFAFAFA),
  nat("Qatar", "QAT", 0xFF8A1538, 0xFF8A1538, 0, 0xFF8A1538, 0xFF8A1538),
  nat("China", "CHN", 0xFFDE2910, 0xFFFFDE00, 0, 0xFFDE2910, 0xFFDE2910),
  nat("Nueva Zelanda", "NZL", 0xFFFAFAFA, 0xFF151515, 0, 0xFF151515, 0xFFFAFAFA),
  // ── Clubes ──
  clb("Boca Juniors", "BOC", 0xFF0A2F6B, 0xFFFFC400, 2, 0xFF0A2F6B, 0xFF0A2F6B),
  clb("River Plate", "RIV", 0xFFFAFAFA, 0xFFE4002B, 3, 0xFFFAFAFA, 0xFFFAFAFA),
  clb("Racing Club", "RAC", 0xFF8FC5EC, 0xFFFAFAFA, 1, 0xFFFAFAFA, 0xFF8FC5EC),
  clb("Independiente", "IND", 0xFFD71920, 0xFFD71920, 0, 0xFF0A2F6B, 0xFFD71920),
  clb("San Lorenzo", "SLO", 0xFF0A2F6B, 0xFFC8102E, 1, 0xFF0A2F6B, 0xFFC8102E),
  clb("Vélez Sarsfield", "VEL", 0xFFFAFAFA, 0xFF0A2F6B, 3, 0xFFFAFAFA, 0xFFFAFAFA),
  clb("Estudiantes (LP)", "EDL", 0xFFE4002B, 0xFFFAFAFA, 1, 0xFF151515, 0xFFE4002B),
  clb("Newell's", "NOB", 0xFFE4002B, 0xFF151515, 5, 0xFF151515, 0xFFE4002B),
  clb("Rosario Central", "ROS", 0xFF0A2F6B, 0xFFFFC400, 1, 0xFF0A2F6B, 0xFF0A2F6B),
  clb("Talleres", "TAL", 0xFF0A2F6B, 0xFFFAFAFA, 1, 0xFFFAFAFA, 0xFF0A2F6B),
  clb("Flamengo", "FLA", 0xFFD71920, 0xFF151515, 4, 0xFF151515, 0xFFD71920),
  clb("Palmeiras", "PAL", 0xFF006437, 0xFF006437, 0, 0xFFFAFAFA, 0xFF006437),
  clb("Peñarol", "PEN", 0xFFFFD100, 0xFF151515, 1, 0xFF151515, 0xFFFFD100),
  clb("Nacional (URU)", "NAC", 0xFFFAFAFA, 0xFF0A2F6B, 0, 0xFF0A2F6B, 0xFFFAFAFA),
  clb("Club América", "AME", 0xFFFFD100, 0xFF0A2F6B, 0, 0xFF0A2F6B, 0xFFFFD100),
  clb("Chivas", "CHV", 0xFFD71920, 0xFFFAFAFA, 1, 0xFF0A2F6B, 0xFFD71920),
  clb("Cruz Azul", "CAZ", 0xFF0A4FA8, 0xFF0A4FA8, 0, 0xFF0A4FA8, 0xFF0A4FA8),
  clb("Pumas UNAM", "PUM", 0xFF0A2F6B, 0xFFC8A04A, 0, 0xFF0A2F6B, 0xFF0A2F6B),
  clb("Real Madrid", "RMA", 0xFFFAFAFA, 0xFFFAFAFA, 0, 0xFFFAFAFA, 0xFFFAFAFA),
  clb("FC Barcelona", "BAR", 0xFFA50044, 0xFF004D98, 1, 0xFF004D98, 0xFFA50044),
  clb("Atlético de Madrid", "ATM", 0xFFCB3524, 0xFFFAFAFA, 1, 0xFF1B2A6B, 0xFFCB3524),
  clb("Manchester City", "MCI", 0xFF6CABDD, 0xFF6CABDD, 0, 0xFFFAFAFA, 0xFF6CABDD),
  clb("Manchester United", "MUN", 0xFFDA291C, 0xFFDA291C, 0, 0xFFFAFAFA, 0xFF151515),
  clb("Liverpool", "LIV", 0xFFC8102E, 0xFFC8102E, 0, 0xFFC8102E, 0xFFC8102E),
  clb("Chelsea", "CHE", 0xFF034694, 0xFF034694, 0, 0xFF034694, 0xFFFAFAFA),
  clb("Arsenal", "ARS", 0xFFEF0107, 0xFFFAFAFA, 6, 0xFFFAFAFA, 0xFFEF0107),
  clb("Bayern Múnich", "BAY", 0xFFDC052D, 0xFFFAFAFA, 0, 0xFFFAFAFA, 0xFFDC052D),
  clb("Juventus", "JUV", 0xFFFAFAFA, 0xFF151515, 1, 0xFF151515, 0xFFFAFAFA),
  clb("AC Milan", "MIL", 0xFFC8102E, 0xFF151515, 1, 0xFF151515, 0xFFC8102E),
  clb("Inter de Milán", "INT", 0xFF0A3D91, 0xFF151515, 1, 0xFF151515, 0xFF0A3D91),
  clb("Paris Saint-Germain", "PSG", 0xFF0A1F4F, 0xFFC8102E, 0, 0xFF0A1F4F, 0xFF0A1F4F),
  clb("Inter Miami", "MIA", 0xFFF7B5CD, 0xFF151515, 0, 0xFF151515, 0xFFF7B5CD)
)

internal fun kitAt(i: Int): SKit = ALL_KITS[i.coerceIn(0, ALL_KITS.size - 1)]

/** Si las dos camisetas se confunden, el rival usa la alternativa. */
internal fun resolveKits(a: Int, b: Int): Pair<SKit, SKit> {
  val ka = kitAt(a); var kb = kitAt(b)
  val d = abs(ka.shirt.red - kb.shirt.red) + abs(ka.shirt.green - kb.shirt.green) + abs(ka.shirt.blue - kb.shirt.blue)
  if (d < 0.55f || a == b) kb = kb.alt()
  return ka to kb
}
