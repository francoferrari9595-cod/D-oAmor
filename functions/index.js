const { onDocumentWritten, onDocumentCreated } = require('firebase-functions/v2/firestore');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { defineSecret } = require('firebase-functions/params');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');

initializeApp();
const db = getFirestore();

async function sendToPartner(coupleId, senderUid, data) {
  const coupleSnap = await db.collection('couples').doc(coupleId).get();
  if (!coupleSnap.exists) return;
  const members = coupleSnap.get('memberUids');
  if (!Array.isArray(members) || members.length !== 2 || !members.includes(senderUid)) return;
  const receiverUid = members.find(uid => uid !== senderUid);
  if (!receiverUid) return;
  const userSnap = await db.collection('users').doc(receiverUid).get();
  const token = userSnap.get('fcmToken');
  if (typeof token !== 'string' || token.length < 20) return;
  try {
    await getMessaging().send({
      token,
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v ?? '')])),
      android: { priority: 'high' }
    });
  } catch (error) {
    if (error && (error.code === 'messaging/registration-token-not-registered' || error.code === 'messaging/invalid-registration-token')) {
      await userSnap.ref.set({ fcmToken: '' }, { merge: true });
    } else {
      console.error('FCM send failed', error?.code || 'unknown');
    }
  }
}

// A call document must be created by an authenticated couple member and contain callerUid.
exports.notifyIncomingCall = onDocumentCreated('couples/{coupleId}/calls/{callId}', async event => {
  const call = event.data?.data();
  if (!call || call.status !== 'ringing' || typeof call.callerUid !== 'string') return;
  await sendToPartner(event.params.coupleId, call.callerUid, {
    action: 'incoming_call', callId: event.params.callId, video: call.video === true ? 'true' : 'false'
  });
});

// A high-priority push wakes the partner device; Firestore listeners alone are not reliable after process death.
exports.notifyPartnerBuzz = onDocumentWritten('couples/{coupleId}', async event => {
  const before = event.data?.before?.data();
  const after = event.data?.after?.data();
  if (!after) return;
  const previousSenderUid = before?.activeBuzzSenderUid;
  const senderUid = after.activeBuzzSenderUid;
  if (previousSenderUid === senderUid) return;

  // También enviamos el fin del zumbido; de lo contrario, un teléfono con la app
  // en segundo plano podría seguir sonando después de que el emisor suelte el botón.
  if (typeof previousSenderUid === 'string' && previousSenderUid &&
      (typeof senderUid !== 'string' || !senderUid)) {
    await sendToPartner(event.params.coupleId, previousSenderUid, { action: 'buzz_stop' });
    return;
  }
  if (typeof senderUid !== 'string' || !senderUid) return;
  const sender = await db.collection('users').doc(senderUid).get();
  await sendToPartner(event.params.coupleId, senderUid, {
    action: 'buzz', senderName: sender.get('displayName') || 'Tu amor', soundType: 'PHONE_RING'
  });
});

// ───────────────────────── TRIVIAS: verificación de respuestas con IA + web ─────────────────────────
// La clave de la API vive SOLO aquí (secreto de Firebase): firebase functions:secrets:set ANTHROPIC_API_KEY
const ANTHROPIC_API_KEY = defineSecret('ANTHROPIC_API_KEY');
const TRIVIA_MODEL = process.env.TRIVIA_MODEL || 'claude-sonnet-4-6';

const TRIVIA_SYSTEM = `Eres el juez de un concurso de preguntas y respuestas en español.
Recibes la pregunta, la respuesta de referencia del banco de preguntas y la respuesta del jugador.
Decide si la respuesta del jugador es CORRECTA, INCORRECTA o AMBIGUA:
- CORRECTA: equivale a la respuesta de referencia (sinónimo, variación ortográfica, forma abreviada habitual, número en cifras o en letras) o es otra respuesta igualmente válida y verificable.
- INCORRECTA: es otra cosa distinta o contradice los hechos.
- AMBIGUA: no puedes decidirlo con seguridad (respuesta incompleta, varias interpretaciones, fuentes que se contradicen).
Usa la búsqueda web SOLO cuando necesites comprobar un dato; prefiere fuentes oficiales, enciclopedias y diccionarios.
No afirmes haber consultado fuentes que no consultaste. Responde ÚNICAMENTE con un JSON: {"verdict":"correct|incorrect|ambiguous","explanation":"una o dos frases"}`;

exports.verifyTriviaAnswer = onCall({ secrets: [ANTHROPIC_API_KEY], timeoutSeconds: 60, memory: '256MiB', region: 'us-central1', maxInstances: 10 }, async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Inicia sesión para verificar respuestas.');
  const d = request.data || {};
  const clip = (v, n) => (typeof v === 'string' ? v.trim().slice(0, n) : '');
  const question = clip(d.question, 400), expected = clip(d.expected, 200), given = clip(d.given, 200), category = clip(d.category, 40);
  const aliases = Array.isArray(d.aliases) ? d.aliases.slice(0, 8).map(a => clip(a, 100)).filter(Boolean) : [];
  const second = d.second === true;
  if (!question || !given) throw new HttpsError('invalid-argument', 'Faltan la pregunta o la respuesta.');

  const userMsg = JSON.stringify({ categoria: category, pregunta: question, respuesta_de_referencia: expected, otras_formas_aceptadas: aliases, respuesta_del_jugador: given,
    instruccion_extra: second ? 'Segunda evaluación: contrasta con al menos una fuente confiable antes de decidir y sé estricto con la ambigüedad.' : '' });
  let res;
  try {
    res = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-api-key': ANTHROPIC_API_KEY.value(), 'anthropic-version': '2023-06-01' },
      body: JSON.stringify({
        model: TRIVIA_MODEL, max_tokens: 800, system: TRIVIA_SYSTEM,
        tools: [{ type: 'web_search_20250305', name: 'web_search', max_uses: second ? 4 : 2 }],
        messages: [{ role: 'user', content: userMsg }]
      })
    });
  } catch (e) { console.error('Anthropic fetch failed', e?.message); throw new HttpsError('unavailable', 'No se pudo contactar al verificador.'); }
  if (!res.ok) { console.error('Anthropic HTTP', res.status); throw new HttpsError('unavailable', 'El verificador no está disponible ahora.'); }
  const body = await res.json();
  const blocks = Array.isArray(body.content) ? body.content : [];
  // «usedWeb» solo es true si la respuesta trae de verdad búsquedas web ejecutadas; nunca se supone.
  const searches = blocks.filter(b => b.type === 'server_tool_use' && b.name === 'web_search');
  const results = blocks.filter(b => b.type === 'web_search_tool_result' && Array.isArray(b.content));
  const sources = [];
  for (const r of results) for (const item of r.content) if (item && item.url && !sources.includes(item.url)) sources.push(item.url);
  const usedWeb = searches.length > 0 && sources.length > 0;
  const text = blocks.filter(b => b.type === 'text').map(b => b.text).join('\n');
  let verdict = 'ambiguous', explanation = 'No se pudo interpretar la evaluación.';
  const m = text.match(/\{[\s\S]*\}/);
  if (m) {
    try {
      const j = JSON.parse(m[0]);
      if (['correct', 'incorrect', 'ambiguous'].includes(j.verdict)) verdict = j.verdict;
      if (typeof j.explanation === 'string') explanation = j.explanation.slice(0, 400);
    } catch (_) { /* queda ambigua */ }
  }
  return { verdict, explanation, usedWeb, sources: sources.slice(0, 5), model: TRIVIA_MODEL };
});
