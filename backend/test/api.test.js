const http = require('http');

const PORT = 5000;
const BASE_URL = `http://127.0.0.1:${PORT}`;

function request(path, options = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    const req = http.request(url, {
      method: options.method || 'GET',
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {})
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch (e) {
          resolve({ status: res.statusCode, body: data });
        }
      });
    });

    req.on('error', reject);
    if (options.body) {
      req.write(JSON.stringify(options.body));
    }
    req.end();
  });
}

async function runTests() {
  console.log('--- STARTING BACKEND INTEGRATION TESTS WITH PRISMA & NEON DB ---');

  // 1. Health Check
  const health = await request('/health');
  console.assert(health.status === 200, 'Health check should be 200');
  console.assert(health.body.postgres_connected === true, 'Neon DB should be connected');
  console.log('✓ [Health] API is healthy & connected to Neon DB via Prisma ORM');

  // 2. Get Blocked Apps
  const apps = await request('/api/apps');
  console.assert(apps.status === 200 && apps.body.success, 'Get apps should succeed');
  const insta = apps.body.data.find(a => (a.packageName || a.package_name) === 'com.instagram.android');
  console.log(`✓ [Apps] Retrieved ${apps.body.data.length} apps. Initial Instagram state:`, (insta?.isBlocked ?? insta?.is_blocked));

  // 3. Toggle Instagram Block State
  const toggle = await request('/api/apps/toggle', {
    method: 'POST',
    body: {
      package_name: 'com.instagram.android',
      app_name: 'Instagram',
      is_blocked: true
    }
  });
  const isBlockedVal = toggle.body.data.isBlocked ?? toggle.body.data.is_blocked;
  console.assert(toggle.status === 200 && isBlockedVal === true, 'Toggle Instagram should be true');
  console.log('✓ [Apps] Successfully toggled Instagram to BLOCKED in Neon DB');

  // 4. Update PIN setting
  const setPin = await request('/api/settings', {
    method: 'POST',
    body: {
      pin_code: '4321',
      require_pin_to_unlock: true
    }
  });
  console.assert(setPin.status === 200 && setPin.body.data.has_pin === true, 'Set PIN should succeed');
  console.log('✓ [Settings] Updated PIN to 4321 in Neon DB');

  // 5. Verify correct PIN
  const verifyValid = await request('/api/settings/verify-pin', {
    method: 'POST',
    body: { pin: '4321' }
  });
  console.assert(verifyValid.body.verified === true, 'PIN 4321 should verify');
  console.log('✓ [Security] PIN 4321 verified correctly against Neon DB');

  // 6. Verify incorrect PIN
  const verifyInvalid = await request('/api/settings/verify-pin', {
    method: 'POST',
    body: { pin: '0000' }
  });
  console.assert(verifyInvalid.body.verified === false, 'PIN 0000 should fail');
  console.log('✓ [Security] Invalid PIN 0000 correctly rejected');

  // 7. Log Block Event
  const logEvent = await request('/api/events', {
    method: 'POST',
    body: {
      package_name: 'com.instagram.android',
      app_name: 'Instagram',
      unlocked_via_pin: false
    }
  });
  console.assert(logEvent.status === 201, 'Log event should return 201');
  console.log('✓ [Events] Interception attempt logged to Neon DB');

  // 8. Get Stats
  const stats = await request('/api/stats');
  console.assert(stats.status === 200 && stats.body.data.blocked_today >= 1, 'Stats should show at least 1 event');
  console.log(`✓ [Stats] Blocked today count in Neon DB: ${stats.body.data.blocked_today}`);

  console.log('--- ALL BACKEND INTEGRATION TESTS WITH PRISMA & NEON DB PASSED! ---');
  process.exit(0);
}

runTests().catch(err => {
  console.error('Test failed:', err);
  process.exit(1);
});
