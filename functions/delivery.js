"use strict";
const { createHash, randomUUID } = require("node:crypto");
const { FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const channelId = "zu_updates";

function createDelivery(db, telegramSecret, logger = console) {
    async function eligibleDevices(notice) {
        const accountCollection = notice.audience === "admin" ? "admins" : "users";
        const readsForAccount = (uid) => db.collection("push_accounts").doc(uid).collection("push_devices")
            .where("audience", "==", notice.audience).get();
        let docs;
        if (notice.uid) {
            docs = (await readsForAccount(notice.uid)).docs;
        } else {
            try {
                docs = (await db.collectionGroup("push_devices").where("audience", "==", notice.audience).get()).docs;
            } catch (error) {
                if (error.code !== 9 && error.code !== "failed-precondition") throw error;
                const accounts = await db.collection(accountCollection).get();
                docs = [];
                for (let index = 0; index < accounts.docs.length; index += 20) {
                    const group = accounts.docs.slice(index, index + 20);
                    const devices = await Promise.all(group.map((account) => readsForAccount(account.id)));
                    for (const snapshot of devices) docs.push(...snapshot.docs);
                }
                logger.info("FCM recovered using per-account token reads", { audience: notice.audience });
            }
        }
        const accounts = new Map();
        const tokens = new Map();
        for (const doc of docs) {
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
        if (tokens.length === 0) {
            logger.warn("No eligible FCM devices; check notification registration and Firestore rules", {
                audience: notice.audience, targeted: Boolean(notice.uid),
            });
            return;
        }
        let delivered = 0;
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
            delivered += result.successCount;
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
        logger.info("FCM delivery processed", { audience: notice.audience, delivered, devices: tokens.length });
    }

    async function sendTelegram(text, ignoreDisabled = false) {
        const settings = await db.collection("telegram_config").doc("settings").get();
        if (!ignoreDisabled && (!settings.exists || settings.get("enabled") !== true)) return;
        if (!settings.exists) throw new Error("Save the Telegram chat ID before testing.");
        const chatId = settings.get("chatId");
        if (typeof chatId !== "string" || !/^-?\d{1,20}$/.test(chatId)) {
            throw new Error("Telegram chat ID is missing or invalid.");
        }
        const token = telegramSecret();
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

    async function storeInboxItem(key, notice) {
        const inbox = notice.audience === "admin" ? db.collection("notification_inbox_admin")
            : notice.uid ? db.collection("notification_inboxes").doc(notice.uid).collection("items")
                : db.collection("notification_inbox_driver");
        try {
            await inbox.doc(key).create({
                title: notice.title,
                body: notice.body,
                details: notice.details ?? [],
                createdAt: FieldValue.serverTimestamp(),
            });
        } catch (error) {
            if (error.code !== 6 && error.code !== "already-exists") throw error;
        }
    }

    async function dispatch(event, notice) {
        if (!notice) return;
        const key = deliveryKey(event);
        const deliveries = [
            storeInboxItem(key, notice),
            deliverOnce(key, "fcm", () => deliverFcm(key, notice)),
        ];
        if (notice.audience === "admin") {
            deliveries.push(deliverOnce(key, "telegram", () => sendTelegram(`${notice.title}\n${notice.body}`)));
        }
        const outcomes = await Promise.allSettled(deliveries);
        const failure = outcomes.find((outcome) => outcome.status === "rejected");
        if (failure) throw failure.reason;
    }

    return { dispatch, deliverOnce, sendTelegram, deliveryKey };
}

module.exports = { createDelivery };
