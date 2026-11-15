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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validAccountPayload(userId = ALICE_UID) {
  return {
    userId,
    name: "Everyday Checking",
    institutionName: "First Republic",
    accountType: "checking",
    mask: "4821",
    balanceCents: 325000,
    currency: "USD",
    syncSource: "manual",
    plaidItemId: "",
    plaidAccountId: "",
    plaidCursor: "",
    lastSyncedAtMillis: 0,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validTransactionPayload(userId = ALICE_UID) {
  return {
    userId,
    accountId: "acc_1",
    merchantName: "Trader Joe's",
    rawDescription: "TRADER JOES #104 SAN FRANCISCO CA",
    amountCents: 6425,
    direction: "expense",
    categoryId: "groceries",
    dateMillis: 1763100000000,
    source: "csv_import",
    externalId: "csv_hash_001",
    isAutoCategorized: true,
    isReviewed: false,
    note: "",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validBudgetPayload(userId = ALICE_UID) {
  return {
    userId,
    categoryId: "groceries",
    monthlyLimitCents: 60000,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validRulePayload(userId = ALICE_UID) {
  return {
    userId,
    keyword: "BLUE BOTTLE",
    categoryId: "dining",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
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

test("Unauthenticated user cannot read or write accounts or transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/accounts`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/transactions`).get());
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/accounts/acc_1`).set(validAccountPayload(ALICE_UID))
  );
});

test("Authenticated owner can create, read, update, and delete own accounts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const accRef = aliceDb.doc(`users/${ALICE_UID}/accounts/acc_1`);
  await assertSucceeds(accRef.set(validAccountPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/accounts`).get());
  await assertSucceeds(
    accRef.update({
      balanceCents: 410000,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(accRef.delete());
});

test("Cross-user access is denied across accounts, transactions, budgets, and rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/transactions/tx_1`).set(validTransactionPayload(ALICE_UID))
  );

  await assertFails(bobDb.doc(`users/${ALICE_UID}/transactions/tx_1`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/transactions`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/transactions/tx_2`).set(validTransactionPayload(BOB_UID))
  );
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/budgets/groceries`).set(validBudgetPayload(ALICE_UID))
  );
});

test("Shadow update with ghost field or invalid category is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Ghost field on create
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/accounts/acc_ghost`).set({
      ...validAccountPayload(ALICE_UID),
      isAdmin: true,
    })
  );

  // Invalid categoryId
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/transactions/tx_bad_cat`).set({
      ...validTransactionPayload(ALICE_UID),
      categoryId: "crypto_scam",
    })
  );

  // Non-positive amountCents
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/transactions/tx_zero`).set({
      ...validTransactionPayload(ALICE_UID),
      amountCents: 0,
    })
  );
});

test("Budgets and category rules enforce schema and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const budgetRef = aliceDb.doc(`users/${ALICE_UID}/budgets/groceries`);
  await assertSucceeds(budgetRef.set(validBudgetPayload(ALICE_UID)));
  await assertSucceeds(
    budgetRef.update({
      monthlyLimitCents: 75000,
      updatedAt: pastTimestamp(),
    })
  );
  // Cannot mutate categoryId on existing budget
  await assertFails(
    budgetRef.update({
      categoryId: "dining",
      updatedAt: pastTimestamp(),
    })
  );

  const ruleRef = aliceDb.doc(`users/${ALICE_UID}/categoryRules/rule_1`);
  await assertSucceeds(ruleRef.set(validRulePayload(ALICE_UID)));
  await assertSucceeds(
    ruleRef.update({
      categoryId: "groceries",
      updatedAt: pastTimestamp(),
    })
  );
});
