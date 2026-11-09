// ============================================================================
// TOWER LOCATION TELEGRAM BOT - CENTRAL FIRESTORE INTEGRATION
// ============================================================================
// এই স্ক্রিপ্টটি Google Apps Script-এ ব্যবহার করার জন্য সম্পূর্ণ প্রস্তুত।
// এটি TowerCam ক্যামেরা অ্যাপের সেন্ট্রাল Firestore ডাটাবেজের সাথে সরাসরি যুক্ত।
// ============================================================================

// ১. টেলিগ্রাম বট কনফিগারেশন (আপনার বটের টোকেন এখানে বসান)
const BOT_TOKEN = "7963236LACEHOLDER_OR_YOUR_TOKEN"; // <-- আপনার আসল বটের টোকেন এখানে রাখুন
const TELEGRAM_URL = "https://api.telegram.org/bot" + BOT_TOKEN;

// ২. সেন্ট্রাল ফায়ারবেস/ফায়ারস্টোর কনফিগারেশন (TowerCam App Database)
const PROJECT_ID = "gen-lang-cl035";
const DATABASE_ID = "ai-studio-android-tow5775";
const API_KEY = "AIzaSyCAsPuBAw8";
const FIRESTORE_BASE_URL = "https://firestore.googleapis.com/v1/projects/" + 
                           PROJECT_ID + "/databases/" + DATABASE_ID + "/documents";

// ============================================================================
// টেলিগ্রাম মেসেজ পাঠানোর ফাংশন (HTML মোড)
// ============================================================================
function sendMessage(chatId, text, replyMarkup) {
  try {
    const payload = {
      chat_id: chatId,
      text: text,
      parse_mode: "HTML",
      disable_web_page_preview: true
    };
    if (replyMarkup) {
      payload.reply_markup = replyMarkup;
    }
    UrlFetchApp.fetch(TELEGRAM_URL + "/sendMessage", {
      method: "post",
      contentType: "application/json",
      payload: JSON.stringify(payload),
      muteHttpExceptions: true
    });
  } catch (e) {
    Logger.log("Send Message Error: " + e.toString());
  }
}

// ============================================================================
// সেন্ট্রাল ডাটাবেজ থেকে টাওয়ারের লোকেশন খোঁজার ফাংশন (towergpslocation)
// ============================================================================
function getFirestoreData(documentId) {
  try {
    const url = FIRESTORE_BASE_URL + "/towergpslocation/" + encodeURIComponent(documentId) + "?key=" + API_KEY;
    const response = UrlFetchApp.fetch(url, { muteHttpExceptions: true });

    if (response.getResponseCode() !== 200) {
      return null;
    }

    const data = JSON.parse(response.getContentText());
    if (!data.fields) return null;

    const fields = data.fields;
    let lat = null;
    let lon = null;
    let isVerified = false;

    // ভেরিফিকেশন স্ট্যাটাস চেক
    if (fields.verification && fields.verification.stringValue) {
      isVerified = (fields.verification.stringValue.toLowerCase() === "yes");
    }

    // ১. TowerCam অ্যাপ থেকে আপলোড হওয়া ফরম্যাট (latitude_1, longitude_1)
    if (fields.latitude_1 && fields.longitude_1) {
      lat = fields.latitude_1.doubleValue || fields.latitude_1.stringValue || fields.latitude_1.integerValue;
      lon = fields.longitude_1.doubleValue || fields.longitude_1.stringValue || fields.longitude_1.integerValue;
    }
    // ২. সাধারণ ফরম্যাট (latitude, longitude)
    else if (fields.latitude && fields.longitude) {
      lat = fields.latitude.doubleValue || fields.latitude.stringValue || fields.latitude.integerValue;
      lon = fields.longitude.doubleValue || fields.longitude.stringValue || fields.longitude.integerValue;
    }
    // ৩. পূর্বের 'pin' স্ট্রিং ফরম্যাট (যেমন: "23.8103, 90.4125")
    else if (fields.pin && fields.pin.stringValue) {
      const parts = fields.pin.stringValue.replace(/\s+/g, "").split(",");
      if (parts.length >= 2) {
        lat = parts[0];
        lon = parts[1];
      }
    }

    if (lat !== null && lon !== null) {
      return {
        lat: String(lat).trim(),
        lon: String(lon).trim(),
        verified: isVerified
      };
    }

    return null;
  } catch (e) {
    Logger.log("Tower Lookup Error: " + e.toString());
    return null;
  }
}

// ============================================================================
// ইউজারের ক্রেডিট ও তথ্য পাওয়ার ফাংশন (users/{userId})
// ============================================================================
function getUserData(userId) {
  try {
    const url = FIRESTORE_BASE_URL + "/users/" + encodeURIComponent(String(userId)) + "?key=" + API_KEY;
    const response = UrlFetchApp.fetch(url, { muteHttpExceptions: true });

    if (response.getResponseCode() !== 200) {
      return null;
    }

    const data = JSON.parse(response.getContentText());
    if (!data.fields) return null;

    return {
      credit: data.fields.credit ? parseInt(data.fields.credit.integerValue || data.fields.credit.stringValue || 0) : 0,
      totalSearch: data.fields.totalSearch ? parseInt(data.fields.totalSearch.integerValue || data.fields.totalSearch.stringValue || 0) : 0,
      successSearch: data.fields.successSearch ? parseInt(data.fields.successSearch.integerValue || data.fields.successSearch.stringValue || 0) : 0,
      failedSearch: data.fields.failedSearch ? parseInt(data.fields.failedSearch.integerValue || data.fields.failedSearch.stringValue || 0) : 0,
      lastSoftwareUpdate: data.fields.last_software_update ? data.fields.last_software_update.stringValue : null
    };
  } catch (e) {
    Logger.log("Get User Data Error: " + e.toString());
    return null;
  }
}

// ============================================================================
// ইউজারের ক্রেডিট ও সার্চ পরিসংখ্যান আপডেট করার ফাংশন
// ============================================================================
function updateUserStats(userId, userData, isSuccess) {
  try {
    const url = FIRESTORE_BASE_URL + "/users/" + encodeURIComponent(String(userId)) +
      "?updateMask.fieldPaths=credit" +
      "&updateMask.fieldPaths=totalSearch" +
      "&updateMask.fieldPaths=successSearch" +
      "&updateMask.fieldPaths=failedSearch" +
      "&updateMask.fieldPaths=lastActivity" +
      "&key=" + API_KEY;

    const newCredit = Math.max(0, userData.credit - 1);
    const newTotalSearch = userData.totalSearch + 1;
    const newSuccessSearch = isSuccess ? userData.successSearch + 1 : userData.successSearch;
    const newFailedSearch = isSuccess ? userData.failedSearch : userData.failedSearch + 1;

    const updateData = {
      fields: {
        credit: { integerValue: String(newCredit) },
        totalSearch: { integerValue: String(newTotalSearch) },
        successSearch: { integerValue: String(newSuccessSearch) },
        failedSearch: { integerValue: String(newFailedSearch) },
        lastActivity: { stringValue: new Date().toISOString() }
      }
    };

    UrlFetchApp.fetch(url, {
      method: "patch",
      contentType: "application/json",
      payload: JSON.stringify(updateData),
      muteHttpExceptions: true
    });

    return newCredit;
  } catch (e) {
    Logger.log("Update Stats Error: " + e.toString());
    return userData.credit;
  }
}

// ============================================================================
// নতুন ইউজার রেজিস্টার এবং প্রাথমিক ২০ ফ্রি ক্রেডিট প্রদান
// ============================================================================
function registerUserWithCredit(userId, userName) {
  try {
    const userDocUrl = FIRESTORE_BASE_URL + "/users/" + encodeURIComponent(String(userId)) + "?key=" + API_KEY;
    const checkResponse = UrlFetchApp.fetch(userDocUrl, { muteHttpExceptions: true });

    // যদি ইউজার আগে থেকেই থাকে
    if (checkResponse.getResponseCode() === 200) {
      const data = JSON.parse(checkResponse.getContentText());
      // যদি ইউজার TowerCam অ্যাপের মাধ্যমে লগইন করে থাকে কিন্তু বটের ক্রেডিট ফিল্ড না থাকে
      if (data.fields && !data.fields.credit) {
        const patchUrl = FIRESTORE_BASE_URL + "/users/" + encodeURIComponent(String(userId)) +
          "?updateMask.fieldPaths=credit" +
          "&updateMask.fieldPaths=totalSearch" +
          "&updateMask.fieldPaths=successSearch" +
          "&updateMask.fieldPaths=failedSearch" +
          "&updateMask.fieldPaths=bot_Account" +
          "&updateMask.fieldPaths=groupactive" +
          "&key=" + API_KEY;

        const patchPayload = {
          fields: {
            credit: { integerValue: "20" },
            totalSearch: { integerValue: "0" },
            successSearch: { integerValue: "0" },
            failedSearch: { integerValue: "0" },
            bot_Account: { stringValue: "Free" },
            groupactive: { stringValue: "Yes" }
          }
        };

        UrlFetchApp.fetch(patchUrl, {
          method: "patch",
          contentType: "application/json",
          payload: JSON.stringify(patchPayload),
          muteHttpExceptions: true
        });
      }
      return;
    }

    // নতুন ইউজার তৈরি
    const nowIso = new Date().toISOString();
    const newUserData = {
      fields: {
        userId: { stringValue: String(userId) },
        name: { stringValue: userName || "Unknown" },
        credit: { integerValue: "20" },
        totalSearch: { integerValue: "0" },
        successSearch: { integerValue: "0" },
        failedSearch: { integerValue: "0" },
        bot_Account: { stringValue: "Free" },
        groupactive: { stringValue: "Yes" },
        last_notify: { stringValue: nowIso },
        last_free_credit: { stringValue: nowIso },
        lastActivity: { stringValue: nowIso },
        registeredAt: { stringValue: nowIso }
      }
    };

    UrlFetchApp.fetch(userDocUrl, {
      method: "patch",
      contentType: "application/json",
      payload: JSON.stringify(newUserData),
      muteHttpExceptions: true
    });

    sendMessage(
      userId,
      "🎉 <b>স্বাগতম!</b> 🎉\n\n" +
      "আপনাকে সফলভাবে রেজিস্টার করা হয়েছে।\n\n" +
      "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
      "🎁 <b>ফ্রি বোনাস!</b> 🎁\n" +
      "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
      "আপনাকে <b>২০ ক্রেডিট</b> উপহার হিসেবে <b>ফ্রি</b> প্রদান করা হয়েছে।\n\n" +
      "⚠️ <b>গুরুত্বপূর্ণ শর্ত:</b> ⚠️\n\n" +
      "✅ আপনি যদি আমাদের <b>অফিসিয়াল গ্রুপে জয়েন</b> করে থাকেন — তাহলে এই ক্রেডিট আপনার জন্য সংরক্ষিত থাকবে।\n\n" +
      "❌ কিন্তু আপনি যদি <b>গ্রুপে জয়েন না থাকেন</b> — তাহলে পরবর্তী ভেরিফিকেশনে আপনার <b>সমস্ত ক্রেডিট জিরো (০)</b> করে দেওয়া হবে।\n\n" +
      "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
      "🔗 <b>আমাদের অফিসিয়াল গ্রুপ:</b>\n" +
      "https://t.me/Telecom_job_bd\n\n" +
      "🙏 <b>আমাদের সাথে থাকার জন্য ধন্যবাদ।</b> 🙏"
    );

  } catch (e) {
    Logger.log("Register Error: " + e.toString());
  }
}

// ============================================================================
// মিসিং সাইট কোড নট-ফাউন্ড লগ সংরক্ষণ (not_found_logs)
// ============================================================================
function logNotFoundToFirestore(userId, userName, siteCode) {
  try {
    const logId = "log_" + new Date().getTime() + "_" + userId;
    const url = FIRESTORE_BASE_URL + "/not_found_logs/" + encodeURIComponent(logId) + "?key=" + API_KEY;

    const logData = {
      fields: {
        userId: { stringValue: String(userId) },
        userName: { stringValue: userName || "Unknown" },
        siteCode: { stringValue: siteCode },
        timestamp: { stringValue: new Date().toISOString() }
      }
    };

    UrlFetchApp.fetch(url, {
      method: "patch",
      contentType: "application/json",
      payload: JSON.stringify(logData),
      muteHttpExceptions: true
    });
  } catch (e) {
    Logger.log("Firestore Logging Error: " + e.toString());
  }
}

// ============================================================================
// মূল ওয়েবহুক হ্যান্ডলার (doPost)
// ============================================================================
function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return HtmlService.createHtmlOutput("OK");
    }

    const data = JSON.parse(e.postData.contents);

    // ডুপ্লিকেট রিকোয়েস্ট ফিল্টার (CacheService ব্যবহার করে)
    const updateId = data.update_id;
    if (updateId) {
      const cache = CacheService.getScriptCache();
      const cacheKey = "msg_update_" + updateId;
      if (cache.get(cacheKey)) {
        // এই মেসেজটি ইতিমধ্যে প্রসেস হয়েছে, টেলিগ্রামকে দ্রুত OK ফেরত দিন
        return HtmlService.createHtmlOutput("OK");
      }
      cache.put(cacheKey, "1", 60); // ৬০ সেকেন্ডের জন্য মেমরিতে রাখুন
    }

    if (!data.message || (data.message.from && data.message.from.is_bot)) {
      return HtmlService.createHtmlOutput("OK");
    }

    const chatId = data.message.chat.id;
    const userId = data.message.from.id;
    const userName = data.message.from.first_name || "Unknown";

    // ইনপুট ক্লিন
    let rawInput = data.message.text ? data.message.text.trim() : "";
    let userInput = rawInput.toUpperCase();

    // /START কমান্ড
    if (userInput === "/START") {
      const startMsg =
        "📡 <b>Tower Location Bot</b>\n\n" +
        "একটি Site Code পাঠান\n\n" +
        "✅ উদাহরণ:\n" +
        "<code>DHBDD01</code>";

      sendMessage(chatId, startMsg);
      return HtmlService.createHtmlOutput("OK");
    }

    // খালি বা স্ল্যাশ যুক্ত অন্যান্য কমান্ড
    if (userInput === "" || userInput.startsWith("/")) {
      const helpMsg =
        "📡 একটি সঠিক Site Code পাঠান\n\n" +
        "✅ উদাহরণ:\n" +
        "<code>DHBDD01</code>";

      sendMessage(chatId, helpMsg);
      return HtmlService.createHtmlOutput("OK");
    }

    // স্পেস বা একাধিক শব্দ থাকলে ফিল্টার
    if (/\s/g.test(rawInput)) {
      const spaceWarnMsg = 
        "⚠️ <b>ভুল সাইট কোড!</b>\n\n" +
        "আমাদের সাইট কোড এক শব্দের হয়, মাঝে কোনো স্পেস (Space) বা একাধিক শব্দ ব্যবহার করা যাবে না।\n\n" +
        "✅ সঠিক উদাহরণ:\n" +
        "<code>DHBDD01</code>";

      sendMessage(chatId, spaceWarnMsg);
      return HtmlService.createHtmlOutput("OK"); 
    }

    // শুধু ইংরেজি অক্ষর ও সংখ্যা অনুমোদন
    if (/[^A-Za-z0-9_]/.test(userInput)) {
      const warnMsg =
        "⚠️ <b>শুধু English Site Code ব্যবহার করুন</b>\n\n" +
        "✅ উদাহরণ:\n" +
        "<code>DHBDD01</code>";

      sendMessage(chatId, warnMsg);
      return HtmlService.createHtmlOutput("OK");
    }

    // দৈর্ঘ্য ভ্যালিডেশন
    const len = userInput.length;
    if (len < 4) {
      sendMessage(chatId, "⚠️ ৪ অক্ষরের কম কোনো Site Code হয় না।");
      return HtmlService.createHtmlOutput("OK");
    }

    if (len > 15) {
      sendMessage(chatId, "🚫 Invalid Site Code");
      return HtmlService.createHtmlOutput("OK");
    }

    // ========================================================================
    // ফায়ারস্টোর প্রসেসিং
    // ========================================================================

    // ইউজার রেজিস্টার ও ক্রেডিট ইনিশিয়ালাইজেশন
    registerUserWithCredit(userId, userName);

    // ইউজারের বর্তমান ডাটা সংগ্রহ
    const userData = getUserData(userId);

    if (userData === null) {
      sendMessage(chatId, "⚠️ Server Busy.\nপরে আবার চেষ্টা করুন।");
      return HtmlService.createHtmlOutput("OK");
    }

    // ক্রেডিট শেষ হলে নোটিশ
    if (userData.credit <= 0) {
      const noCreditMsg =
        "📡 <b>টাওয়ার লোকেশন ক্রেডিট শেষ!</b>\n\n" +
        "ভাই, আপনার অ্যাকাউন্টের <b>ফ্রি ক্রেডিট শেষ</b> হয়ে গেছে।\n\n" +
        "নতুন লোকেশন সার্চ করতে এখনই নতুন ক্রেডিট নিন।\n\n" +
        "🔄 <b>যোগাযোগ:</b>\n" +
        "<code>01886196651</code>\n\n" +
        "👉 <a href='https://t.me/Tower_Location'>🎯 ক্রেডিট নিতে ক্লিক করুন</a>\n\n" +
        "🆔 <b>User ID:</b> " + userId + "\n\n" +
        "<i>ধন্যবাদ ❤️</i>";

      sendMessage(chatId, noCreditMsg);
      return HtmlService.createHtmlOutput("OK");
    }

    // ========================================================================
    // সেন্ট্রাল টাওয়ার লোকেশন ডাটাবেজে সার্চ (towergpslocation)
    // ========================================================================
    const documentId = userInput;
    const towerInfo = getFirestoreData(documentId);

    // যদি টাওয়ার পাওয়া যায়
    if (towerInfo) {
      const remainingCredit = updateUserStats(userId, userData, true);

      const lat = towerInfo.lat;
      const lon = towerInfo.lon;
      const mapLink = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;

      const verificationBadge = towerInfo.verified 
        ? "🟢 <b>ভেরিফাইড সাইট (Verified)</b>" 
        : "🟡 <b>ফিল্ড টেকনিশিয়ান লগ (Pending Verification)</b>";

      const replyText =
        "📡 <b>Site Code:</b> " + documentId + "\n" +
        "🔰 <b>স্ট্যাটাস:</b> " + verificationBadge + "\n\n" +
        "📍 <b>Lat, Lon: </b> " +
        "<code>" + lat + ", " + lon + "</code>\n\n" +
        "🌐 <b>Google Map:</b>\n" +
        mapLink + "\n\n" +
        "💳 ১ ক্রেডিট কাটা হয়েছে\n" +
        "💰 অবশিষ্ট ক্রেডিট: <b>" + remainingCredit + "</b>\n\n" +
        "👉 <a href='https://t.me/Tower_Location_bot'>🎯 Tower Location</a>";

      sendMessage(chatId, replyText);
      return HtmlService.createHtmlOutput("OK");
    }

    // যদি টাওয়ার না পাওয়া যায় (নট ফাউন্ড)
    const remainingCredit = updateUserStats(userId, userData, false);
    logNotFoundToFirestore(userId, userName, documentId);

    const notFoundMsg =
      "❌ <b>আইডি পাওয়া যায়নি!</b>\n\n" +
      "🔍 দুঃখিত ভাই,\n" +
      "এই Site Code টি আমাদের সেন্ট্রাল ডাটাবেজে নেই:\n\n" +
      "<code>" + documentId + "</code>\n\n" +
      "💳 ১ সার্চ ক্রেডিট সমন্বয় করা হয়েছে\n" +
      "💰 অবশিষ্ট ক্রেডিট: <b>" + remainingCredit + "</b>\n\n" +
      "🛠️ <b>আপনি কীভাবে সাহায্য করতে পারেন?</b>\n" +
      "Your information can make a big difference! 🙏✨\n\n" +
      "📢 <b>তথ্য আপডেট করার মাধ্যমসমূহ:</b>\n" +
      "🤖 <b>আমাদের বটের মাধ্যমে:</b> সরাসরি নিচের লিংকে গিয়ে নিজে নিজেই আপডেট করে দিন:\n" +
      "👉 @new_tower_location_update_bot 🔄\n\n" +
      "💬 <b>আমাদের গ্রুপে পোস্ট করুন:</b> অথবা আমাদের গ্রুপে টাওয়ারের তথ্য লিখে পোস্ট দিন, আমরা পরবর্তীতে তা কালেক্ট করে ডাটাবেজে যুক্ত করে নেব : https://t.me/new_tower_location_update_group \n" +
      "👉 @Tower_Location 👥\n\n" +
      "🤝 <i>সবার সম্মিলিত প্রচেষ্টায় গড়ে উঠুক একটি নির্ভুল নেটওয়ার্ক ডাটাবেজ। ধন্যবাদ! ❤️</i>";

    sendMessage(chatId, notFoundMsg);
    return HtmlService.createHtmlOutput("OK");

  } catch (e) {
    Logger.log("Main Error: " + e.toString());
    return HtmlService.createHtmlOutput("OK");
  }
}

// ============================================================================
// ওয়েবহুক সেট করার সহজ ফাংশন
// ============================================================================
// Google Apps Script এ স্ক্রিপ্টটি Deploy করার পর যে Web App URL পাবেন, 
// সেটি নিচের WEB_APP_URL এ বসিয়ে একবার 'setWebhook' রান করলেই টেলিগ্রামের সাথে যুক্ত হয়ে যাবে।
function setWebhook() {
  const WEB_APP_URL = "https://script.google.com/macros/s/YOUR_DEPLOYMENT_ID/exec"; // <-- আপনার Web App URL দিন
  const url = TELEGRAM_URL + "/setWebhook?url=" + encodeURIComponent(WEB_APP_URL);
  const response = UrlFetchApp.fetch(url);
  Logger.log(response.getContentText());
}
