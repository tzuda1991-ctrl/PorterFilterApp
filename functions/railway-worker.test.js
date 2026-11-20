"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const { pendingEvents } = require("./railway-worker");

const snapshot = (value, created, updated) => ({
    data: () => value,
    createTime: { toMillis: () => created },
    updateTime: { toMillis: () => updated },
});

test("historical events do not broadcast on startup", () => {
    assert.deepEqual(pendingEvents("order", snapshot({ status: "Accepted" }, 1, 1), 10), []);
    assert.deepEqual(pendingEvents("driver", snapshot({ driverNumber: 211126 }, 1, 2), 10), []);
    assert.deepEqual(pendingEvents("payment", snapshot({ status: "approved" }, 1, 2), 10), []);
});

test("a payment submitted and approved while offline still routes both notifications", () => {
    const events = pendingEvents("payment", snapshot({ status: "approved", userId: "driver-a" }, 11, 12), 10);
    assert.deepEqual(events.map(([key, notice]) => [key, notice.audience, notice.uid]), [
        ["submitted", "admin", undefined],
        ["approved", "driver", "driver-a"],
    ]);
});

test("an existing pending payment approved after deployment is private to the driver", () => {
    const events = pendingEvents("payment", snapshot({ status: "approved", userId: "driver-a" }, 1, 12), 10);
    assert.deepEqual(events.map(([key, notice]) => [key, notice.uid]), [["approved", "driver-a"]]);
});
