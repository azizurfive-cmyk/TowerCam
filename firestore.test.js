const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "azizur_owner";

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

test("Any technician (unauthenticated): can create valid towergpslocation document in central database", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(
    unauthDb.collection("towergpslocation").doc("FNDGN27").set({
      site_code: "FNDGN27",
      latitude: 23.8103,
      longitude: 90.4125,
      timestamp: "2026-11-05 17:30:00",
      technician: "Device_Android_9921",
      created_at: new Date(),
    })
  );
  // Can also update/resubmit coordinates for existing site
  await assertSucceeds(
    unauthDb.collection("towergpslocation").doc("FNDGN27").set({
      site_code: "FNDGN27",
      latitude: 23.8105,
      longitude: 90.4128,
      timestamp: "2026-11-05 17:35:00",
      technician: "Device_Android_9921",
      created_at: new Date(),
    })
  );
});

test("Any technician: can create site code with spaces or dashes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(
    unauthDb.collection("towergpslocation").doc("SITE 101").set({
      site_code: "SITE 101",
      latitude: 23.8103,
      longitude: 90.4125,
      timestamp: "2026-11-05 17:30:00",
      technician: "Device_Android_9921",
      created_at: new Date(),
    })
  );
});

test("Any technician: cannot create document with invalid coordinates or mismatched site_code", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  // Invalid latitude (> 90)
  await assertFails(
    unauthDb.collection("towergpslocation").doc("FNDGN27").set({
      site_code: "FNDGN27",
      latitude: 195.0,
      longitude: 90.4125,
      timestamp: "2026-11-05 17:30:00",
      created_at: new Date(),
    })
  );
  // Mismatched site_code in doc ID vs body
  await assertFails(
    unauthDb.collection("towergpslocation").doc("FNDGN27").set({
      site_code: "DIFFERENT_CODE",
      latitude: 23.8103,
      longitude: 90.4125,
      timestamp: "2026-11-05 17:30:00",
      created_at: new Date(),
    })
  );
});

test("Unauthenticated user: cannot read tower location documents", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("towergpslocation").doc("FNDGN27").set({
      site_code: "FNDGN27",
      latitude: 23.8103,
      longitude: 90.4125,
      timestamp: "2026-11-05 17:30:00",
      created_at: new Date(),
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("towergpslocation").doc("FNDGN27").get());
});

test("Database Owner (authenticated): can read tower location documents", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("towergpslocation").doc("FNDGN27").set({
      site_code: "FNDGN27",
      latitude: 23.8103,
      longitude: 90.4125,
      timestamp: "2026-11-05 17:30:00",
      created_at: new Date(),
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(ownerDb.collection("towergpslocation").doc("FNDGN27").get());
});
