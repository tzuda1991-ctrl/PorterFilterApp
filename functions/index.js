"use strict";

const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
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

const { createDelivery } = require("./delivery");
const { dispatch, sendTelegram } = createDelivery(db, () => telegramToken.value(), logger);

exports.driverIdAssigned = onDocumentWritten({ ...adminTrigger, document: "users/{uid}" }, (event) =>
    dispatch(event, routeEvent("driver", event.data?.before.data(), event.data?.after.data(), event.data?.after.id)));

exports.orderRecorded = onDocumentCreated({ ...adminTrigger, document: "orders/{orderId}" }, (event) =>
    dispatch(event, routeEvent("order", null, event.data?.data(), event.data?.id)));

exports.paymentChanged = onDocumentWritten({ ...adminTrigger, document: "payment_requests/{paymentId}" }, (event) =>
    dispatch(event, routeEvent("payment", event.data?.before.data(), event.data?.after.data(), event.data?.after.id)));

exports.versionPublished = onDocumentWritten({ ...driverTrigger, document: "app_config/version_control" }, (event) =>
    dispatch(event, routeEvent("version", event.data?.before.data(), event.data?.after.data(), event.data?.after.id)));

exports.planBroadcastRequested = onDocumentCreated({ ...driverTrigger, document: "notification_requests/{requestId}" }, async (event) => {
    const request = event.data?.data();
    if (request?.kind !== "plan" || typeof request.planId !== "string") return;
    const plan = await db.collection("subscription_plans").doc(request.planId).get();
    await dispatch(event, routeEvent("plan", null, plan.data(), plan.id));
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
