# FocusShield - React Native App Blocker & Node.js PostgreSQL Backend

An Android App Blocker built with **React Native** and a **Node.js/Express + PostgreSQL** backend. It intercepts and prevents target apps (e.g. **Instagram**) from opening on your device.

---

## 🏗️ Architecture & How Market App Blockers Work

```
+--------------------------------------------------------------------------+
|                               ANDROID OS                                 |
|                                                                          |
| 1. User taps Instagram on phone home screen                              |
| 2. OS triggers Activity: com.instagram.android                           |
| 3. AccessibilityService receives event: TYPE_WINDOW_STATE_CHANGED        |
| 4. Native Service reads SharedPreferences (<1ms fast memory cache)       |
| 5. If package is blocked:                                                |
|    - Calls performGlobalAction(GLOBAL_ACTION_HOME) (Dismisses Instagram) |
|    - Launches BlockOverlayActivity (Draws over other apps)               |
| 6. User sees: "Instagram: No, you cannot open it."                       |
+--------------------------------------------------------------------------+
                                    |
            +-----------------------+-----------------------+
            |                                               |
            v                                               v
+-----------------------+                       +-----------------------+
|  REACT NATIVE BRIDGE  |                       |   NODE.JS + POSTGRES  |
|  - AppBlockerModule   |                       |   - Express REST API  |
|  - AppBlockerService  | <--- HTTP Sync -----> |   - PostgreSQL 18 DB  |
|  - BlockOverlayView   |                       |   - Block Event Logs  |
|  - 4-Digit PIN Lock   |                       |   - Interception Stats|
+-----------------------+                       +-----------------------+
```

### 1. Zero-Latency Interception Protocol
* **The Problem:** If React Native JavaScript or an HTTP network request is consulted before deciding to block, Instagram will open for 1–2 seconds before getting blocked.
* **The Solution:** The blocked list is saved directly in Android native `SharedPreferences` by `AppBlockerModule.kt`. The native `AppBlockerAccessibilityService.kt` reads this cache directly on the OS thread in **<1ms**, intercepting the launch before the target app can even render.

### 2. Required Android Permissions
* **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)**: Allows the service to receive window state change events when any foreground app launches.
* **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)**: Allows drawing the full-screen touch-capturing blocking overlay on top of Instagram.
* **Package Query (`QUERY_ALL_PACKAGES`)**: Allows querying installed apps to present the block list to the user.
* **Boot Persistence (`RECEIVE_BOOT_COMPLETED`)**: Auto-starts the service on device reboot.

---

## 🚀 Running the Project

### 1. Start the Node.js + PostgreSQL Backend

The backend is pre-configured to connect to your local PostgreSQL server at `localhost:5432`.

```bash
cd backend
npm install
npm start
```
* API Server will start on `http://127.0.0.1:5000`
* Automatically creates the database `sc_app` and tables if they don't exist yet.

Run integration tests:
```bash
npm test
```

### 2. Run the Mobile App (React Native)

```bash
cd mobile
npm install
npm run android
```

---

## 📱 App Modes & Features

1. **One-Tap Instagram Lock**:
   - Prominently featured on the main dashboard.
   - Instantly locks or unlocks Instagram with a single button.
2. **Direct Mode (No PIN)**:
   - Blocks Instagram directly. When tapped, it pops up: *"Instagram: No, you cannot open it"* and returns to the home screen.
   - To use Instagram again, simply toggle it to unblock inside the FocusShield app.
3. **PIN-Protected Mode**:
   - Set a simple 4-digit PIN.
   - Tapping a blocked app opens the block overlay with a PIN unlock input to prevent impulsive unblocking.
4. **App Scanner**:
   - Scans all installed apps on your device with search & filter.
   - Any app (TikTok, YouTube, Games) can be added to the block list.
5. **Analytics & Logging**:
   - Records every blocked attempt in PostgreSQL.
   - Displays daily blocked attempts on the dashboard.
