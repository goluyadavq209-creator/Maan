const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";
const ADMIN_EMAIL = "maansinghyadav095@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsPayload() {
  return new Date(Date.now() - 5000);
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("videos").get());
  await assertFails(unauthDb.collection("social_accounts").get());
  await assertFails(unauthDb.collection("publishing_jobs").get());
  await assertFails(unauthDb.collection("scheduled_posts").get());
  await assertFails(unauthDb.collection("admin_logs").get());
  await assertFails(unauthDb.collection("system_settings").get());
});

test("User registration enforces creator role and blocks self-escalation", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();

  // Attempt privilege escalation to admin
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "admin",
      isBlocked: false,
      createdAt: tsPayload(),
      updatedAt: tsPayload(),
    })
  );

  // Valid creator profile creation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "creator",
      isBlocked: false,
      notifyOnPublish: true,
      notifyOnFailure: true,
      createdAt: tsPayload(),
      updatedAt: tsPayload(),
    })
  );

  // Shadow update with ghost field or role change fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: tsPayload(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      ghostField: "hack",
      updatedAt: tsPayload(),
    })
  );
});

test("PII isolation: Bob cannot read Alice's user profile or list users", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "creator",
      isBlocked: false,
      createdAt: tsPayload(),
      updatedAt: tsPayload(),
    });
  });

  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Email spoofing test: unverified admin email is rejected", async () => {
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  await assertFails(spoofDb.collection("users").get());
  await assertFails(spoofDb.collection("admin_logs").get());
  await assertFails(
    spoofDb.collection("system_settings").doc("global").set({
      maxDailyUploadsPerUser: 20,
      maxVideoSizeMb: 200,
      maxVideoDurationSec: 90,
      maintenanceMode: false,
      updatedBy: "spoof_uid",
      updatedAt: tsPayload(),
    })
  );
});

test("Verified Super Admin can list users, manage system_settings, and read admin_logs", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(adminDb.collection("admin_logs").get());
  await assertSucceeds(
    adminDb.collection("system_settings").doc("global").set({
      maxDailyUploadsPerUser: 25,
      maxVideoSizeMb: 250,
      maxVideoDurationSec: 90,
      maintenanceMode: false,
      backendRelayUrl: "",
      metaAppId: "",
      youtubeClientId: "",
      appDownloadUrl: "https://example.com/gyanverse-studio.apk",
      updatedBy: ADMIN_UID,
      updatedAt: tsPayload(),
    })
  );
});

test("Videos and publishing jobs enforce ownership, query filter alignment, and terminal state lock", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();

  // Create video for Alice
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").set({
      userId: ALICE_UID,
      title: "My Vertical Reel",
      description: "Testing cross-post",
      captionInstagram: "IG Reel",
      captionFacebook: "FB Reel",
      captionYoutube: "YT Short",
      hashtags: ["#gyanverse", "#shorts"],
      localUri: "content://media/1",
      durationSeconds: 32,
      fileSizeBytes: 10485760,
      aspectRatio: "9:16",
      status: "ready",
      createdAt: tsPayload(),
      updatedAt: tsPayload(),
    })
  );

  // Unfiltered list fails; filtered list succeeds
  await assertFails(aliceDb.collection("videos").get());
  await assertSucceeds(aliceDb.collection("videos").where("userId", "==", ALICE_UID).get());

  // Cross-user read/write fails
  await assertFails(bobDb.collection("videos").doc("vid_1").get());
  await assertFails(
    bobDb.collection("publishing_jobs").doc("job_bob_hijack").set({
      userId: BOB_UID,
      videoId: "vid_1",
      videoTitle: "Hijack",
      platform: "youtube",
      status: "queued",
      progressPercent: 0,
      retryCount: 0,
      createdAt: tsPayload(),
      updatedAt: tsPayload(),
    })
  );

  // Alice creates publishing job referencing her video
  await assertSucceeds(
    aliceDb.collection("publishing_jobs").doc("job_1").set({
      userId: ALICE_UID,
      videoId: "vid_1",
      videoTitle: "My Vertical Reel",
      platform: "youtube",
      caption: "YT Short",
      status: "success",
      progressPercent: 100,
      externalPostUrl: "https://youtube.com/shorts/abc",
      retryCount: 0,
      createdAt: tsPayload(),
      updatedAt: tsPayload(),
    })
  );

  // Terminal state lock: cannot mutate once status == 'success'
  await assertFails(
    aliceDb.collection("publishing_jobs").doc("job_1").update({
      status: "failed",
      updatedAt: tsPayload(),
    })
  );
});

test("Scheduled posts and admin_logs enforce ownership and append-only rules", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_2").set({
      userId: ALICE_UID,
      title: "Scheduled Video",
      status: "ready",
      durationSeconds: 25,
      fileSizeBytes: 5242880,
      createdAt: tsPayload(),
      updatedAt: tsPayload(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("scheduled_posts").doc("sched_1").set({
      userId: ALICE_UID,
      videoId: "vid_2",
      videoTitle: "Scheduled Video",
      platforms: ["instagram", "youtube"],
      scheduledForMillis: Date.now() + 3600000,
      status: "scheduled",
      createdAt: tsPayload(),
      updatedAt: tsPayload(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("scheduled_posts").doc("sched_1").update({
      status: "cancelled",
      updatedAt: tsPayload(),
    })
  );

  // Terminal state lock after cancelled
  await assertFails(
    aliceDb.collection("scheduled_posts").doc("sched_1").update({
      status: "scheduled",
      updatedAt: tsPayload(),
    })
  );

  // Admin log append-only
  await assertSucceeds(
    aliceDb.collection("admin_logs").doc("log_1").set({
      actorId: ALICE_UID,
      actorEmail: "alice@example.com",
      action: "PUBLISH_ATTEMPT",
      platform: "instagram",
      severity: "warning",
      details: "API CONNECTION REQUIRED",
      createdAt: tsPayload(),
    })
  );
  await assertFails(
    aliceDb.collection("admin_logs").doc("log_1").update({ details: "tampered" })
  );
  await assertFails(aliceDb.collection("admin_logs").doc("log_1").get());
});

test("Social accounts allow owner get before creation and upsert while blocking cross-user access", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();

  const docRef = aliceDb.collection("social_accounts").doc(`${ALICE_UID}_youtube`);
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      platform: "youtube",
      accountName: "Alice YT",
      accountHandle: "@alice",
      status: "api_required",
      oauthMode: "unconfigured",
      createdAt: tsPayload(),
      updatedAt: tsPayload(),
    })
  );
  await assertFails(bobDb.collection("social_accounts").doc(`${ALICE_UID}_youtube`).get());
  await assertFails(bobDb.collection("social_accounts").doc(`${ALICE_UID}_youtube`).get());
});

