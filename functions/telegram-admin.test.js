"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const { commands, isOperator, parseCommand } = require("./telegram-admin");

const privateMessage = id => ({ chat: { id, type: "private" }, from: { id, is_bot: false } });

test("only the configured human in their private chat can use admin commands", () => {
    assert.equal(isOperator({ message: privateMessage(42) }, "42"), true);
    assert.equal(isOperator({ message: privateMessage(99) }, "42"), false);
    assert.equal(isOperator({ message: { ...privateMessage(42), chat: { id: -42, type: "supergroup" } } }, "-42"), false);
    assert.equal(isOperator({ callback_query: { message: privateMessage(42), from: { id: 99, is_bot: false } } }, "42"), false);
    assert.equal(isOperator({ callback_query: { message: privateMessage(42), from: { id: 42, is_bot: false } } }, "42"), true);
});

test("command parser preserves multiword and multiline form fields", () => {
    assert.deepEqual(parseCommand("/save_plan@ZuBot new | Basic Starter | 25 | 7 | on | Line one\nLine two"), {
        command: "save_plan", args: ["new", "Basic Starter", "25", "7", "on", "Line one\nLine two"],
    });
});

function storage(records) {
    const db = { doc: path => path, collection: name => ({ doc: id => name + "/" + id }) };
    const writes = [];
    const tx = {
        get: async ref => ({
            ref, exists: Object.hasOwn(records, ref), id: ref.split("/").at(-1), get: key => records[ref]?.[key],
        }),
        set: (ref, values) => writes.push({ ref, values }),
        update: (ref, values) => writes.push({ ref, values }),
    };
    return { service: commands(db), tx, writes };
}

test("approval rechecks status and writes the plan and exact payment together", async () => {
    const { service, tx, writes } = storage({
        "payment_requests/a": { status: "pending", userId: "driver-a", planName: "Weekly", durationDays: 7 },
        "payment_requests/b": { status: "approved" },
        "users/driver-a": { status: "active", planName: "Weekly", validUntil: "2090-01-01T00:00:00.000Z" },
    });
    assert.equal(await service.write(tx, "approve", ["a"]), "Payment approved and plan activated.");
    assert.equal(writes[0].ref, "users/driver-a");
    assert.equal(writes[0].values.validUntil, "2090-01-08T00:00:00.000Z");
    assert.equal(writes[1].ref, "payment_requests/a");
    assert.equal(writes[1].values.status, "approved");
    await assert.rejects(service.write(tx, "reject", ["b"]), /already been reviewed/);
    assert.equal(writes.length, 2);
});

test("editing a default city area keeps the rest of the bundled catalog", async () => {
    const { service, tx, writes } = storage({});
    await service.write(tx, "remove_area", ["Delhi NCR", "Okhla"]);
    assert.equal(writes[0].ref, "area_catalog/Delhi NCR");
    assert.equal(writes[0].values.areas.Dwarka, "");
    assert.equal(Object.hasOwn(writes[0].values.areas, "Okhla"), false);
});

test("payment buttons copy the full ID and keep review callbacks within Telegram's byte limit", () => {
    const { paymentButtons } = require("./telegram-admin");
    const id = "PAY-50b2b431-c8d3-4633-a570-9fdffd692d11";
    assert.deepEqual(paymentButtons(id, [["Review", "review"], ["Approve", "approve"]]), [
        { text: "Copy ID", copy_text: { text: id } },
        { text: "Review", callback_data: "zu_review:" + id },
        { text: "Approve", callback_data: "zu_approve:" + id },
    ]);
    const longId = "a".repeat(150);
    assert.deepEqual(paymentButtons(longId, [["Review", "review"]]), [
        { text: "Copy ID", copy_text: { text: longId } },
    ]);
});

test("inline admin menu exposes all write commands through buttons or key presets", () => {
    const { renderMenu } = require("./telegram-menu");
    const { service } = storage({});
    const visited = new Set();
    const actions = new Set();
    function visit(section) {
        if (visited.has(section)) return;
        visited.add(section);
        const menu = renderMenu(section);
        assert.ok(menu?.text);
        for (const button of menu.markup.inline_keyboard.flat()) {
            assert.ok(Buffer.byteLength(button.callback_data) <= 64);
            const [type, action] = button.callback_data.split(":");
            if (type === "zu_menu") visit(action);
            if (type === "zu_run") actions.add(action);
        }
    }
    visit("home");
    for (const command of service.writers) {
        assert.ok(actions.has(command === "mirror" ? "mirror_on" : command), command);
    }
    assert.ok(actions.has("key_365_30"));
});

test("copy buttons keep maximum-size batches within Telegram's limit", () => {
    const { keyResult } = require("./telegram-menu");
    const keys = Array.from({ length: 30 }, (_, index) => "ZU-" + String(index).padStart(8, "0"));
    const buttons = keyResult(keys).markup.inline_keyboard.flat().filter(button => button.copy_text);
    assert.equal(buttons.length, 2);
    assert.ok(buttons.every(button => button.copy_text.text.length <= 256));
    assert.deepEqual(buttons.flatMap(button => button.copy_text.text.split("\n")), keys);
});

test("each submenu has Back and results return to their own section", () => {
    const { renderMenu, withNavigation, actionSection } = require("./telegram-menu");
    for (const section of ["drivers", "plans", "payments", "keys", "cities", "orders", "alerts", "settings", "updates"]) {
        assert.deepEqual(renderMenu(section).markup.inline_keyboard.at(-1), [
            { text: "‹ Back", callback_data: "zu_menu:home" },
        ]);
    }
    assert.equal(renderMenu("key_count_30").markup.inline_keyboard.at(-1)[0].callback_data, "zu_menu:key_validity");
    assert.equal(renderMenu("key_validity").markup.inline_keyboard.at(-1)[0].callback_data, "zu_menu:key_plan");
    assert.equal(actionSection("drivers"), "drivers");
    assert.equal(actionSection("save_plan"), "plans");
    assert.equal(actionSection("key_quantity_90"), "key_count_90");
    assert.equal(actionSection("key_365_30"), "key_count_365");
    assert.deepEqual(withNavigation(null, "zu_open:drivers").inline_keyboard[0], [
        { text: "‹ Back", callback_data: "zu_open:drivers" },
        { text: "Home", callback_data: "zu_open:home" },
    ]);
});

test("review and confirmation targets preserve the payment page", async () => {
    const id = "PAY-50b2b431-c8d3-4633-a570-9fdffd692d11";
    const data = { userName: "Driver", planName: "Weekly", status: "pending", amount: 100, utrNumber: "012345678900" };
    const snapshot = { exists: true, id, data: () => data };
    const query = { orderBy: () => query, offset: () => query, limit: () => query,
        get: async () => ({ docs: [snapshot], size: 1 }), doc: () => ({ get: async () => snapshot }) };
    const service = commands({ collection: () => query, doc: () => ({}) });
    const listing = await service.paymentPage("3");
    assert.equal(listing.markup.inline_keyboard[0][1].callback_data, "zu_review:3:" + id);
    assert.equal(listing.markup.inline_keyboard.at(-1)[0].callback_data, "zu_open:payments");
    const review = await service.paymentReview(id, 3);
    assert.equal(review.markup.inline_keyboard[0][1].callback_data, "zu_approve:3:" + id);
    assert.equal(review.markup.inline_keyboard.at(-1)[0].callback_data, "zu_payments:3");
});

test("adding an area for a missing city saves the city and area together", async () => {
    const { service, tx, writes } = storage({});
    const result = await service.write(tx, "save_area", ["Dhoraji", "new", "Baharpura", "360410"]);
    assert.match(result, /City added to the driver dropdown/);
    assert.ok(writes[0].values.cities.includes("Dhoraji"));
    assert.ok(writes[0].values.cities.includes("Bengaluru"));
    assert.deepEqual(writes[1], { ref: "area_catalog/Dhoraji", values: { areas: { Baharpura: "360410" } } });
});

test("area additions use the saved spelling of the city and avoid duplicate cities", async () => {
    const { service, tx, writes } = storage({ "app_config/driver_cities": { cities: ["Dhoraji"] } });
    await service.write(tx, "save_area", ["dhoraji", "new", "Baharpura", "360410"]);
    assert.equal(writes.length, 1);
    assert.equal(writes[0].ref, "area_catalog/Dhoraji");
});

test("invalid PIN stops the city and area from being added", async () => {
    const { service, tx, writes } = storage({});
    await assert.rejects(service.write(tx, "save_area", ["Dhoraji", "new", "Baharpura", "123"]), /six-digit/);
    assert.equal(writes.length, 0);
});
