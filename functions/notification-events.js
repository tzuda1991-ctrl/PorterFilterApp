"use strict";

function orderText(order) {
    const amount = Number.isSafeInteger(order.fare) && order.fare > 0 ? ` · ₹${order.fare}` : "";
    return `A driver recorded an acceptance attempt${amount}.`;
}

function routeEvent(kind, before, after) {
    if (!after) return null;
    switch (kind) {
        case "driver":
            if (before?.driverNumber || !Number.isSafeInteger(after.driverNumber)) return null;
            return { audience: "admin", title: "New driver ID", body: `zupro${after.driverNumber} was assigned.` };
        case "order":
            if (after.status !== "Accepted") return null;
            return { audience: "admin", title: "Order acceptance recorded", body: orderText(after) };
        case "payment":
            if (!before && after.status === "pending") {
                return { audience: "admin", title: "Payment proof submitted", body: "A new UTR is awaiting review in Payments." };
            }
            if (before?.status === "pending" && after.status === "approved" && typeof after.userId === "string") {
                return { audience: "driver", uid: after.userId, title: "Payment approved", body: "Open ZU Smart to see your updated plan." };
            }
            return null;
        case "version":
            if (!Number.isSafeInteger(after.currentVersionCode) || after.currentVersionCode <= (before?.currentVersionCode ?? 0)) return null;
            return {
                audience: "driver",
                title: "ZU Smart update available",
                body: `Open ZU Smart to download version ${String(after.currentVersionName ?? "").slice(0, 40)}.`,
            };
        case "plan":
            if (after.enabled === false || typeof after.name !== "string" || !Number.isSafeInteger(after.price)) return null;
            return {
                audience: "driver",
                title: "ZU Smart plan available",
                body: `${after.name.slice(0, 80)} · ₹${after.price}. Open Plans to view it.`,
            };
        default:
            return null;
    }
}

module.exports = { routeEvent };
