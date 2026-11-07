const { onDocumentWritten, onDocumentCreated } = require('firebase-functions/v2/firestore');
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
