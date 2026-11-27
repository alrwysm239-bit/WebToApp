const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_1";
const ADMIN_EMAIL = "alrwysm239@gmail.com";
const USER_UID = "user_uid_2";
const USER_EMAIL = "regular@example.com";

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

test("Unauthenticated caller cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("admin_settings").doc("gemini").get());
  await assertFails(unauthDb.collection("public_ai_config").doc("runtime").get());
  await assertFails(unauthDb.collection("admins").doc(ADMIN_UID).get());
  await assertFails(
    unauthDb.collection("public_ai_config").doc("runtime").set({
      aiEnabled: true,
      defaultModel: "gemini-2.5-flash",
      updatedBy: "anon",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Bootstrapped admin with verified email can create, read, and update admin_settings and public_ai_config", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    adminDb.collection("admin_settings").doc("gemini").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      aiEnabled: true,
      proxyUrl: "https://proxy.example.workers.dev",
      geminiApiKey: "AIzaSecretAdminKey123",
      defaultModel: "gemini-2.5-flash",
      passwordSalt: "abcd1234",
      passwordHash: "hash1234",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(adminDb.collection("admin_settings").doc("gemini").get());

  await assertSucceeds(
    adminDb.collection("admin_settings").doc("gemini").update({
      aiEnabled: false,
      proxyUrl: "",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    adminDb.collection("public_ai_config").doc("runtime").set({
      aiEnabled: true,
      proxyUrl: "https://proxy.example.workers.dev",
      defaultModel: "gemini-2.5-flash",
      updatedBy: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    adminDb.collection("public_ai_config").doc("runtime").update({
      aiEnabled: false,
      updatedBy: ADMIN_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Email spoofing test: unverified admin email is rejected", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(spoofDb.collection("admin_settings").doc("gemini").get());
  await assertFails(
    spoofDb.collection("admin_settings").doc("gemini").set({
      ownerUid: "spoof_uid",
      ownerEmail: ADMIN_EMAIL,
      aiEnabled: true,
      defaultModel: "gemini-2.5-flash",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Regular signed-in user can read public runtime config ONLY via get, and cannot read admin_settings or list collections", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = new Date(Date.now() - 5000);
    await db.collection("admin_settings").doc("gemini").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      aiEnabled: true,
      proxyUrl: "https://proxy.example.workers.dev",
      geminiApiKey: "AIzaSecretAdminKey123",
      defaultModel: "gemini-2.5-flash",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("public_ai_config").doc("runtime").set({
      aiEnabled: true,
      proxyUrl: "https://proxy.example.workers.dev",
      defaultModel: "gemini-2.5-flash",
      updatedBy: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
  });

  const userDb = testEnv.authenticatedContext(USER_UID, {
    email: USER_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(userDb.collection("public_ai_config").doc("runtime").get());
  await assertFails(userDb.collection("public_ai_config").get());
  await assertFails(userDb.collection("admin_settings").doc("gemini").get());
  await assertFails(userDb.collection("admin_settings").get());
  await assertFails(
    userDb.collection("public_ai_config").doc("runtime").update({
      aiEnabled: false,
      updatedBy: USER_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Shadow fields, extra keys, and tampered future timestamps are rejected", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const created = new Date(Date.now() - 5000);

  await assertFails(
    adminDb.collection("admin_settings").doc("gemini").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      aiEnabled: true,
      defaultModel: "gemini-2.5-flash",
      extraGhostField: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    adminDb.collection("admin_settings").doc("gemini").set({
      ownerUid: ADMIN_UID,
      ownerEmail: ADMIN_EMAIL,
      aiEnabled: true,
      defaultModel: "gemini-2.5-flash",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );
});
