"use strict";

function orderText(order) {
    const amount = Number.isSafeInteger(order.fare) && order.fare > 0 ? ` · ₹${order.fare}` : "";
    return `A driver recorded an acceptance attempt${amount}.`;
}

function detail(label, value) {
    return typeof value === "string" && value.trim() ? [label + ": " + value.trim().slice(0, 160)] : [];
}

function paymentDetails(payment) {
    return [
        ...detail("Plan", payment.planName),
        ...(Number.isSafeInteger(payment.amount) ? ["Amount: ₹" + payment.amount] : []),
    ];
}

function routeEvent(kind, before, after, documentId = "") {
    if (!after) return null;
    switch (kind) {
        case "driver":
            if (before?.driverNumber || !Number.isSafeInteger(after.driverNumber)) return null;
            return { audience: "admin", title: "New driver ID", action: "driver", targetId: documentId, body: `zupro${after.driverNumber} was assigned.`, details: [...detail("Driver", after.name), ...detail("City", after.city)] };
        case "order":
            if (after.status !== "Accepted") return null;
            return { audience: "admin", title: "Order acceptance recorded", action: "order", targetId: after.orderId ?? documentId, body: orderText(after), details: detail("Driver", after.driverName) };
        case "payment":
            if (!before && after.status === "pending") {
                return { audience: "admin", title: "Payment proof submitted", action: "payment", targetId: documentId, body: "A new UTR is awaiting review in Payments.",
                    details: [...detail("Driver", after.userName), ...paymentDetails(after), ...detail("UTR", after.utrNumber)] };
            }
            if (before?.status === "pending" && after.status === "approved" && typeof after.userId === "string") {
                return { audience: "driver", uid: after.userId, title: "Payment approved", action: "profile", targetId: "", body: "Open ZU Smart to see your updated plan.", details: paymentDetails(after) };
            }
            return null;
        case "version":
            if (!Number.isSafeInteger(after.currentVersionCode) || after.currentVersionCode <= (before?.currentVersionCode ?? 0)) return null;
            return {
                audience: "driver",
                title: "ZU Smart update available", action: "update", targetId: "",
                body: `Open ZU Smart to download version ${String(after.currentVersionName ?? "").slice(0, 40)}.`,
            };
        case "plan":
            if (after.enabled === false || typeof after.name !== "string" || !Number.isSafeInteger(after.price)) return null;
            return {
                audience: "driver",
                title: "ZU Smart plan available", action: "plan", targetId: documentId,
                body: `${after.name.slice(0, 80)} · ₹${after.price}. Open Plans to view it.`,
            };
        default:
            return null;
    }
}

module.exports = { routeEvent };
