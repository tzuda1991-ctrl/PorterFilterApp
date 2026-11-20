"use strict";

const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { setTimeout: delay } = require("node:timers/promises");
const { createDelivery } = require("./delivery");
const { routeEvent } = require("./notification-events");

function pendingEvents(kind, document, since) {
    const data = document.data();
    const created = document.createTime?.toMillis() >= since;
    const updated = document.updateTime?.toMillis() >= since;
    if (kind === "payment") {
        const events = [];
        if (created) events.push(["submitted", routeEvent(kind, null, { ...data, status: "pending" })]);
        const reviewedAt = typeof data.reviewedAt === "string" ? Date.parse(data.reviewedAt) : NaN;
        if ((Number.isFinite(reviewedAt) ? reviewedAt >= since : updated) && data.status === "approved") {
            events.push(["approved", routeEvent(kind, { status: "pending" }, data)]);
        }
        return events;
    }
    if (kind === "driver" && updated && data.driverNumber) {
        return [[`driver-${data.driverNumber}`, routeEvent(kind, null, data)]];
    }
    if (kind === "version" && updated) {
        return [[`version-${data.currentVersionCode}`, routeEvent(kind, null, data)]];
    }
    if (kind === "order" && created) return [["order", routeEvent(kind, null, data)]];
    return [];
}

async function run() {
    let credentials;
    try {
        credentials = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON ?? "");
        if (credentials.project_id !== "zusmartpro" || !credentials.private_key || !credentials.client_email) {
            throw new Error();
        }
    } catch {
        throw new Error("Set FIREBASE_SERVICE_ACCOUNT_JSON for the zusmartpro Firebase project in Railway Variables.");
    }
    initializeApp({ credential: cert(credentials), projectId: credentials.project_id });
    const db = getFirestore();
    const delivery = createDelivery(db, () => process.env.ZU_TELEGRAM_BOT_TOKEN ?? "");
    const state = db.collection("notification_worker").doc("railway");
    await db.runTransaction(async (transaction) => {
        if (!(await transaction.get(state)).exists) {
            transaction.create(state, { startedAt: FieldValue.serverTimestamp() });
        }
    });
    const since = (await state.get()).get("startedAt").toMillis();
    let stopping = false;
    const pending = new Map();
    const unsubscribers = [];

    function enqueue(id, action) {
        if (pending.has(id) || stopping) return;
        const task = (async () => {
            let retryDelay = 1000;
            while (!stopping) {
                try {
                    await action();
                    return;
                } catch {
                    console.error("Notification processing failed; will retry.", { event: id });
                    await delay(retryDelay, undefined, { ref: false });
                    retryDelay = Math.min(retryDelay * 2, 60_000);
                }
            }
        })();
        pending.set(id, task);
        void task.finally(() => pending.delete(id));
    }

    function listen(query, processDocument) {
        unsubscribers.push(query.onSnapshot((snapshot) => {
            for (const change of snapshot.docChanges()) {
                if (change.type !== "removed") processDocument(change.doc);
            }
        }, (error) => {
            console.error("Firestore listener stopped.", { code: error.code });
            process.exit(1);
        }));
    }

    for (const [collection, kind] of [["users", "driver"], ["orders", "order"], ["payment_requests", "payment"]]) {
        listen(db.collection(collection), (document) => {
            if (kind === "driver" && document.updateTime.toMillis() < since && document.get("driverNumber")) {
                const id = `${document.ref.path}/driver-${document.get("driverNumber")}`;
                enqueue(id, () => Promise.all([
                    delivery.deliverOnce(delivery.deliveryKey({ id }), "fcm", async () => {}),
                    delivery.deliverOnce(delivery.deliveryKey({ id }), "telegram", async () => {}),
                ]));
            }
            for (const [version, notice] of pendingEvents(kind, document, since)) {
                if (!notice) continue;
                const id = `${document.ref.path}/${version}`;
                enqueue(id, () => delivery.dispatch({ id }, notice));
            }
        });
    }
    unsubscribers.push(db.doc("app_config/version_control").onSnapshot((document) => {
        if (!document.exists) return;
        for (const [version, notice] of pendingEvents("version", document, since)) {
            if (!notice) continue;
            const id = `${document.ref.path}/${version}`;
            enqueue(id, () => delivery.dispatch({ id }, notice));
        }
    }, (error) => {
        console.error("Version listener stopped.", { code: error.code });
        process.exit(1);
    }));
    listen(db.collection("notification_requests"), (document) => {
        if (document.createTime.toMillis() < since || document.get("kind") !== "plan") return;
        const id = document.ref.path;
        enqueue(id, async () => {
            const plan = await db.collection("subscription_plans").doc(document.get("planId")).get();
            await delivery.dispatch({ id }, routeEvent("plan", null, plan.data()));
        });
    });
    listen(db.collection("telegram_tests").where("status", "==", "pending"), (document) => {
        const id = document.ref.path;
        enqueue(id, async () => {
            try {
                await delivery.deliverOnce(delivery.deliveryKey({ id }), "telegram",
                    () => delivery.sendTelegram("ZU Smart Telegram connection is working.", true));
                await document.ref.update({ status: "sent" });
            } catch (error) {
                await document.ref.update({ status: "failed", error: error.message });
            }
        });
    });

    function shutdown() {
        stopping = true;
        unsubscribers.forEach((unsubscribe) => unsubscribe());
        const timeout = setTimeout(() => process.exit(0), 8000);
        timeout.unref();
        void Promise.allSettled([...pending.values()]).then(() => process.exit(0));
    }
    process.once("SIGTERM", shutdown);
    process.once("SIGINT", shutdown);
    console.log("ZU Smart Railway notification worker listening.");
}

if (require.main === module) {
    run().catch((error) => {
        console.error("Notification worker could not start.", error.code ?? error.message);
        process.exitCode = 1;
    });
}

module.exports = { pendingEvents };
