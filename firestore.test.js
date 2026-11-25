const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_A = "admin_alpha_1";
const ADMIN_B = "admin_beta_2";
const EMP_1 = "employee_one_1";
const EMP_2 = "employee_two_2";
const OUTSIDER = "outsider_99";

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ADMIN_A).get());
  await assertFails(unauth.collection("invitations").doc("INV123").get());
  await assertFails(unauth.collection("email_invites").doc("emp@example.com").get());
  await assertFails(unauth.collection("workspaces").doc(ADMIN_A).collection("sheets").get());
});

test("Direct login creates unlimited independent ADMIN accounts with workspace isolation", async () => {
  const now = new Date(Date.now() - 2000);
  const adminADb = testEnv.authenticatedContext(ADMIN_A, { email: "alpha@example.com" }).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B, { email: "beta@example.com" }).firestore();

  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A).set({
      userId: ADMIN_A,
      email: "alpha@example.com",
      displayName: "Admin Alpha",
      role: "ADMIN",
      workspaceId: ADMIN_A,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminBDb.collection("users").doc(ADMIN_B).set({
      userId: ADMIN_B,
      email: "beta@example.com",
      displayName: "Admin Beta",
      role: "ADMIN",
      workspaceId: ADMIN_B,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin B cannot read Admin A's profile or workspace sheets
  await assertFails(adminBDb.collection("users").doc(ADMIN_A).get());
  await assertFails(adminBDb.collection("workspaces").doc(ADMIN_A).collection("sheets").get());
  await assertFails(adminADb.collection("users").get());
});

test("Invited employee email cannot self-register as ADMIN and automatically redeems EMPLOYEE invitation without code", async () => {
  const now = new Date(Date.now() - 2000);
  const adminADb = testEnv.authenticatedContext(ADMIN_A, { email: "alpha@example.com" }).firestore();
  const empDb = testEnv.authenticatedContext(EMP_1, { email: "Emp1@Example.com" }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER, { email: "outsider@example.com" }).firestore();

  // Admin A invites emp1@example.com (normalized lowercase) with per-sheet permissions
  await assertSucceeds(
    adminADb.collection("invitations").doc("INVITE_EMAIL_1").set({
      inviteCode: "INVITE_EMAIL_1",
      adminUid: ADMIN_A,
      adminName: "Admin Alpha",
      adminEmail: "alpha@example.com",
      workspaceId: ADMIN_A,
      targetEmail: "emp1@example.com",
      role: "EMPLOYEE",
      defaultPermission: "SEARCH_ONLY",
      assignedSheetIds: ["sheet_1"],
      sheetPermissions: { sheet_1: "SEARCH_ADD" },
      maxUses: 1,
      usedCount: 0,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminADb.collection("email_invites").doc("emp1@example.com").set({
      email: "emp1@example.com",
      inviteCode: "INVITE_EMAIL_1",
      adminUid: ADMIN_A,
      workspaceId: ADMIN_A,
      role: "EMPLOYEE",
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Outsider cannot read emp1's email_invite lookup or list emp1's invitation
  await assertFails(outsiderDb.collection("email_invites").doc("emp1@example.com").get());
  await assertFails(
    outsiderDb.collection("invitations").where("targetEmail", "==", "emp1@example.com").get()
  );

  // Employee CAN query their own email_invite index and email-matched invitation
  await assertSucceeds(empDb.collection("email_invites").doc("emp1@example.com").get());
  await assertSucceeds(
    empDb.collection("invitations").where("targetEmail", "==", "emp1@example.com").get()
  );

  // SECURITY CRITICAL: Invited employee CANNOT register as ADMIN even via frontend manipulation!
  await assertFails(
    empDb.collection("users").doc(EMP_1).set({
      userId: EMP_1,
      email: "emp1@example.com",
      displayName: "Sneaky Employee",
      role: "ADMIN",
      workspaceId: EMP_1,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invited employee registers as EMPLOYEE cleanly
  await assertSucceeds(
    empDb.collection("users").doc(EMP_1).set({
      userId: EMP_1,
      email: "emp1@example.com",
      displayName: "Employee One",
      role: "EMPLOYEE",
      workspaceId: ADMIN_A,
      inviteCodeUsed: "INVITE_EMAIL_1",
      addedByAdminUid: ADMIN_A,
      addedByAdminName: "Admin Alpha",
      addedByAdminEmail: "alpha@example.com",
      invitedAt: now,
      joinedAt: now,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Employee cannot promote themselves to ADMIN
  await assertFails(
    empDb.collection("users").doc(EMP_1).update({
      role: "ADMIN",
      workspaceId: EMP_1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Employee cannot assign themselves extra sheets when registering employee doc
  await assertFails(
    empDb.collection("workspaces").doc(ADMIN_A).collection("employees").doc(EMP_1).set({
      employeeUid: EMP_1,
      workspaceId: ADMIN_A,
      employeeCode: "EMP-001",
      name: "Employee One",
      email: "emp1@example.com",
      addedByAdminUid: ADMIN_A,
      addedByAdminName: "Admin Alpha",
      addedByAdminEmail: "alpha@example.com",
      inviteCode: "INVITE_EMAIL_1",
      invitedAt: now,
      joinedAt: now,
      status: "ACTIVE",
      defaultPermission: "SEARCH_ONLY",
      assignedSheetIds: ["sheet_1", "sheet_secret"],
      sheetPermissions: { sheet_1: "SEARCH_ADD" },
      totalSearches: 0,
      totalRecordsAdded: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Employee registers exact assigned sheets from invite
  await assertSucceeds(
    empDb.collection("workspaces").doc(ADMIN_A).collection("employees").doc(EMP_1).set({
      employeeUid: EMP_1,
      workspaceId: ADMIN_A,
      employeeCode: "EMP-001",
      name: "Employee One",
      email: "emp1@example.com",
      addedByAdminUid: ADMIN_A,
      addedByAdminName: "Admin Alpha",
      addedByAdminEmail: "alpha@example.com",
      inviteCode: "INVITE_EMAIL_1",
      invitedAt: now,
      joinedAt: now,
      status: "ACTIVE",
      defaultPermission: "SEARCH_ONLY",
      assignedSheetIds: ["sheet_1"],
      sheetPermissions: { sheet_1: "SEARCH_ADD" },
      totalSearches: 0,
      totalRecordsAdded: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    empDb.collection("invitations").doc("INVITE_EMAIL_1").update({
      usedCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Assigned sheet permissions isolate row_pages and enforce per-sheet pending_writes", async () => {
  const now = new Date(Date.now() - 2000);
  const adminADb = testEnv.authenticatedContext(ADMIN_A, { email: "alpha@example.com" }).firestore();
  const empDb = testEnv.authenticatedContext(EMP_1, { email: "emp1@example.com" }).firestore();
  const emp2Db = testEnv.authenticatedContext(EMP_2, { email: "emp2@example.com" }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER).firestore();

  // Seed active employee EMP_1 (SEARCH_ADD on sheet_1, no access to sheet_secret)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("workspaces").doc(ADMIN_A).collection("employees").doc(EMP_1).set({
      employeeUid: EMP_1,
      workspaceId: ADMIN_A,
      employeeCode: "EMP-001",
      name: "Employee One",
      email: "emp1@example.com",
      addedByAdminUid: ADMIN_A,
      addedByAdminName: "Admin Alpha",
      addedByAdminEmail: "alpha@example.com",
      inviteCode: "INVITE101",
      invitedAt: now,
      joinedAt: now,
      status: "ACTIVE",
      defaultPermission: "SEARCH_ONLY",
      assignedSheetIds: ["sheet_1"],
      sheetPermissions: { sheet_1: "SEARCH_ADD" },
      totalSearches: 0,
      totalRecordsAdded: 0,
      createdAt: now,
      updatedAt: now,
    });
    // EMP_2 is SEARCH_ONLY on sheet_1
    await db.collection("workspaces").doc(ADMIN_A).collection("employees").doc(EMP_2).set({
      employeeUid: EMP_2,
      workspaceId: ADMIN_A,
      employeeCode: "EMP-002",
      name: "Employee Two",
      email: "emp2@example.com",
      addedByAdminUid: ADMIN_A,
      addedByAdminName: "Admin Alpha",
      addedByAdminEmail: "alpha@example.com",
      inviteCode: "INVITE102",
      invitedAt: now,
      joinedAt: now,
      status: "ACTIVE",
      defaultPermission: "SEARCH_ONLY",
      assignedSheetIds: ["sheet_1"],
      sheetPermissions: { sheet_1: "SEARCH_ONLY" },
      totalSearches: 0,
      totalRecordsAdded: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(
    adminADb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_1").set({
      workspaceId: ADMIN_A,
      spreadsheetId: "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms",
      originalTitle: "Sales IMEI Tracker",
      customName: "Store Sales",
      selectedWorksheets: ["Sheet1"],
      allWorksheets: ["Sheet1", "Archive"],
      active: true,
      connectionHealth: "HEALTHY",
      totalRecords: 120,
      aadhaarMaskingEnabled: true,
      assignedEmployeeUids: [EMP_1, EMP_2],
      createdByUid: ADMIN_A,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminADb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_1")
      .collection("row_pages").doc("page_0").set({
        workspaceId: ADMIN_A,
        sheetConnectionId: "sheet_1",
        spreadsheetId: "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms",
        spreadsheetName: "Store Sales",
        worksheetTitle: "Sheet1",
        pageIndex: 0,
        headers: ["Customer", "IMEI"],
        rowsJson: "[[\"Rohan\",\"356789012345678\"]]",
        rowCount: 1,
        assignedEmployeeUids: [EMP_1, EMP_2],
        syncedAtMillis: Date.now(),
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    adminADb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_secret").set({
      workspaceId: ADMIN_A,
      spreadsheetId: "1SecretSheetId",
      originalTitle: "Executive Payroll",
      customName: "Payroll",
      selectedWorksheets: ["Payroll"],
      allWorksheets: ["Payroll"],
      active: true,
      connectionHealth: "HEALTHY",
      totalRecords: 20,
      aadhaarMaskingEnabled: true,
      assignedEmployeeUids: [],
      createdByUid: ADMIN_A,
      createdAt: now,
      updatedAt: now,
    })
  );

  // EMP_1 can read assigned sheet and its row_pages using single-filter array-contains
  await assertSucceeds(
    empDb.collection("workspaces").doc(ADMIN_A).collection("sheets")
      .where("assignedEmployeeUids", "array-contains", EMP_1)
      .get()
  );
  await assertSucceeds(
    empDb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_1")
      .collection("row_pages").get()
  );

  // EMP_1 cannot read unassigned sheet or its row_pages
  await assertFails(empDb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_secret").get());
  await assertFails(
    empDb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_secret")
      .collection("row_pages").get()
  );
  await assertFails(outsiderDb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_1").get());

  // EMP_1 has SEARCH_ADD on sheet_1 -> can submit ADD pending_write, CANNOT submit EDIT or write to sheet_secret
  await assertSucceeds(
    empDb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_1")
      .collection("pending_writes").doc("pw_1").set({
        workspaceId: ADMIN_A,
        sheetConnectionId: "sheet_1",
        spreadsheetId: "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms",
        worksheetTitle: "Sheet1",
        actionType: "ADD",
        rowNumber: 0,
        values: ["Amit", "356789012345679"],
        headers: ["Customer", "IMEI"],
        requestedByUid: EMP_1,
        requestedByName: "Employee One",
        requestedByEmail: "emp1@example.com",
        status: "PENDING",
        createdAt: now,
        updatedAt: now,
      })
  );
  await assertFails(
    empDb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_1")
      .collection("pending_writes").doc("pw_edit_denied").set({
        workspaceId: ADMIN_A,
        sheetConnectionId: "sheet_1",
        spreadsheetId: "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms",
        worksheetTitle: "Sheet1",
        actionType: "EDIT",
        rowNumber: 2,
        values: ["Amit", "356789012345679"],
        headers: ["Customer", "IMEI"],
        requestedByUid: EMP_1,
        requestedByName: "Employee One",
        requestedByEmail: "emp1@example.com",
        status: "PENDING",
        createdAt: now,
        updatedAt: now,
      })
  );

  // EMP_2 is SEARCH_ONLY -> cannot submit ADD pending_write
  await assertFails(
    emp2Db.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_1")
      .collection("pending_writes").doc("pw_add_denied").set({
        workspaceId: ADMIN_A,
        sheetConnectionId: "sheet_1",
        spreadsheetId: "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms",
        worksheetTitle: "Sheet1",
        actionType: "ADD",
        rowNumber: 0,
        values: ["Blocked", "000"],
        headers: ["Customer", "IMEI"],
        requestedByUid: EMP_2,
        requestedByName: "Employee Two",
        requestedByEmail: "emp2@example.com",
        status: "PENDING",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Revoking sheet_1 from EMP_1 immediately denies further row_pages access
  await assertSucceeds(
    adminADb.collection("workspaces").doc(ADMIN_A).collection("employees").doc(EMP_1).update({
      assignedSheetIds: [],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    empDb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_1")
      .collection("row_pages").doc("page_0").get()
  );
});

test("Atomic employee batch onboarding + post-commit activity log + sheet reconcile + cross-admin re-invite succeed cleanly", async () => {
  const now = new Date(Date.now() - 3000);
  const adminADb = testEnv.authenticatedContext(ADMIN_A, { email: "alpha@example.com" }).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B, { email: "beta@example.com" }).firestore();
  const empDb = testEnv.authenticatedContext(EMP_1, { email: "worker@gmail.com" }).firestore();

  // Seed Admin A's connected sheet (employee not yet in assignedEmployeeUids)
  await assertSucceeds(
    adminADb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_a").set({
      workspaceId: ADMIN_A,
      spreadsheetId: "sp_a",
      originalTitle: "Alpha Sheet",
      customName: "Alpha Sheet",
      selectedWorksheets: ["Main"],
      allWorksheets: ["Main"],
      active: true,
      connectionHealth: "HEALTHY",
      totalRecords: 10,
      aadhaarMaskingEnabled: true,
      assignedEmployeeUids: [],
      createdByUid: ADMIN_A,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Case 1: Suppose worker@gmail.com had previously signed in directly as ADMIN before receiving an invite
  await assertSucceeds(
    empDb.collection("users").doc(EMP_1).set({
      userId: EMP_1,
      email: "worker@gmail.com",
      displayName: "Worker One",
      role: "ADMIN",
      workspaceId: EMP_1,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin A invites worker@gmail.com
  await assertSucceeds(
    adminADb.collection("invitations").doc("SM-BATCH01").set({
      inviteCode: "SM-BATCH01",
      adminUid: ADMIN_A,
      adminName: "Admin Alpha",
      adminEmail: "alpha@example.com",
      workspaceId: ADMIN_A,
      targetEmail: "worker@gmail.com",
      role: "EMPLOYEE",
      defaultPermission: "SEARCH_ADD",
      assignedSheetIds: ["sheet_a"],
      sheetPermissions: { sheet_a: "SEARCH_EDIT" },
      maxUses: 1,
      usedCount: 0,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminADb.collection("email_invites").doc("worker@gmail.com").set({
      email: "worker@gmail.com",
      inviteCode: "SM-BATCH01",
      adminUid: ADMIN_A,
      workspaceId: ADMIN_A,
      role: "EMPLOYEE",
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Batch transition: update users/{uid}, create employees/{uid}, increment invite, mark email_invite EXPIRED
  const t1 = new Date(Date.now() - 2000);
  const onboardBatch = empDb.batch();
  onboardBatch.update(empDb.collection("users").doc(EMP_1), {
    role: "EMPLOYEE",
    workspaceId: ADMIN_A,
    inviteCodeUsed: "SM-BATCH01",
    addedByAdminUid: ADMIN_A,
    addedByAdminName: "Admin Alpha",
    addedByAdminEmail: "alpha@example.com",
    invitedAt: now,
    joinedAt: t1,
    status: "ACTIVE",
    updatedAt: t1,
  });
  onboardBatch.set(empDb.collection("workspaces").doc(ADMIN_A).collection("employees").doc(EMP_1), {
    employeeUid: EMP_1,
    workspaceId: ADMIN_A,
    employeeCode: "EMP-WORK1",
    name: "Worker One",
    email: "worker@gmail.com",
    addedByAdminUid: ADMIN_A,
    addedByAdminName: "Admin Alpha",
    addedByAdminEmail: "alpha@example.com",
    inviteCode: "SM-BATCH01",
    invitedAt: now,
    joinedAt: t1,
    status: "ACTIVE",
    defaultPermission: "SEARCH_ADD",
    assignedSheetIds: ["sheet_a"],
    sheetPermissions: { sheet_a: "SEARCH_EDIT" },
    totalSearches: 0,
    totalRecordsAdded: 0,
    lastActiveAt: t1,
    createdAt: t1,
    updatedAt: t1,
  });
  onboardBatch.update(empDb.collection("invitations").doc("SM-BATCH01"), {
    usedCount: 1,
    updatedAt: t1,
  });
  onboardBatch.update(empDb.collection("email_invites").doc("worker@gmail.com"), {
    status: "EXPIRED",
    updatedAt: t1,
  });
  await assertSucceeds(onboardBatch.commit());

  // Post-commit: logActivity and reconcileEmployeeSheetMemberships succeed cleanly!
  await assertSucceeds(
    empDb.collection("workspaces").doc(ADMIN_A).collection("activity_logs").doc("log_join").set({
      workspaceId: ADMIN_A,
      userId: EMP_1,
      userName: "Worker One",
      userEmail: "worker@gmail.com",
      userRole: "EMPLOYEE",
      activityType: "INVITE_ACCEPTED",
      description: "Joined workspace via SM-BATCH01",
      createdAt: t1,
      updatedAt: t1,
    })
  );
  await assertSucceeds(
    empDb.collection("workspaces").doc(ADMIN_A).collection("sheets").doc("sheet_a").update({
      assignedEmployeeUids: [EMP_1],
      updatedAt: t1,
    })
  );
  await assertSucceeds(
    empDb.collection("workspaces").doc(ADMIN_A).collection("sheets")
      .where("assignedEmployeeUids", "array-contains", EMP_1)
      .get()
  );

  // Case 2: Admin B can now invite the same email because SM-BATCH01 has usedCount == maxUses
  const t2 = new Date(Date.now() - 1000);
  const crossAdminBatch = adminBDb.batch();
  crossAdminBatch.set(adminBDb.collection("invitations").doc("SM-BETA02"), {
    inviteCode: "SM-BETA02",
    adminUid: ADMIN_B,
    adminName: "Admin Beta",
    adminEmail: "beta@example.com",
    workspaceId: ADMIN_B,
    targetEmail: "worker@gmail.com",
    role: "EMPLOYEE",
    defaultPermission: "SEARCH_ONLY",
    assignedSheetIds: [],
    sheetPermissions: {},
    maxUses: 1,
    usedCount: 0,
    status: "ACTIVE",
    createdAt: t2,
    updatedAt: t2,
  });
  crossAdminBatch.set(adminBDb.collection("email_invites").doc("worker@gmail.com"), {
    email: "worker@gmail.com",
    inviteCode: "SM-BETA02",
    adminUid: ADMIN_B,
    workspaceId: ADMIN_B,
    role: "EMPLOYEE",
    status: "ACTIVE",
    createdAt: t2,
    updatedAt: t2,
  });
  await assertSucceeds(crossAdminBatch.commit());
});

test("Users can delete their own profile but cannot delete another user's profile", async () => {
  const now = new Date(Date.now() - 2000);
  const adminADb = testEnv.authenticatedContext(ADMIN_A).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER).firestore();

  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A).set({
      userId: ADMIN_A,
      email: "alpha@example.com",
      displayName: "Admin Alpha",
      role: "ADMIN",
      workspaceId: ADMIN_A,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Outsider cannot delete Admin A's profile
  await assertFails(outsiderDb.collection("users").doc(ADMIN_A).delete());
  // Admin A can delete their own profile
  await assertSucceeds(adminADb.collection("users").doc(ADMIN_A).delete());
});
