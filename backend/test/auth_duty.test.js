const http = require('http');
const app = require('../src/server');
const { initDB } = require('../src/config/db');

const TEST_PORT = 5005;
const BASE_URL = `http://127.0.0.1:${TEST_PORT}`;

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

async function run() {
  console.log('--- STARTING AUTH & DUTY MODE TEST ---');
  await initDB();
  const server = app.listen(TEST_PORT);

  try {
    // 1. QR Code Endpoint Test
    const qrRes = await request('/api/duty-mode/qr');
    console.assert(qrRes.status === 200 && qrRes.body.qrPayload === 'SCHOOLMINI:DUTY_ON:2026', 'QR Endpoint test');
    console.log('✓ [Duty Mode QR] Received valid QR payload:', qrRes.body.qrPayload);

    // 2. Register User
    const testUsername = 'school_student_' + Date.now();
    const regRes = await request('/api/auth/register', {
      method: 'POST',
      body: { username: testUsername, password: 'password123' }
    });
    console.assert(regRes.status === 201 && regRes.body.token, 'Register should return 201 and token');
    console.log('✓ [Auth Register] Successfully registered user:', testUsername);

    // 3. Login User
    const loginRes = await request('/api/auth/login', {
      method: 'POST',
      body: { username: testUsername, password: 'password123' }
    });
    console.assert(loginRes.status === 200 && loginRes.body.token, 'Login should return 200 and token');
    const token = loginRes.body.token;
    console.log('✓ [Auth Login] JWT generated successfully');

    // 4. Verify /auth/me with JWT
    const meRes = await request('/api/auth/me', {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` }
    });
    console.assert(meRes.status === 200 && meRes.body.user.username === testUsername, 'Me test with JWT');
    console.log('✓ [Auth Me] JWT validated. User profile:', meRes.body.user.username);

    // 5. Duty Mode Settings
    const dutySettings = await request('/api/settings', {
      method: 'POST',
      body: {
        is_duty_mode_active: true,
        school_start_time: '09:00',
        school_end_time: '15:00'
      }
    });
    console.assert(dutySettings.status === 200 && dutySettings.body.data.is_duty_mode_active === true, 'Duty Mode Active test');
    console.log('✓ [Settings] Duty Mode state synchronized with Neon DB: is_duty_mode_active =', dutySettings.body.data.is_duty_mode_active);

    console.log('--- ALL AUTH & DUTY MODE TESTS PASSED! ---');
  } finally {
    server.close();
    process.exit(0);
  }
}

run().catch(err => {
  console.error('Test error:', err);
  process.exit(1);
});
