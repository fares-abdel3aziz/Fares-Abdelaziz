const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-2f44fbeb-21de-4f57-8d28-627f8dbbc3f5";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const OWNER_UID = "salon_owner_uid";
const OWNER_EMAIL = "fareselbialy1941@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildValidAppointment(uid, serverTimestamp) {
  return {
    userId: uid,
    customerName: "أحمد محمود",
    customerPhone: "01029898309",
    barberId: "mody",
    barberName: "مودي",
    packageId: "vip_royal",
    packageTitle: "باكيدج أبو العطا الملكي",
    packageServices: "قصة شعر + تحديد دقن + استشوار + تنظيف بشرة وماسك فحم",
    selectedHaircut: "سكين فيد مع كويف عصري (Mid Skin Fade & Quiff)",
    appointmentDate: "اليوم (الثلاثاء) ١٧ نوفمبر",
    appointmentTime: "11:30 مساءً",
    appointmentEpochMillis: 1795199400000,
    notes: "تحديد تدريج عالي",
    status: "confirmed",
    reminderScheduled: true,
    createdAt: serverTimestamp,
    updatedAt: serverTimestamp,
  };
}

test("Unauthenticated user cannot list or read appointments or salon_bookings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("appointments").get());
  await assertFails(unauthDb.collection("salon_bookings").get());
});

test("Authenticated customer can batch-create their appointment in users/{uid}/appointments and salon_bookings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = new Date(Date.now() - 1000);
  const payload = buildValidAppointment(ALICE_UID, now);

  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("users").doc(ALICE_UID).collection("appointments").doc("appt_1"), payload);
  batch.set(aliceDb.collection("salon_bookings").doc("appt_1"), payload);
  await assertSucceeds(batch.commit());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("appointments").get()
  );
});

test("Cross-user isolation: Regular customer Bob CANNOT list salon_bookings or read Alice's booking", async () => {
  const now = new Date(Date.now() - 2000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(DATABASE_ID);
    await db.collection("users").doc(ALICE_UID).collection("appointments").doc("appt_1").set(buildValidAppointment(ALICE_UID, now));
    await db.collection("salon_bookings").doc("appt_1").set(buildValidAppointment(ALICE_UID, now));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore(DATABASE_ID);

  // Regular customer CANNOT list all salon_bookings (Today's Bookings dashboard is strictly owner-only!)
  await assertFails(bobDb.collection("salon_bookings").get());
  await assertFails(bobDb.collection("salon_bookings").doc("appt_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("appointments").doc("appt_1").get());
});

test("Email Spoofing Guard: unverified owner email CANNOT access salon_bookings", async () => {
  const now = new Date(Date.now() - 2000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore(DATABASE_ID).collection("salon_bookings").doc("appt_1").set(buildValidAppointment(ALICE_UID, now));
  });

  const spoofDb = testEnv.authenticatedContext("attacker_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore(DATABASE_ID);

  await assertFails(spoofDb.collection("salon_bookings").get());
  await assertFails(spoofDb.collection("salon_bookings").doc("appt_1").get());
});

test("Verified Salon Owner (email or +201029898309 phone) CAN list all salon_bookings and update booking status", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(DATABASE_ID);
    await db.collection("users").doc(ALICE_UID).collection("appointments").doc("appt_a").set(buildValidAppointment(ALICE_UID, created));
    await db.collection("salon_bookings").doc("appt_a").set(buildValidAppointment(ALICE_UID, created));
    await db.collection("users").doc(BOB_UID).collection("appointments").doc("appt_b").set(buildValidAppointment(BOB_UID, created));
    await db.collection("salon_bookings").doc("appt_b").set(buildValidAppointment(BOB_UID, created));
  });

  // 1. Verified Owner via Google Email
  const ownerEmailDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore(DATABASE_ID);

  const snapshot = await assertSucceeds(ownerEmailDb.collection("salon_bookings").get());
  if (snapshot.size !== 2) {
    throw new Error(`Expected Salon Owner to see 2 bookings, got ${snapshot.size}`);
  }

  // Owner marks Alice's booking as completed in both collections
  const updatePayload = {
    status: "completed",
    reminderScheduled: false,
    updatedAt: new Date(Date.now() - 1000),
  };
  await assertSucceeds(ownerEmailDb.collection("salon_bookings").doc("appt_a").update(updatePayload));
  await assertSucceeds(
    ownerEmailDb.collection("users").doc(ALICE_UID).collection("appointments").doc("appt_a").update(updatePayload)
  );

  // Owner CANNOT forge customer rating or mutate customerId
  await assertFails(
    ownerEmailDb.collection("salon_bookings").doc("appt_a").update({
      rating: 5,
      feedbackComment: "تقييم مزيف",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 2. Verified Owner via Firebase Phone Auth (+201029898309)
  const ownerPhoneDb = testEnv.authenticatedContext("owner_phone_uid", {
    phone_number: "+201029898309",
  }).firestore(DATABASE_ID);

  await assertSucceeds(ownerPhoneDb.collection("salon_bookings").get());
});

test("Shadow update & ghost field injection is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = new Date(Date.now() - 1000);
  const malicious = {
    ...buildValidAppointment(ALICE_UID, now),
    isAdmin: true,
  };
  await assertFails(
    aliceDb.collection("salon_bookings").doc("appt_ghost").set(malicious)
  );
});

test("Completed appointment allows customer 1-5 star feedback in both collections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const created = new Date(Date.now() - 10000);
  const userDoc = aliceDb.collection("users").doc(ALICE_UID).collection("appointments").doc("appt_fb");
  const salonDoc = aliceDb.collection("salon_bookings").doc("appt_fb");

  await assertSucceeds(userDoc.set(buildValidAppointment(ALICE_UID, created)));
  await assertSucceeds(salonDoc.set(buildValidAppointment(ALICE_UID, created)));

  const completeUpdate = {
    status: "completed",
    reminderScheduled: false,
    updatedAt: new Date(Date.now() - 5000),
  };
  await assertSucceeds(userDoc.update(completeUpdate));
  await assertSucceeds(salonDoc.update(completeUpdate));

  const feedbackUpdate = {
    rating: 5,
    feedbackComment: "تسلم إيد كابتن مودي، تدريج ممتاز وخدمة راقية جداً",
    updatedAt: new Date(Date.now() - 1000),
  };
  await assertSucceeds(userDoc.update(feedbackUpdate));
  await assertSucceeds(salonDoc.update(feedbackUpdate));
});
