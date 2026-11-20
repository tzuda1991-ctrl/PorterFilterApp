"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const { routeEvent } = require("./notification-events");

test("new IDs notify admins only when a number is first assigned", () => {
    assert.equal(routeEvent("driver", {}, {}) , null);
    assert.equal(routeEvent("driver", {}, { driverNumber: 211126 }).audience, "admin");
    assert.equal(routeEvent("driver", { driverNumber: 211126 }, { driverNumber: 211126 }), null);
});

test("payment submission and approval have different recipients", () => {
    assert.equal(routeEvent("payment", null, { status: "pending" }).audience, "admin");
    const approval = routeEvent("payment", { status: "pending" }, { status: "approved", userId: "driver-a" });
    assert.equal(approval.audience, "driver");
    assert.equal(approval.uid, "driver-a");
    assert.equal(routeEvent("payment", { status: "approved" }, { status: "approved" }), null);
    assert.equal(routeEvent("payment", { status: "pending" }, { status: "rejected" }), null);
});

test("enabled plans and higher versions broadcast to drivers", () => {
    assert.equal(routeEvent("plan", null, { name: "Starter", price: 25 }).audience, "driver");
    assert.equal(routeEvent("plan", null, { enabled: false, name: "Starter", price: 25 }), null);
    assert.equal(routeEvent("version", { currentVersionCode: 54 }, { currentVersionCode: 55 }).audience, "driver");
    assert.equal(routeEvent("version", { currentVersionCode: 55 }, { currentVersionCode: 55 }), null);
});

test("order notifications describe what the app recorded", () => {
    const notice = routeEvent("order", null, { status: "Accepted", fare: 220 });
    assert.equal(notice.audience, "admin");
    assert.match(notice.body, /acceptance attempt/);
    assert.equal(routeEvent("order", null, { status: "rejected" }), null);
});
