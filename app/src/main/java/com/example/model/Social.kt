package com.example.model

import android.content.Context
import androidx.compose.ui.graphics.Color

// ─────────────── Chat de sala, efectos, tienda de ítems y billetera local ───────────────

/** Mensaje del chat de la sala. `kind`: text, gift, fx, join, call. */
data class RoomMsg(
  val id: String, val uid: String, val name: String, val kind: String, val text: String,
  val gift: String, val to: String, val toName: String, val fx: String, val bubble: String, val nameCol: String, val t: Long
)

/** Efecto de pantalla que se reproduce para todos los de la sala. */
data class FxDef(val id: String, val emoji: String, val name: String, val price: Long)

val FX_LIST = listOf(
  FxDef("confetti", "🎉", "Confeti", 0),
  FxDef("hearts", "❤️", "Lluvia de corazones", 0),
  FxDef("stars", "⭐", "Estrellas", 0),
  FxDef("snow", "❄️", "Nieve", 0),
  FxDef("fire", "🔥", "Llamas", 20),
  FxDef("fireworks", "🎆", "Fuegos artificiales", 30),
  FxDef("lightning", "⚡", "Tormenta eléctrica", 30),
  FxDef("galaxy", "🌌", "Galaxia", 40),
  FxDef("sakura", "🌸", "Lluvia de sakuras", 25),
  FxDef("rain", "🌧️", "Lluvia neón", 25),
  FxDef("bubbles", "🫧", "Burbujas", 20),
  FxDef("meteor", "☄️", "Lluvia de meteoros", 35),
  FxDef("aurora", "🌠", "Aurora boreal", 40),
  FxDef("coins", "🪙", "Lluvia de monedas", 30),
  FxDef("disco", "🪩", "Pista de baile", 45),
  FxDef("dragon", "🐉", "Dragón", 60),
  FxDef("portal", "🌀", "Portal dimensional", 55)
)

/** Categorías de la tienda de ítems. */
object Cat {
  const val BALL = "ball"      // bolas de bowling (sistema anterior)
  const val CUE = "cue"        // tacos de Pool
  const val FELT = "felt"      // paños de Pool
  const val CAR = "car"        // autos de Carreras
  const val BUBBLE = "bubble"  // burbujas del chat
  const val NAME = "name"      // color del nombre
  const val ENTRY = "entry"    // efecto al entrar a la sala
}

data class ShopItem(val id: String, val cat: String, val name: String, val emoji: String, val price: Long, val desc: String, val c1: Color, val c2: Color)

val SHOP_ITEMS: List<ShopItem> = listOf(
  // Tacos de Pool
  ShopItem("cue_classic", Cat.CUE, "Taco clásico", "🏒", 0, "Madera clara y empuñadura oscura", Color(0xFFE6C48A), Color(0xFF3B2412)),
  ShopItem("cue_neon", Cat.CUE, "Taco neón", "💠", 150, "Brilla en cian", Color(0xFF7DF9FF), Color(0xFF0B5C7A)),
  ShopItem("cue_fire", Cat.CUE, "Taco de fuego", "🔥", 250, "Naranja ardiente", Color(0xFFFFB347), Color(0xFF8A1C00)),
  ShopItem("cue_gold", Cat.CUE, "Taco de oro", "🥇", 350, "Para campeones", Color(0xFFFFE08A), Color(0xFF8A6A00)),
  ShopItem("cue_pink", Cat.CUE, "Taco rosa", "💗", 200, "Rosa chicle", Color(0xFFFF9AD5), Color(0xFF7A1B4A)),
  ShopItem("cue_ice", Cat.CUE, "Taco de hielo", "🧊", 280, "Azul cristal", Color(0xFFBFEFFF), Color(0xFF2979FF)),
  ShopItem("cue_toxic", Cat.CUE, "Taco tóxico", "☣️", 260, "Verde radiactivo", Color(0xFF9CFF7A), Color(0xFF0B6B3A)),
  ShopItem("cue_black", Cat.CUE, "Taco negro mate", "🖤", 300, "Sobrio y letal", Color(0xFF55555F), Color(0xFF0C0C12)),
  // Paños de Pool
  ShopItem("felt_green", Cat.FELT, "Paño verde clásico", "🟩", 0, "El de siempre", Color(0xFF1FA85A), Color(0xFF0C5E34)),
  ShopItem("felt_blue", Cat.FELT, "Paño azul real", "🟦", 200, "Torneo profesional", Color(0xFF2A7BDB), Color(0xFF0B2F6B)),
  ShopItem("felt_red", Cat.FELT, "Paño rojo casino", "🟥", 200, "Noche de casino", Color(0xFFD83A4A), Color(0xFF6B0F1C)),
  ShopItem("felt_violet", Cat.FELT, "Paño galaxia", "🟪", 250, "Violeta profundo", Color(0xFF8E5CF0), Color(0xFF2E1470)),
  ShopItem("felt_black", Cat.FELT, "Paño élite negro", "⬛", 300, "Sobrio y elegante", Color(0xFF3A3A44), Color(0xFF0C0C12)),
  ShopItem("felt_teal", Cat.FELT, "Paño turquesa", "🟦", 220, "Mar caribe", Color(0xFF1FB8A8), Color(0xFF0A5E5A)),
  ShopItem("felt_pink", Cat.FELT, "Paño rosa neón", "🩷", 250, "Noche de discoteca", Color(0xFFE0559A), Color(0xFF6B1B44)),
  ShopItem("felt_gold", Cat.FELT, "Paño dorado VIP", "🟨", 400, "Solo para VIP", Color(0xFFD9B13A), Color(0xFF6B4A00)),
  // Autos de Carreras
  ShopItem("car_cyan", Cat.CAR, "Auto cian", "🏎️", 0, "El de salida", Color(0xFF00E5FF), Color(0xFF006680)),
  ShopItem("car_pink", Cat.CAR, "Auto rosa neón", "🩷", 120, "Se ve desde lejos", Color(0xFFFF4D8D), Color(0xFF7A1B3A)),
  ShopItem("car_lime", Cat.CAR, "Auto lima", "🟢", 120, "Verde fosforescente", Color(0xFF7CFF6B), Color(0xFF1B6B1B)),
  ShopItem("car_gold", Cat.CAR, "Auto dorado", "🏆", 250, "Edición campeón", Color(0xFFFFD60A), Color(0xFF8A6A00)),
  ShopItem("car_violet", Cat.CAR, "Auto violeta", "🔮", 150, "Misterioso", Color(0xFFB388FF), Color(0xFF3D1E8A)),
  ShopItem("car_orange", Cat.CAR, "Auto naranja", "🧡", 150, "Color de fuego", Color(0xFFFF9500), Color(0xFF8A3B00)),
  ShopItem("car_white", Cat.CAR, "Auto blanco perla", "⚪", 200, "Elegante", Color(0xFFF5F5F5), Color(0xFF9E9E9E)),
  ShopItem("car_red", Cat.CAR, "Auto rojo fuego", "🔴", 150, "Clásico de carreras", Color(0xFFFF3B3B), Color(0xFF7A0B0B)),
  ShopItem("car_blue", Cat.CAR, "Auto azul eléctrico", "🔵", 150, "Rápido y frío", Color(0xFF3D7BFF), Color(0xFF0B2A6B)),
  // Burbujas del chat
  ShopItem("bub_basic", Cat.BUBBLE, "Burbuja estándar", "💬", 0, "Sencilla", Color(0xFF4A3A7A), Color(0xFF2A1F4A)),
  ShopItem("bub_neon", Cat.BUBBLE, "Burbuja neón", "💙", 100, "Cian y violeta", Color(0xFF00C8FF), Color(0xFF7C4DFF)),
  ShopItem("bub_gold", Cat.BUBBLE, "Burbuja dorada", "✨", 200, "Brillo de oro", Color(0xFFFFC857), Color(0xFFB8741A)),
  ShopItem("bub_fire", Cat.BUBBLE, "Burbuja de fuego", "🔥", 200, "Rojo y naranja", Color(0xFFFF5E3A), Color(0xFFB3123A)),
  ShopItem("bub_galaxy", Cat.BUBBLE, "Burbuja galaxia", "🌌", 250, "Rosa y violeta", Color(0xFFFF4D8D), Color(0xFF5B2BD6)),
  ShopItem("bub_sakura", Cat.BUBBLE, "Burbuja sakura", "🌸", 180, "Rosa pastel", Color(0xFFFF9AC8), Color(0xFFB8336A)),
  ShopItem("bub_toxic", Cat.BUBBLE, "Burbuja tóxica", "☣️", 180, "Verde ácido", Color(0xFF7CFF6B), Color(0xFF0B6B3A)),
  ShopItem("bub_ice", Cat.BUBBLE, "Burbuja hielo", "🧊", 180, "Azul glacial", Color(0xFFB3E5FF), Color(0xFF2979FF)),
  ShopItem("bub_black", Cat.BUBBLE, "Burbuja elite", "🖤", 300, "Negro y oro", Color(0xFF2A2A33), Color(0xFFB8741A)),
  // Color del nombre
  ShopItem("nm_white", Cat.NAME, "Nombre blanco", "⚪", 0, "Normal", Color.White, Color.White),
  ShopItem("nm_gold", Cat.NAME, "Nombre dorado", "🟡", 80, "Dorado brillante", Color(0xFFFFD166), Color(0xFFFFB300)),
  ShopItem("nm_cyan", Cat.NAME, "Nombre cian", "🔵", 80, "Cian eléctrico", Color(0xFF7DF9FF), Color(0xFF00B8D4)),
  ShopItem("nm_pink", Cat.NAME, "Nombre rosa", "🩷", 80, "Rosa neón", Color(0xFFFF9AD5), Color(0xFFFF2E93)),
  ShopItem("nm_rainbow", Cat.NAME, "Nombre arcoíris", "🌈", 300, "Todos los colores", Color(0xFFFF5E5E), Color(0xFF7C4DFF)),
  ShopItem("nm_green", Cat.NAME, "Nombre lima", "🟢", 80, "Verde lima", Color(0xFF9CFF7A), Color(0xFF2EBF4A)),
  ShopItem("nm_fire", Cat.NAME, "Nombre de fuego", "🔥", 150, "Naranja ardiente", Color(0xFFFFB347), Color(0xFFFF3B1F)),
  ShopItem("nm_violet", Cat.NAME, "Nombre violeta", "🟣", 100, "Violeta eléctrico", Color(0xFFD1A8FF), Color(0xFF7C4DFF)),
  // Efectos de entrada
  ShopItem("en_none", Cat.ENTRY, "Entrada normal", "🚪", 0, "Solo aparece tu nombre", Color(0xFF9E9E9E), Color(0xFF424242)),
  ShopItem("en_confetti", Cat.ENTRY, "Entrada con confeti", "🎉", 100, "Lluvia de confeti", Color(0xFFFFD60A), Color(0xFFFF4D8D)),
  ShopItem("en_fire", Cat.ENTRY, "Entrada en llamas", "🔥", 200, "Llegás ardiendo", Color(0xFFFF9500), Color(0xFFB3123A)),
  ShopItem("en_frost", Cat.ENTRY, "Entrada de escarcha", "❄️", 200, "Nevada helada", Color(0xFFB3E5FF), Color(0xFF2979FF)),
  ShopItem("en_lightning", Cat.ENTRY, "Entrada con rayo", "⚡", 250, "Un trueno anuncia tu llegada", Color(0xFFFFF176), Color(0xFF5B2BD6)),
  ShopItem("en_galaxy", Cat.ENTRY, "Entrada galáctica", "🌌", 350, "Remolino de estrellas", Color(0xFFB388FF), Color(0xFF00E5FF)),
  ShopItem("en_sakura", Cat.ENTRY, "Entrada de sakuras", "🌸", 150, "Pétalos de cerezo", Color(0xFFFFB7D5), Color(0xFFB8336A)),
  ShopItem("en_rain", Cat.ENTRY, "Entrada con lluvia neón", "🌧️", 200, "Lluvia cian y violeta", Color(0xFF37E6FF), Color(0xFF5B2BD6)),
  ShopItem("en_bubbles", Cat.ENTRY, "Entrada de burbujas", "🫧", 120, "Burbujas brillantes", Color(0xFF7DF9FF), Color(0xFF2979FF)),
  ShopItem("en_meteor", Cat.ENTRY, "Entrada de meteoros", "☄️", 300, "Una lluvia de estrellas fugaces", Color(0xFFFFD166), Color(0xFF8A1C00)),
  ShopItem("en_aurora", Cat.ENTRY, "Entrada aurora boreal", "🌠", 350, "Luces del norte", Color(0xFF00E5A0), Color(0xFF5B2BD6)),
  ShopItem("en_coins", Cat.ENTRY, "Entrada millonaria", "💰", 300, "Llueven monedas de oro", Color(0xFFFFD60A), Color(0xFF8A6A00)),
  ShopItem("en_disco", Cat.ENTRY, "Entrada de discoteca", "🪩", 400, "Luces y bola de espejos", Color(0xFFFF4D8D), Color(0xFF7C4DFF)),
  ShopItem("en_dragon", Cat.ENTRY, "Entrada del dragón", "🐉", 500, "Un dragón cruza la pantalla", Color(0xFFFF5E3A), Color(0xFF3B0A0A)),
  ShopItem("en_royal", Cat.ENTRY, "Entrada real", "👑", 400, "Corona y destellos dorados", Color(0xFFFFE08A), Color(0xFFB8741A)),
  // ── 30 efectos de entrada nuevos ──
  ShopItem("en_hearts", Cat.ENTRY, "Entrada de corazones", "💖", 120, "Una lluvia de corazones", Color(0xFFFF4D8D), Color(0xFF7A1B3A)),
  ShopItem("en_stars", Cat.ENTRY, "Entrada estelar", "⭐", 150, "Estrellas que brillan para ti", Color(0xFFFFE066), Color(0xFF5B2BD6)),
  ShopItem("en_neon", Cat.ENTRY, "Entrada neón", "💜", 220, "Luces de neón al entrar", Color(0xFFB388FF), Color(0xFF00E5FF)),
  ShopItem("en_storm", Cat.ENTRY, "Entrada con tormenta", "⛈️", 260, "Nubes, lluvia y relámpagos", Color(0xFF90A4AE), Color(0xFF263238)),
  ShopItem("en_tornado", Cat.ENTRY, "Entrada tornado", "🌪️", 280, "Un remolino te trae a la sala", Color(0xFFB0BEC5), Color(0xFF37474F)),
  ShopItem("en_volcano", Cat.ENTRY, "Entrada volcánica", "🌋", 330, "Lava y brasas al aparecer", Color(0xFFFF7043), Color(0xFF3B0A0A)),
  ShopItem("en_tsunami", Cat.ENTRY, "Entrada tsunami", "🌊", 300, "Una ola gigante te recibe", Color(0xFF29B6F6), Color(0xFF0B2A6B)),
  ShopItem("en_rainbow", Cat.ENTRY, "Entrada arcoíris", "🌈", 250, "Un arcoíris cruza la pantalla", Color(0xFF4FC3F7), Color(0xFFFF4D8D)),
  ShopItem("en_butterflies", Cat.ENTRY, "Entrada de mariposas", "🦋", 180, "Mariposas de mil colores", Color(0xFF4FC3F7), Color(0xFF9C27B0)),
  ShopItem("en_fireflies", Cat.ENTRY, "Entrada de luciérnagas", "✨", 200, "Luces suaves en la noche", Color(0xFFD4FF5A), Color(0xFF0B3B2E)),
  ShopItem("en_leaves", Cat.ENTRY, "Entrada de otoño", "🍂", 140, "Hojas secas que bailan", Color(0xFFFF8A3D), Color(0xFF6B2A00)),
  ShopItem("en_petals", Cat.ENTRY, "Entrada de rosas", "🌹", 200, "Pétalos de rosa por todos lados", Color(0xFFFF2E63), Color(0xFF7A0B3A)),
  ShopItem("en_pizza", Cat.ENTRY, "Entrada pizzera", "🍕", 160, "Llueven porciones de pizza", Color(0xFFFFB74D), Color(0xFF8A2B0B)),
  ShopItem("en_ghosts", Cat.ENTRY, "Entrada fantasmal", "👻", 240, "Fantasmitas te acompañan", Color(0xFFB388FF), Color(0xFF1A0B4E)),
  ShopItem("en_skulls", Cat.ENTRY, "Entrada pirata", "☠️", 260, "Calaveras y tesoros", Color(0xFFCFD8DC), Color(0xFF263238)),
  ShopItem("en_rockets", Cat.ENTRY, "Entrada de cohetes", "🚀", 320, "Cohetes rumbo a la sala", Color(0xFFFF7043), Color(0xFF1A0B4E)),
  ShopItem("en_ufo", Cat.ENTRY, "Entrada alienígena", "🛸", 350, "Un ovni te deja en la sala", Color(0xFF69F0AE), Color(0xFF1A0B4E)),
  ShopItem("en_money", Cat.ENTRY, "Entrada de billetes", "💸", 340, "Llueven billetes", Color(0xFF66BB6A), Color(0xFF1B5E20)),
  ShopItem("en_diamonds", Cat.ENTRY, "Entrada de diamantes", "💎", 380, "Diamantes brillantes", Color(0xFF7DF9FF), Color(0xFF0B3A8A)),
  ShopItem("en_crowns", Cat.ENTRY, "Entrada de reyes", "👑", 420, "Coronas por doquier", Color(0xFFFFD166), Color(0xFF7A3B00)),
  ShopItem("en_music", Cat.ENTRY, "Entrada musical", "🎶", 170, "Notas musicales al viento", Color(0xFFB388FF), Color(0xFFFF4D8D)),
  ShopItem("en_soccer", Cat.ENTRY, "Entrada futbolera", "⚽", 190, "Pelotas y gritos de gol", Color(0xFFA5D6A7), Color(0xFF1B5E20)),
  ShopItem("en_emoji", Cat.ENTRY, "Entrada de emojis", "😎", 130, "Una fiesta de caritas", Color(0xFFFFD54F), Color(0xFFFF4D8D)),
  ShopItem("en_balloons", Cat.ENTRY, "Entrada de globos", "🎈", 150, "Globos de colores suben", Color(0xFFFF6B8B), Color(0xFF5B1B7A)),
  ShopItem("en_cherry", Cat.ENTRY, "Entrada de cerezas", "🍒", 140, "Cerezas jugosas", Color(0xFFE53935), Color(0xFF7A0B1A)),
  ShopItem("en_snowfall", Cat.ENTRY, "Entrada de copos", "🌨️", 170, "Una nevada suave", Color(0xFFB3E5FF), Color(0xFF2A4A8A)),
  ShopItem("en_sunrise", Cat.ENTRY, "Entrada amanecer", "🌅", 290, "El sol sale por ti", Color(0xFFFFB347), Color(0xFFFF4D8D)),
  ShopItem("en_magic", Cat.ENTRY, "Entrada mágica", "🪄", 310, "Varita y polvo de hadas", Color(0xFFB388FF), Color(0xFFFFD60A)),
  ShopItem("en_glitch", Cat.ENTRY, "Entrada glitch", "👾", 270, "Interferencias digitales", Color(0xFF00E5FF), Color(0xFFFF2E93)),
  ShopItem("en_legend", Cat.ENTRY, "Entrada legendaria", "🏆", 600, "Fuego, rayos y oro a la vez", Color(0xFFFFD60A), Color(0xFFFF4D8D))
)

fun shopItem(id: String): ShopItem? = SHOP_ITEMS.firstOrNull { it.id == id }
fun shopItemsOf(cat: String): List<ShopItem> = SHOP_ITEMS.filter { it.cat == cat }

/** Qué efecto de pantalla corresponde a una entrada comprada. */
fun entryFx(entryId: String): String = when (entryId) {
  "en_confetti" -> "confetti"
  "en_fire" -> "fire"
  "en_frost" -> "snow"
  "en_lightning" -> "lightning"
  "en_galaxy" -> "galaxy"
  "en_sakura" -> "sakura"
  "en_rain" -> "rain"
  "en_bubbles" -> "bubbles"
  "en_meteor" -> "meteor"
  "en_aurora" -> "aurora"
  "en_coins" -> "coins"
  "en_disco" -> "disco"
  "en_dragon" -> "dragon"
  "en_royal" -> "royal"
  else -> if (entryId.startsWith("en_") && entryId != "en_none") entryId.removePrefix("en_") else ""
}

/** Billetera local de este teléfono y compras guardadas (las salas no tienen billetera compartida). */
object LocalWallet {
  private const val PREFS = "kombo_wallet"
  const val START_COINS = 1000L
  const val DAILY_BONUS = 200L
  private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun coins(ctx: Context): Long = p(ctx).getLong("coins", START_COINS)
  fun add(ctx: Context, delta: Long): Long {
    val n = (coins(ctx) + delta).coerceAtLeast(0L)
    p(ctx).edit().putLong("coins", n).apply()
    return n
  }

  private fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
  fun dailyAvailable(ctx: Context): Boolean = p(ctx).getString("daily", "") != today()
  fun claimDaily(ctx: Context): Long? {
    if (!dailyAvailable(ctx)) return null
    p(ctx).edit().putString("daily", today()).apply()
    return add(ctx, DAILY_BONUS)
  }

  // Ítems comprados / equipados
  fun owned(ctx: Context): Set<String> {
    val saved = p(ctx).getStringSet("owned_items", emptySet()).orEmpty()
    return saved + SHOP_ITEMS.filter { it.price == 0L }.map { it.id }
  }
  fun own(ctx: Context, id: String) {
    val now = p(ctx).getStringSet("owned_items", emptySet()).orEmpty().toMutableSet(); now.add(id)
    p(ctx).edit().putStringSet("owned_items", now).apply()
  }
  fun equipped(ctx: Context, cat: String): String {
    val def = SHOP_ITEMS.first { it.cat == cat }.id
    val id = p(ctx).getString("eq_$cat", def) ?: def
    return if (id in owned(ctx)) id else def
  }
  fun equip(ctx: Context, cat: String, id: String) {
    if (id in owned(ctx)) p(ctx).edit().putString("eq_$cat", id).apply()
  }
  fun equippedItem(ctx: Context, cat: String): ShopItem = shopItem(equipped(ctx, cat)) ?: SHOP_ITEMS.first { it.cat == cat }
}

/** Preferencias de la sala guardadas en este teléfono. */
object RoomPrefs {
  private fun p(ctx: Context) = ctx.getSharedPreferences("kombo_room_prefs", Context.MODE_PRIVATE)
  fun fxEnabled(ctx: Context) = p(ctx).getBoolean("fx", true)
  fun setFx(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("fx", v).apply()
  fun giftBanners(ctx: Context) = p(ctx).getBoolean("gifts", true)
  fun setGiftBanners(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("gifts", v).apply()
  fun joinFx(ctx: Context) = p(ctx).getBoolean("joinfx", true)
  fun setJoinFx(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("joinfx", v).apply()
  fun announceJoin(ctx: Context) = p(ctx).getBoolean("announce", true)
  fun setAnnounceJoin(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("announce", v).apply()
  fun textScale(ctx: Context) = p(ctx).getFloat("textscale", 1f)
  fun setTextScale(ctx: Context, v: Float) = p(ctx).edit().putFloat("textscale", v).apply()
}
