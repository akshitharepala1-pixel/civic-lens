const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_citizen_1";
const BOB_UID = "bob_citizen_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validPrivateReport(uid, overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    userId: uid,
    trackingId: "CL-2026-1001",
    title: "Deep pothole near Ward 12 Bus Stop",
    description: "Hazardous waterlogged pothole causing traffic congestion near school.",
    language: "English",
    category: "ROADS",
    severity: "HIGH",
    riskScore: 78,
    confidence: 0.91,
    needsHumanReview: false,
    departmentName: "Public Works Department (PWD)",
    departmentCode: "PWD-RD-01",
    cpgramsMinistry: "Ministry of Road Transport & Highways / Municipal PWD",
    citationTitle: "CPGRAMS Civic Roads & Pothole Grievance Matrix",
    citationUrl: "https://pgportal.gov.in",
    wardName: "Ward 12 - Indiranagar",
    approxLocation: "100ft Road Bus Stop",
    clusterId: "ROADS_WARD12_1297_7764",
    hasPhoto: true,
    hasVideo: false,
    hasAudio: false,
    mediaAuthNote: "Clear daylight street photograph; metadata unavailable (confidence: 0.84).",
    status: "SUBMITTED",
    userConsented: true,
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

function validPublicReport(uid, overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    creatorUid: uid,
    visibility: "PUBLIC_ANONYMIZED",
    trackingCode: "CL-2026-1001",
    title: "Deep pothole near Ward 12 Bus Stop",
    category: "ROADS",
    severity: "HIGH",
    riskScore: 78,
    confidence: 0.91,
    needsHumanReview: false,
    departmentName: "Public Works Department (PWD)",
    departmentCode: "PWD-RD-01",
    citationUrl: "https://pgportal.gov.in",
    wardName: "Ward 12 - Indiranagar",
    geoCellLat: 12.97,
    geoCellLng: 77.64,
    clusterKey: "ROADS_WARD12_1297_7764",
    reportCount: 1,
    slaHours: 72,
    status: "OPEN",
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("public_reports")
      .where("visibility", "==", "PUBLIC_ANONYMIZED")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("public_reports")
      .doc("pub_1")
      .set(validPublicReport(ALICE_UID))
  );
});

test("User profile PII is strictly owner-isolated and rejects shadow fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Citizen",
      email: "alice@example.com",
      preferredLanguage: "Hindi",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());

  // Shadow field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Private report enforces ownership, consent guardrail, and immutable fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Guardrail: cannot submit without userConsented == true
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reports")
      .doc("rep_bad")
      .set(validPrivateReport(ALICE_UID, { status: "SUBMITTED", userConsented: false }))
  );

  // Valid consented submit succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reports")
      .doc("rep_1")
      .set(validPrivateReport(ALICE_UID))
  );

  // Owner list query succeeds; cross-user read fails
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("reports").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("reports").get()
  );

  // Partial update to IN_PROGRESS succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reports")
      .doc("rep_1")
      .update({
        status: "IN_PROGRESS",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Immutable createdAt tamper fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reports")
      .doc("rep_1")
      .update({
        createdAt: new Date(Date.now() - 100),
        updatedAt: new Date(Date.now() - 100),
      })
  );
});

test("Public anonymized dashboard allows filtered query, blocks PII, and allows duplicate cluster +1", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Rejects PII ghost field (email) on public_reports
  await assertFails(
    aliceDb
      .collection("public_reports")
      .doc("pub_bad")
      .set(validPublicReport(ALICE_UID, { email: "leak@example.com" }))
  );

  // Valid anonymized create succeeds
  await assertSucceeds(
    aliceDb
      .collection("public_reports")
      .doc("pub_1")
      .set(validPublicReport(ALICE_UID))
  );

  // Filtered query matches rule; unfiltered query fails
  await assertSucceeds(
    bobDb
      .collection("public_reports")
      .where("visibility", "==", "PUBLIC_ANONYMIZED")
      .get()
  );
  await assertFails(bobDb.collection("public_reports").get());

  // Duplicate cluster +1 increment by another citizen succeeds
  await assertSucceeds(
    bobDb
      .collection("public_reports")
      .doc("pub_1")
      .update({
        reportCount: 2,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Non-owner cannot change status or jump reportCount by +5
  await assertFails(
    bobDb
      .collection("public_reports")
      .doc("pub_1")
      .update({
        status: "RESOLVED",
        updatedAt: new Date(Date.now() - 200),
      })
  );

  // Owner marks RESOLVED -> terminal lock prevents further edits
  await assertSucceeds(
    aliceDb
      .collection("public_reports")
      .doc("pub_1")
      .update({
        status: "RESOLVED",
        updatedAt: new Date(Date.now() - 200),
      })
  );
  await assertFails(
    bobDb
      .collection("public_reports")
      .doc("pub_1")
      .update({
        reportCount: 3,
        updatedAt: new Date(Date.now() - 100),
      })
  );
});
