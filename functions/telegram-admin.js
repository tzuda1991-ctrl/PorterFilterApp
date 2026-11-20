"use strict";

const { randomBytes, randomUUID } = require("node:crypto");
const { setTimeout: delay } = require("node:timers/promises");
const { FieldValue, AggregateField } = require("firebase-admin/firestore");
const areasByCity = require("./area-defaults.json");

const { renderMenu, prompts, withNavigation, actionSection, keyResult } = require("./telegram-menu");

const help = [
    "ZU Smart Admin",
    "Browse: /stats · /drivers [page] · /payments [page] · /orders [page] · /plans [page] · /keys [page]",
    "Inbox: /alerts · /alert ID",
    "Details: /driver ID · /payment ID · /order ID",
    "Driver: /block ID · /unblock ID · /delete_driver ID · /reset_phone ID",
    "/edit_driver ID | name | phone | city | vehicle · /assign_id ID",
    "/extend ID | days · /expire ID | minutes",
    "Payments: /approve ID · /reject ID",
    "Plans: /save_plan ID-or-new | name | price | days | on/off | description",
    "/push_plan ID · /plan_visibility ID | on/off",
    "Keys: /generate_keys plan | days | count",
    "Cities: /cities · /add_city city · /remove_city city",
    "Areas: /areas city | page · /save_area city | old-name-or-new | name | PINs",
    "/remove_area city | area",
    "Settings: /settings · /support phone | WhatsApp · /upi UPI-ID | QR-HTTPS-URL-or-- | on/off",
    "/mirror on/off · /version · /publish_update code | name | min-code | HTTPS-APK-URL | on/off | notes | announcement",
    "Changes require a confirmation button. Use - for optional blank fields.",
].join("\n");

class CommandError extends Error {}
function requireInput(condition, message) {
    if (!condition) throw new CommandError(message);
}
function documentId(value) {
    requireInput(typeof value === "string" && /^[^/\s]{1,150}$/.test(value) && !/^\.{1,2}$/.test(value), "Enter a valid record ID.");
    return value;
}
function integer(value, min, max) {
    const number = Number(value);
    requireInput(/^\d+$/.test(value ?? "") && Number.isSafeInteger(number) && number >= min && number <= max,
        "Enter a number from " + min + " to " + max + ".");
    return number;
}
function enabled(value) {
    requireInput(value === "on" || value === "off", "Use on or off.");
    return value === "on";
}
function fields(args, size) {
    requireInput(args.length === size && args.every(Boolean), "Check the command arguments in /help.");
    return args;
}
function optional(value) {
    return value === "-" ? "" : value;
}
function https(value) {
    let url;
    try { url = new URL(value); } catch {}
    requireInput(url?.protocol === "https:" && !url.username && !url.password, "Enter an HTTPS URL.");
    return url.href;
}
function cityName(value) {
    requireInput(/^[\p{L}\p{N}][\p{L}\p{N} .&'()-]{0,59}$/u.test(value ?? ""), "Enter a valid city name.");
    return value;
}
function asTime(value) {
    return (value?.toMillis?.() ?? Date.parse(value ?? "")) || 0;
}

function isOperator(update, chatId) {
    const message = update.callback_query?.message ?? update.message;
    const sender = update.callback_query?.from ?? message?.from;
    return /^[1-9]\d{0,19}$/.test(chatId ?? "") && message?.chat?.type === "private" &&
        sender?.is_bot === false && String(message.chat.id) === chatId && String(sender.id) === chatId;
}

function parseCommand(text) {
    const match = /^\/([a-z_]+)(?:@[\w]+)?(?:\s+([\s\S]+))?$/i.exec(text.trim());
    return match ? { command: match[1].toLowerCase(), args: (match[2] ?? "").split("|").map(s => s.trim()) } : null;
}

function paymentButtons(id, actions) {
    const buttons = [{ text: "Copy ID", copy_text: { text: id } }];
    for (const [label, action, page] of actions) {
        const data = "zu_" + action + ":" + (page ? page + ":" : "") + id;
        if (Buffer.byteLength(data) <= 64) buttons.push({ text: label, callback_data: data });
    }
    return buttons;
}

function commands(db) {
    const config = id => db.collection("app_config").doc(id);
    const mirror = db.doc("telegram_config/settings");
    const ref = (collection, id) => db.collection(collection).doc(documentId(id));
    const current = async (tx, document) => {
        const snapshot = await tx.get(document);
        requireInput(snapshot.exists, "Record was removed or could not be found.");
        return snapshot;
    };
    const drivers = db.collection("users");
    async function driverById(id, read) {
        documentId(id);
        if (/^zupro\d+$/i.test(id)) {
            const number = integer(id.slice(5), 1, Number.MAX_SAFE_INTEGER);
            const matches = await read(drivers.where("driverNumber", "==", number).limit(2));
            requireInput(matches.size === 1, "Driver not found.");
            return matches.docs[0];
        }
        const driver = await read(drivers.doc(id));
        requireInput(driver.exists, "Driver not found.");
        return driver;
    }
    function describe(snapshot) {
        const data = snapshot.data();
        const safe = Object.fromEntries(Object.entries(data).filter(([key]) =>
            !/token|secret|auth|session|^email$|password|device/i.test(key)));
        return snapshot.id + "\n" + JSON.stringify(safe, null, 2);
    }
    function pageNumber(value) {
        return value ? integer(value, 1, 50) : 1;
    }
    async function list(collection, args, format) {
        const page = pageNumber(args[0]);
        const limit = 10;
        const records = db.collection(collection);
        const base = collection === "notification_inbox_admin" ? records.orderBy("createdAt", "desc") : records.orderBy("__name__");
        const result = await base.offset((page - 1) * limit).limit(limit + 1).get();
        return result.docs.slice(0, limit).map(format).join("\n") +
            (result.size > limit ? "\n\nNext page: " + (page + 1) : "") || "Nothing on this page.";
    }
    const sections = {
        alerts: ["notification_inbox_admin", doc => doc.id + " · " + doc.get("title") + " · " + doc.get("body")],
        drivers: ["users", doc => {
            const d = doc.data();
            return (d.driverNumber ? "zupro" + d.driverNumber : doc.id) + " · " + (d.name || "Driver") +
                " · " + (d.status || "unknown") + " · " + (d.planName || d.plan || "No plan");
        }],
        orders: ["orders", doc => {
            const d = doc.data();
            return doc.id + " · " + (d.driverName || "Driver") + " · ₹" + d.fare + " · " + (d.city || "");
        }],
        plans: ["subscription_plans", doc => {
            const d = doc.data();
            return doc.id + " · " + d.name + " · ₹" + d.price + " · " + d.durationDays + " days · " +
                (d.enabled === false ? "Hidden" : "Visible");
        }],
        keys: ["license_keys", doc => {
            const d = doc.data();
            return doc.id + " · " + d.planName + " · " + d.durationDays + " days · " + (d.isUsed ? "Used" : "Unused");
        }],
    };
    const cityRef = config("driver_cities");
    const cityList = snapshot => snapshot.exists ? snapshot.get("cities") ?? [] : ["Bengaluru", "Mumbai", "Delhi NCR", "Hyderabad", "Chennai", "Pune"];
    const areaList = (snapshot, city) => snapshot.exists ? snapshot.get("areas") ?? {} :
        Object.hasOwn(areasByCity, city) ? areasByCity[city] : {};

    async function paymentPage(pageArg) {
        const page = pageNumber(pageArg);
        const limit = 8;
        const snapshot = await db.collection("payment_requests").orderBy("__name__")
            .offset((page - 1) * limit).limit(limit + 1).get();
        const docs = snapshot.docs.slice(0, limit);
        const text = docs.map((doc, index) => {
            const { userName, status, amount, planName } = doc.data();
            return (index + 1) + ". " + [userName, planName, "₹" + amount, status].filter(Boolean).map(value => String(value).slice(0, 48)).join(" · ") +
                "\n" + doc.id;
        }).join("\n\n");
        const buttons = docs.map((doc, index) => paymentButtons(doc.id, [["Review", "review", page]])
            .map(button => ({ ...button, text: (index + 1) + ". " + button.text })));
        const navigation = [];
        if (page > 1) navigation.push({ text: "Previous", callback_data: "zu_payments:" + (page - 1) });
        if (snapshot.size > limit && page < 50) navigation.push({ text: "Next", callback_data: "zu_payments:" + (page + 1) });
        if (navigation.length) buttons.push(navigation);
        return { text: "Payments · Page " + page + "\n\n" + (text || "Nothing on this page."),
            markup: withNavigation({ inline_keyboard: buttons }, "zu_open:payments") };
    }

    async function paymentReview(id, page = 1) {
        const snapshot = await ref("payment_requests", id).get();
        requireInput(snapshot.exists, "Payment request could not be found.");
        const { userName, status, amount, planName, utrNumber } = snapshot.data();
        const fields = ["Payment review", "ID: " + snapshot.id, "Driver: " + (userName || "Unknown"),
            "Plan: " + (planName || "Unknown"), "Amount: ₹" + amount, "Status: " + status,
            "UTR: " + (utrNumber || "Unavailable")];
        const actions = status === "pending" ? [["Approve & Activate", "approve", page], ["Reject", "reject", page]] : [];
        return { text: fields.join("\n"), markup: withNavigation({ inline_keyboard: [paymentButtons(snapshot.id, actions)] }, "zu_payments:" + page) };
    }

    async function read(command, args) {
        if (["start", "help", "admin"].includes(command)) return help;
        if (Object.hasOwn(sections, command)) return list(sections[command][0], args, sections[command][1]);
        if (command === "stats") {
            const [all, active, orders, pending, fare] = await Promise.all([
                drivers.count().get(), drivers.where("status", "==", "active").count().get(),
                db.collection("orders").count().get(),
                db.collection("payment_requests").where("status", "==", "pending").count().get(),
                db.collection("orders").aggregate({ fare: AggregateField.sum("fare") }).get(),
            ]);
            return "Drivers: " + all.data().count + " · Active: " + active.data().count +
                "\nRecorded order attempts: " + orders.data().count + "\nRecorded fare: ₹" + fare.data().fare +
                "\nPayments pending: " + pending.data().count;
        }
        if (command === "driver") return describe(await driverById(args[0], query => query.get()));
        if (["order", "alert"].includes(command)) {
            const collection = { order: "orders", alert: "notification_inbox_admin" }[command];
            const snapshot = await ref(collection, args[0]).get();
            requireInput(snapshot.exists, "Record not found.");
            if (command === "alert") {
                const actions = { payment: "/payment", driver: "/driver", order: "/order" };
                const action = actions[snapshot.get("action")];
                const target = snapshot.get("targetId");
                return [snapshot.get("title"), snapshot.get("body"), ...(snapshot.get("details") ?? []),
                    action && target ? action + " " + target : ""].filter(Boolean).join("\n");
            }
            return describe(snapshot);
        }
        if (command === "cities") return cityList(await cityRef.get()).join("\n") || "No cities.";
        if (command === "areas") {
            const city = cityName(args[0]);
            const page = pageNumber(args[1]);
            const entries = Object.entries(areaList(await ref("area_catalog", city).get(), city))
                .sort(([a], [b]) => a.localeCompare(b));
            return entries.slice((page - 1) * 25, page * 25).map(([name, pins]) => name + (pins ? " · " + pins : ""))
                .join("\n") + (entries.length > page * 25 ? "\nNext page: " + (page + 1) : "") || "No areas.";
        }
        if (command === "settings" || command === "version") {
            const docs = command === "version" ? [config("version_control")] :
                [config("support"), config("payment_settings"), mirror];
            const data = await db.getAll(...docs);
            return data.map(doc => doc.exists ? describe(doc) : doc.id + ": not configured").join("\n\n");
        }
        return null;
    }

    const writers = new Set([
        "block", "unblock", "delete_driver", "reset_phone", "edit_driver", "extend", "expire", "assign_id",
        "approve", "reject", "save_plan", "push_plan", "plan_visibility", "generate_keys",
        "add_city", "remove_city", "save_area", "remove_area", "support", "upi", "mirror", "publish_update",
    ]);
    async function write(tx, command, args) {
        const now = Date.now();
        const iso = new Date(now).toISOString();
        if (["block", "unblock", "delete_driver", "reset_phone", "edit_driver", "extend", "expire", "assign_id"].includes(command)) {
            const driver = await driverById(args[0], query => tx.get(query));
            requireInput(driver.get("status") !== "deleted", "This driver is deleted.");
            let patch;
            switch (command) {
                case "block": patch = { status: "suspended" }; break;
                case "unblock": patch = { status: "active" }; break;
                case "delete_driver": patch = { status: "deleted" }; break;
                case "reset_phone": patch = { sessionAuthTime: 0, sessionResetAfter: Math.floor(now / 1000) }; break;
                case "assign_id": {
                    const assigned = driver.get("driverNumber");
                    if (Number.isSafeInteger(assigned)) return "Driver ID: zupro" + assigned;
                    const counter = db.doc("counters/driver_numbers");
                    const previous = (await tx.get(counter)).get("lastNumber") ?? 211125;
                    const number = previous + 1;
                    requireInput(Number.isSafeInteger(number), "Driver ID sequence is invalid.");
                    tx.set(counter, { lastNumber: number, lastUid: driver.id });
                    tx.update(driver.ref, { driverNumber: number });
                    return "Driver ID assigned: zupro" + number;
                }
                case "edit_driver": {
                    const [, name, phone, city, vehicle] = fields(args, 5);
                    requireInput(/^\d{10}$/.test(phone) && name.length <= 100 && vehicle.length <= 100,
                        "Enter a name, 10-digit phone and vehicle.");
                    requireInput(cityList(await tx.get(cityRef)).includes(city), "Choose a city from /cities.");
                    patch = { name, phone, city, vehicle };
                    break;
                }
                default: {
                    fields(args, 2);
                    const previous = asTime(driver.get("validUntil")) || asTime(driver.get("planExpiresAt"));
                    const expiry = command === "extend"
                        ? Math.max(now, previous) + integer(args[1], 1, 3650) * 86400000
                        : Math.min(previous || Infinity, now + integer(args[1], 0, 5256000) * 60000);
                    const validUntil = new Date(expiry).toISOString();
                    patch = { validUntil, planExpiresAt: validUntil };
                }
            }
            tx.update(driver.ref, patch);
            return "Driver updated. " + (patch.validUntil ? "Valid until " + patch.validUntil : "");
        }
        if (command === "approve" || command === "reject") {
            const payment = await current(tx, ref("payment_requests", args[0]));
            requireInput(payment.get("status") === "pending", "This payment has already been reviewed.");
            if (command === "approve") {
                const driver = await current(tx, ref("users", payment.get("userId")));
                requireInput(driver.get("status") !== "deleted", "This driver is deleted.");
                const planName = payment.get("planName");
                const duration = integer(String(payment.get("durationDays")), 1, 3650);
                requireInput(typeof planName === "string" && planName.trim(), "Payment plan is missing.");
                const previousPlan = driver.get("planName");
                const start = previousPlan && !/^trial$/i.test(previousPlan)
                    ? Math.max(now, asTime(driver.get("validUntil"))) : now;
                tx.update(driver.ref, {
                    subscriptionStatus: "active", planName,
                    validUntil: new Date(start + duration * 86400000).toISOString(),
                    subscriptionStartsAt: new Date(start).toISOString(), subscriptionDurationDays: String(duration),
                });
            }
            tx.update(payment.ref, { status: command === "approve" ? "approved" : "rejected", reviewedAt: iso });
            return command === "approve" ? "Payment approved and plan activated." : "Payment rejected.";
        }
        if (command === "save_plan") {
            const [id, name, price, days, visible, description] = fields(args, 6);
            const plan = id === "new" ? db.collection("subscription_plans").doc() :
                (await current(tx, ref("subscription_plans", id))).ref;
            requireInput(name.length <= 100 && description.length <= 1000, "Plan text is too long.");
            tx.set(plan, { name, price: integer(price, 1, 1000000), durationDays: integer(days, 1, 3650),
                enabled: enabled(visible), description: optional(description) }, { merge: true });
            return "Plan saved. ID: " + plan.id;
        }
        if (command === "push_plan" || command === "plan_visibility") {
            const plan = await current(tx, ref("subscription_plans", args[0]));
            if (command === "push_plan") {
                requireInput(plan.get("enabled") !== false, "Make the plan visible first.");
                tx.create(db.collection("notification_requests").doc(), { kind: "plan", planId: plan.id });
                return "Plan notification queued.";
            }
            fields(args, 2);
            tx.update(plan.ref, { enabled: enabled(args[1]) });
            return "Plan visibility saved.";
        }
        if (command === "generate_keys") {
            const [planName, days, count] = fields(args, 3);
            const durationDays = integer(days, 1, 3650);
            const total = integer(count, 1, 30);
            requireInput(planName.length <= 100, "Plan name is too long.");
            const keys = Array.from({ length: total }, () => "ZU-" + randomUUID().slice(0, 8).toUpperCase());
            for (const key of keys) {
                tx.create(ref("license_keys", key), { key, planName, durationDays, isUsed: false, createdAt: iso });
            }
            return keyResult(keys);
        }
        if (command === "add_city" || command === "remove_city") {
            const city = cityName(args[0]);
            const cities = cityList(await tx.get(cityRef));
            const match = cities.find(name => name.toLowerCase() === city.toLowerCase());
            requireInput(command === "add_city" ? !match : Boolean(match),
                command === "add_city" ? "City already exists." : "City not found.");
            tx.set(cityRef, { cities: command === "add_city" ? [...cities, city] : cities.filter(name => name !== match) });
            return "City list updated.";
        }
        if (command === "save_area" || command === "remove_area") {
            fields(args, command === "save_area" ? 4 : 2);
            const requestedCity = cityName(args[0]);
            const [, original, name, pins] = args;
            const cities = cityList(await tx.get(cityRef));
            const existingCity = cities.find(city => city.toLowerCase() === requestedCity.toLowerCase());
            const addingArea = command === "save_area" && original === "new";
            requireInput(existingCity || addingArea, "City not found. Open List Cities to check its name.");
            const city = existingCity ?? requestedCity;
            const area = db.collection("area_catalog").doc(city);
            const catalog = { ...areaList(await tx.get(area), city) };
            if (command === "remove_area" || original !== "new") {
                requireInput(Object.hasOwn(catalog, original), "Area not found.");
                delete catalog[original];
            }
            if (command === "save_area") {
                requireInput(name.length <= 100 && !name.includes(",") && !["__proto__", "constructor", "prototype"].includes(name),
                    "Enter a valid area name.");
                requireInput(!Object.keys(catalog).some(key => key.toLowerCase() === name.toLowerCase()), "Area already exists.");
                const codes = optional(pins).split(/[ ,]+/).filter(Boolean);
                requireInput(codes.every(code => /^\d{6}$/.test(code)), "Enter six-digit PIN codes.");
                catalog[name] = [...new Set(codes)].join(" ");
            }
            if (!existingCity) tx.set(cityRef, { cities: [...cities, city] });
            tx.set(area, { areas: catalog });
            if (command === "remove_area") return original + " removed from " + city + ".";
            return name + " saved in " + city + "." + (existingCity ? "" : " City added to the driver dropdown.");
        }
        if (command === "support") {
            const [phone, whatsapp] = fields(args, 2).map(optional);
            requireInput([phone, whatsapp].every(number => !number || /^\+[1-9]\d{7,14}$/.test(number)),
                "Use international numbers, for example +919876543210.");
            tx.set(config("support"), { phone, whatsapp });
            return "Support contacts saved.";
        }
        if (command === "upi") {
            const [upiId, qr, visible] = fields(args, 3);
            requireInput(/^[\w.\-]{2,100}@[\w.\-]{2,64}$/.test(upiId), "Enter a valid UPI ID.");
            tx.set(config("payment_settings"), { upiId, qrUrl: qr === "-" ? "" : https(qr), showUpiId: enabled(visible) },
                { merge: true });
            return "UPI and QR settings saved.";
        }
        if (command === "mirror") {
            tx.update(mirror, { enabled: enabled(args[0]) });
            return "Telegram alert mirroring updated.";
        }
        const [code, name, minCode, url, force, notes, announcement] = fields(args, 7);
        const currentVersionCode = integer(code, 1, 2147483647);
        const minSupportedVersionCode = integer(minCode, 1, currentVersionCode);
        const ota = config("version_control");
        const previous = await tx.get(ota);
        requireInput(currentVersionCode > (previous.get("currentVersionCode") ?? 0), "Use a version code higher than the published version.");
        tx.set(ota, { currentVersionCode, currentVersionName: name, minSupportedVersionCode,
            apkDownloadUrl: https(url), forceUpdate: enabled(force), releaseNotes: optional(notes),
            announcementNotice: optional(announcement), updatedAt: iso }, { merge: true });
        return "Update published.";
    }
    return { read, write, writers, mirror, paymentPage, paymentReview };
}

function startTelegramAdmin(db, token, signal, logger = console) {
    if (!/^\d+:[\w-]+$/.test(token)) return;
    const botId = token.split(":")[0];
    const service = commands(db);
    let offset;
    let confirmation;
    let input;
    const api = async (method, params, timeout = 12000) => {
        const response = await fetch("https://api.telegram.org/bot" + token + "/" + method, {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(params),
            signal: AbortSignal.any([signal, AbortSignal.timeout(timeout)]),
        });
        const data = await response.json();
        if (!response.ok || !data.ok) throw Object.assign(new Error("Telegram request failed"), { code: data.error_code });
        return data.result;
    };
    async function reply(chatId, message, markup) {
        const characters = Array.from(message);
        let firstMessage;
        for (let index = 0; index < characters.length; index += 1800) {
            const sent = await api("sendMessage", { chat_id: chatId, text: characters.slice(index, index + 1800).join(""),
                ...(index === 0 && markup ? { reply_markup: markup } : {}) });
            firstMessage ??= sent;
        }
        return firstMessage;
    }
    async function showResult(chatId, result) {
        await reply(chatId, result.text, result.markup);
    }
    async function confirmChange(chatId, command, args, description, back) {
        confirmation = { command, args, chatId, back, nonce: randomBytes(12).toString("hex"), expires: Date.now() + 120000 };
        await reply(chatId, "Confirm this change within 2 minutes:\n" + description, withNavigation({
            inline_keyboard: [[
                { text: "Confirm", callback_data: "zu_confirm:" + confirmation.nonce },
                { text: "Cancel", callback_data: "zu_cancel:" + confirmation.nonce },
            ]],
        }, back));
    }
    const backToAction = action => "zu_open:" + actionSection(action);
    const homeBack = "zu_open:home";

    async function requestInput(chatId, command, prompt, prefix = [], back = backToAction(command)) {
        const message = await reply(chatId, "Send:\n" + prompt + "\n\nUse - for an optional blank field.",
            withNavigation(null, back));
        input = { chatId, command, prefix, back, messageId: message.message_id, expires: Date.now() + 300000 };
    }

    async function runCommand(chatId, command, args, back = backToAction(command)) {
        if (command === "id") {
            await reply(chatId, "Your private chat ID: " + chatId, withNavigation(null, back));
            return;
        }
        if (command === "payments") {
            await showResult(chatId, await service.paymentPage(args[0]));
            return;
        }
        if (command === "payment") {
            await showResult(chatId, await service.paymentReview(args[0]));
            return;
        }
        if (command === "new_plan") {
            command = "save_plan";
            args = ["new", ...args];
        }
        if (command === "new_area") {
            command = "save_area";
            args = [args[0], "new", ...args.slice(1)];
        }
        if (service.writers.has(command)) {
            const description = command === "save_area" && args[1] === "new" && args.length === 4
                ? "Add " + args[2] + " in " + args[0] + (optional(args[3]) ? " · PIN " + args[3] : "") +
                    ". If this city is missing, it will also be added to the driver dropdown."
                : "/" + command + " " + args.join(" | ");
            await confirmChange(chatId, command, args, description, back);
            return;
        }
        const result = await service.read(command, args);
        await reply(chatId, result ?? "Unknown command. Open the admin menu.", withNavigation(null, back));
    }

    async function handle(update) {
        const callback = update.callback_query;
        const message = callback?.message ?? update.message;
        if (!message || (!callback && !message.text)) return;
        if (!Number.isSafeInteger(message.date) || message.date <= 0 || (!callback && Date.now() / 1000 - message.date > 900)) return;
        const explicitOperator = process.env.ZU_TELEGRAM_ADMIN_CHAT_ID?.trim();
        if (!callback && parseCommand(message.text)?.command === "id" && message.chat?.type === "private" &&
            String(message.chat.id) === String(message.from?.id) && message.from?.is_bot === false) {
            await reply(message.chat.id, "Your private chat ID: " + message.chat.id);
            return;
        }
        if (!callback && !message.text.startsWith("/") && !input) return;
        const chatId = explicitOperator || (await service.mirror.get()).get("chatId");
        if (!isOperator(update, chatId)) return;
        let errorBack = input?.back ?? confirmation?.back ?? homeBack;
        try {
            if (callback) {
                await api("answerCallbackQuery", { callback_query_id: callback.id });
                const data = callback.data ?? "";
                if (!/^zu_(?:confirm|cancel):/.test(data)) {
                    input = undefined;
                    confirmation = undefined;
                }
                if (data.startsWith("zu_open:")) {
                    const menu = renderMenu(data.slice("zu_open:".length));
                    requireInput(menu, "Menu unavailable. Send /start to reopen it.");
                    input = undefined;
                    await showResult(chatId, menu);
                    return;
                }
                if (data.startsWith("zu_menu:")) {
                    const menu = renderMenu(data.slice("zu_menu:".length));
                    requireInput(menu, "Menu unavailable. Send /start to reopen it.");
                    input = undefined;
                    try {
                        await api("editMessageText", { chat_id: chatId, message_id: message.message_id,
                            text: menu.text, reply_markup: menu.markup });
                    } catch (error) {
                        if (error.code !== 400) throw error;
                        if (menu.text !== message.text) await showResult(chatId, menu);
                    }
                    return;
                }
                if (/^zu_run:[a-z0-9_]+$/.test(data)) {
                    const action = data.slice("zu_run:".length);
                    const back = backToAction(action);
                    errorBack = back;
                    const keyPreset = /^key_(7|30|90|365)_(1|3|5|10|20|30)$/.exec(action);
                    const keyQuantity = /^key_quantity_(7|30|90|365)$/.exec(action);
                    if (keyPreset) {
                        await runCommand(chatId, "generate_keys", ["vip", keyPreset[1], keyPreset[2]], back);
                    } else if (keyQuantity) {
                        await requestInput(chatId, "generate_keys", "Quantity (1–30)", ["vip", keyQuantity[1]], back);
                    } else if (action === "mirror_on" || action === "mirror_off") {
                        await runCommand(chatId, "mirror", [action.slice("mirror_".length)], back);
                    } else if (Object.hasOwn(prompts, action)) {
                        await requestInput(chatId, action, prompts[action]);
                    } else {
                        requireInput(!service.writers.has(action), "Choose this action from the admin menu.");
                        await runCommand(chatId, action, []);
                    }
                    return;
                }
                const selected = /^zu_(payments|review|approve|reject):(.+)$/.exec(callback.data ?? "");
                if (selected) {
                    const [, action, target] = selected;
                    const paged = /^(\d{1,2}):(.+)$/.exec(target);
                    const page = paged ? integer(paged[1], 1, 50) : 1;
                    const id = paged ? paged[2] : target;
                    errorBack = "zu_open:payments";
                    if (action === "payments") {
                        await showResult(chatId, await service.paymentPage(id));
                    } else if (action === "review") {
                        await showResult(chatId, await service.paymentReview(id, page));
                    } else {
                        documentId(id);
                        await confirmChange(chatId, action, [id], (action === "approve" ? "Approve & activate " : "Reject ") + id, "zu_review:" + page + ":" + id);
                    }
                    return;
                }
                if (callback.data === "zu_cancel:" + confirmation?.nonce && confirmation?.chatId === chatId) {
                    const { back } = confirmation;
                    confirmation = undefined;
                    await reply(chatId, "Cancelled.", withNavigation(null, back));
                    return;
                }
                requireInput(confirmation?.chatId === chatId && confirmation.expires > Date.now() &&
                    callback.data === "zu_confirm:" + confirmation.nonce, "Confirmation expired. Send the command again.");
                const { command, args, back } = confirmation;
                const receipt = db.doc("telegram_admin_actions/" + botId + "-" + update.update_id);
                const result = await db.runTransaction(async tx => {
                    const auth = await tx.get(service.mirror);
                    requireInput(isOperator(update, explicitOperator || auth.get("chatId")), "Admin access has changed.");
                    const processed = await tx.get(receipt);
                    if (processed.exists) return "This action was already processed.";
                    const result = await service.write(tx, command, args);
                    tx.create(receipt, { actor: chatId, command, createdAt: FieldValue.serverTimestamp() });
                    return result;
                });
                confirmation = undefined;
                if (typeof result === "string") await reply(chatId, result, withNavigation(null, back));
                else await showResult(chatId, { ...result, markup: withNavigation(result.markup, back) });
                return;
            }
            const parsed = parseCommand(message.text);
            if (!parsed) {
                if (!input || input.chatId !== chatId ||
                    (message.reply_to_message && message.reply_to_message.message_id !== input.messageId)) return;
                const request = input;
                input = undefined;
                requireInput(request.expires > Date.now(), "This entry expired. Open the action again from the admin menu.");
                await runCommand(chatId, request.command, [...request.prefix, ...message.text.split("|").map(value => value.trim())], request.back);
                return;
            }
            input = undefined;
            confirmation = undefined;
            if (["start", "admin", "menu"].includes(parsed.command)) {
                await reply(chatId, "Open any section from the buttons below.", { remove_keyboard: true });
                await showResult(chatId, renderMenu("home"));
                return;
            }
            errorBack = backToAction(parsed.command);
            await runCommand(chatId, parsed.command, parsed.args);
        } catch (error) {
            if (!(error instanceof CommandError)) logger.warn("Telegram admin command failed", { code: error.code ?? "internal" });
            await reply(chatId, error instanceof CommandError ? error.message : "Could not finish the command. Check the record before retrying.", withNavigation(null, errorBack));
        }
    }
    return (async () => {
        logger.info("Telegram admin polling started.");
        while (!signal.aborted) {
            try {
                const updates = await api("getUpdates", { offset, timeout: 30, allowed_updates: ["message", "callback_query"] }, 40000);
                for (const update of updates) {
                    await handle(update);
                    offset = update.update_id + 1;
                }
            } catch (error) {
                if (signal.aborted) return;
                logger.warn("Telegram admin polling will retry", { code: error.code ?? "network" });
                await delay(error.code === 409 ? 30000 : 5000, undefined, { signal }).catch(() => {});
            }
        }
    })();
}

module.exports = { commands, isOperator, parseCommand, paymentButtons, startTelegramAdmin };
