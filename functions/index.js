"use strict";

const { createHash, randomUUID } = require("node:crypto");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { defineSecret } = require("firebase-functions/params");
const { logger } = require("firebase-functions");
const { routeEvent } = require("./notification-events");

initializeApp();
const db = getFirestore();
const telegramToken = defineSecret("ZU_TELEGRAM_BOT_TOKEN");
const region = "asia-south1";
const driverTrigger = { region, retry: true };
const adminTrigger = { ...driverTrigger, secrets: [telegramToken] };
const channelId = "zu_updates";

async function eligibleDevices(notice) {
    const snapshot = notice.uid
        ? await db.collection("push_accounts").doc(notice.uid).collection("push_devices")
            .where("audience", "==", notice.audience).get()
        : await db.collectionGroup("push_devices").where("audience", "==", notice.audience).get();
    const accounts = new Map();
    const tokens = new Map();
    for (const doc of snapshot.docs) {
        const data = doc.data();
        const uid = doc.ref.parent.parent.id;
        if (!accounts.has(uid)) {
            accounts.set(uid, db.collection(notice.audience === "admin" ? "admins" : "users").doc(uid).get());
        }
        const account = await accounts.get(uid);
        if (!account.exists || typeof data.token !== "string") continue;
        if (notice.audience === "driver" &&
            (account.get("status") !== "active" || account.get("sessionAuthTime") !== data.sessionAuthTime)) continue;
        tokens.set(data.token, doc.ref);
    }
    return tokens;
}

async function deliverFcm(key, notice) {
    const devices = await eligibleDevices(notice);
    const tokens = [...devices.keys()];
    for (let index = 0; index < tokens.length; index += 500) {
        const batch = tokens.slice(index, index + 500);
        const result = await getMessaging().sendEachForMulticast({
            tokens: batch,
            notification: { title: notice.title, body: notice.body },
            data: { eventId: key },
            android: {
                priority: "high",
                notification: { channelId, tag: key },
            },
        });
        let retry = false;
        await Promise.all(result.responses.map(async (response, offset) => {
            if (response.success) return;
            const token = batch[offset];
            const code = response.error?.code;
            if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
                const ref = devices.get(token);
                await db.runTransaction(async (transaction) => {
                    if ((await transaction.get(ref)).get("token") === token) transaction.delete(ref);
                });
            } else {
                logger.warn("FCM device delivery failed", { code });
                retry = true;
            }
        }));
        if (retry) throw new Error("An FCM batch did not finish.");
    }
}

async function sendTelegram(text, ignoreDisabled = false) {
    const settings = await db.collection("telegram_config").doc("settings").get();
    if (!settings.exists || (!ignoreDisabled && settings.get("enabled") !== true)) return;
    const chatId = settings.get("chatId");
    if (typeof chatId !== "string" || !/^-?\d{1,20}$/.test(chatId)) {
        throw new Error("Telegram chat ID is missing or invalid.");
    }
    const token = telegramToken.value();
    if (!/^\d+:[A-Za-z0-9_-]+$/.test(token)) throw new Error("Telegram bot secret is not configured.");
    let response;
    try {
        response = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ chat_id: chatId, text }),
            signal: AbortSignal.timeout(10_000),
        });
    } catch {
        throw new Error("Could not reach Telegram.");
    }
    if (!response.ok) throw new Error(`Telegram rejected delivery (HTTP ${response.status}).`);
}

async function deliverOnce(key, channel, send) {
    const record = db.collection("notification_deliveries").doc(`${key}-${channel}`);
    const leaseId = randomUUID();
    const status = await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(record);
        if (snapshot.get("state") === "sent") return "sent";
        if ((snapshot.get("until") ?? 0) > Date.now()) return "busy";
        transaction.set(record, { state: "pending", leaseId, until: Date.now() + 150_000 });
        return "claimed";
    });
    if (status === "sent") return;
    if (status === "busy") throw new Error("Notification is already being delivered.");
    try {
        await send();
        await record.set({ state: "sent", sentAt: FieldValue.serverTimestamp() });
    } catch (error) {
        await db.runTransaction(async (transaction) => {
            if ((await transaction.get(record)).get("leaseId") === leaseId) transaction.delete(record);
        });
        throw error;
    }
}

function deliveryKey(event) {
    return createHash("sha256").update(event.id).digest("hex");
}

async function dispatch(event, notice) {
    if (!notice) return;
    const key = deliveryKey(event);
    const deliveries = [deliverOnce(key, "fcm", () => deliverFcm(key, notice))];
    if (notice.audience === "admin") {
        deliveries.push(deliverOnce(key, "telegram", () => sendTelegram(`${notice.title}\n${notice.body}`)));
    }
    const outcomes = await Promise.allSettled(deliveries);
    const failure = outcomes.find((outcome) => outcome.status === "rejected");
    if (failure) throw failure.reason;
}

exports.driverIdAssigned = onDocumentWritten({ ...adminTrigger, document: "users/{uid}" }, (event) =>
    dispatch(event, routeEvent("driver", event.data?.before.data(), event.data?.after.data())));

exports.orderRecorded = onDocumentCreated({ ...adminTrigger, document: "orders/{orderId}" }, (event) =>
    dispatch(event, routeEvent("order", null, event.data?.data())));

exports.paymentChanged = onDocumentWritten({ ...adminTrigger, document: "payment_requests/{paymentId}" }, (event) =>
    dispatch(event, routeEvent("payment", event.data?.before.data(), event.data?.after.data())));

exports.versionPublished = onDocumentWritten({ ...driverTrigger, document: "app_config/version_control" }, (event) =>
    dispatch(event, routeEvent("version", event.data?.before.data(), event.data?.after.data())));

exports.planBroadcastRequested = onDocumentCreated({ ...driverTrigger, document: "notification_requests/{requestId}" }, async (event) => {
    const request = event.data?.data();
    if (request?.kind !== "plan" || typeof request.planId !== "string") return;
    const plan = await db.collection("subscription_plans").doc(request.planId).get();
    await dispatch(event, routeEvent("plan", null, plan.data()));
});

exports.telegramTestRequested = onDocumentCreated({ ...adminTrigger, document: "telegram_tests/{testId}" }, async (event) => {
    if (!event.data) return;
    try {
        await sendTelegram("ZU Smart Telegram connection is working.", true);
        await event.data.ref.update({ status: "sent" });
    } catch (error) {
        await event.data.ref.update({ status: "failed", error: error.message });
    }
});
