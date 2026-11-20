"use strict";

const sections = {
    home: [
        ["Stats|stats", "Drivers|@drivers"],
        ["Payments|@payments", "Plans|@plans"],
        ["License Keys|@keys", "Cities & Areas|@cities"],
        ["Orders|@orders", "Notifications|@alerts"],
        ["Settings|@settings", "App Updates|@updates"],
        ["Help|help"],
    ],
    drivers: [
        ["List Drivers|drivers", "Driver Details|driver"],
        ["Edit Profile|edit_driver", "Assign Driver ID|assign_id"],
        ["Block Driver|block", "Unblock Driver|unblock"],
        ["Extend Validity|extend", "Expire Plan|expire"],
        ["Reset Phone Link|reset_phone", "Delete Profile|delete_driver"],
    ],
    payments: [
        ["List Payments|payments", "Review by ID|payment"],
        ["Approve by ID|approve", "Reject by ID|reject"],
    ],
    plans: [
        ["List Plans|plans", "Add Plan|new_plan"],
        ["Edit Plan|save_plan", "Show / Hide Plan|plan_visibility"],
        ["Push Plan Notification|push_plan"],
    ],
    keys: [
        ["Generate Keys|@key_plan", "List Keys|keys"],
    ],
    key_plan: [
        ["VIP|@key_validity", "Custom Plan|generate_keys"],
    ],
    key_validity: [
        ["7 Days|@key_count_7", "30 Days|@key_count_30"],
        ["90 Days|@key_count_90", "365 Days|@key_count_365"],
    ],
    cities: [
        ["List Cities|cities", "List Areas|areas"],
        ["Add City|add_city", "Remove City|remove_city"],
        ["Add Area|new_area", "Edit Area|save_area"],
        ["Remove Area|remove_area"],
    ],
    orders: [["List Orders|orders", "Order Details|order"]],
    alerts: [["Recent Notifications|alerts", "Notification Details|alert"]],
    settings: [
        ["View Settings|settings", "Support Contacts|support"],
        ["UPI & QR|upi", "Telegram Alerts|@mirroring"],
        ["My Telegram ID|id"],
    ],
    mirroring: [["Turn On|mirror_on", "Turn Off|mirror_off"]],
    updates: [["Current Version|version", "Publish Update|publish_update"]],
};

const headings = {
    home: "ZU Smart Admin", drivers: "Driver Management", payments: "Payments", plans: "Plan Management",
    keys: "License Keys", key_plan: "Choose a license plan", key_validity: "VIP license validity",
    cities: "Cities & Areas", orders: "Orders", alerts: "Notifications", settings: "Settings",
    mirroring: "Telegram alert mirroring", updates: "App Updates",
};

const prompts = {
    driver: "Driver ID", edit_driver: "Driver ID | name | phone | city | vehicle",
    assign_id: "Driver ID", block: "Driver ID", unblock: "Driver ID", delete_driver: "Driver ID",
    reset_phone: "Driver ID", extend: "Driver ID | days to add", expire: "Driver ID | minutes until expiry (0 for now)",
    payment: "Payment ID", approve: "Payment ID", reject: "Payment ID",
    new_plan: "Name | price | days | on/off | description",
    save_plan: "Plan ID | name | price | days | on/off | description",
    push_plan: "Plan ID", plan_visibility: "Plan ID | on/off",
    generate_keys: "Plan name | validity in days | quantity (1–30)",
    add_city: "City name", remove_city: "City name", areas: "City name | page (optional)",
    new_area: "City | area name | PINs\nA missing city can be added at the same time.", save_area: "City | old area name | new area name | PINs",
    remove_area: "City | area name",
    order: "Order ID", alert: "Notification ID",
    support: "Phone with country code | WhatsApp number with country code",
    upi: "UPI ID | QR HTTPS URL | on/off to show UPI ID",
    publish_update: "Version code | name | minimum code | HTTPS APK URL | force on/off | release notes | announcement",
};

const menuButton = { text: "Home", callback_data: "zu_open:home" };

function actionSection(action) {
    const normalized = action === "mirror" ? "mirror_on" : action;
    const preset = /^key_(?:quantity_)?(7|30|90|365)(?:_\d+)?$/.exec(action);
    if (preset) return "key_count_" + preset[1];
    return Object.entries(sections).find(([, rows]) => rows.flat()
        .some(entry => entry.split("|")[1] === normalized))?.[0] ?? "home";
}

function navigationRow(back) {
    const button = { text: "‹ Back", callback_data: back };
    return back.endsWith(":home") ? [button] : [button, menuButton];
}

function withNavigation(markup, back) {
    return { inline_keyboard: [...(markup?.inline_keyboard ?? []), navigationRow(back)] };
}

function renderMenu(section) {
    const quantity = /^key_count_(7|30|90|365)$/.exec(section);
    const rows = quantity ? [
        [1, 3, 5].map(count => count + "|key_" + quantity[1] + "_" + count),
        [10, 20, 30].map(count => count + "|key_" + quantity[1] + "_" + count),
        ["Custom Quantity|key_quantity_" + quantity[1]],
    ] : sections[section];
    if (!rows || (!quantity && !Object.hasOwn(sections, section))) return null;
    const back = quantity ? "key_validity" : ({ key_plan: "keys", key_validity: "key_plan", mirroring: "settings" })[section];
    const keyboard = rows.map(row => row.map(entry => {
        const [text, action] = entry.split("|");
        return { text, callback_data: action.startsWith("@") ? "zu_menu:" + action.slice(1) : "zu_run:" + action };
    }));
    if (section !== "home") {
        const row = navigationRow("zu_menu:" + (back ?? "home"));
        if (row.length === 2) row[1] = { ...menuButton, callback_data: "zu_menu:home" };
        keyboard.push(row);
    }
    return {
        text: quantity ? "Choose quantity for " + quantity[1] + "-day VIP keys" : headings[section],
        markup: { inline_keyboard: keyboard },
    };
}

function keyResult(keys) {
    const rows = [];
    for (let start = 0; start < keys.length; start += 20) {
        const batch = keys.slice(start, start + 20);
        rows.push([{
            text: keys.length <= 20 ? "Copy Keys" : "Copy Keys " + (start + 1) + "–" + (start + batch.length),
            copy_text: { text: batch.join("\n") },
        }]);
    }
    rows.push([{ text: "Generate More", callback_data: "zu_open:key_plan" }]);
    return { text: keys.length + " license key" + (keys.length === 1 ? "" : "s") + " generated\n\n" + keys.join("\n"),
        markup: { inline_keyboard: rows } };
}

module.exports = { renderMenu, prompts, withNavigation, actionSection, keyResult };
