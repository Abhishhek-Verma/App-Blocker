const QRCode = require('qrcode');
const fs = require('fs');
const path = require('path');

const QR_PAYLOAD = 'SCHOOLMINI:DUTY_ON:2026';

const artifactDir = 'C:\\Users\\Abhishek Verma\\.gemini\\antigravity-ide\\brain\\ee91d1ea-01ba-4a4d-a3cc-e1fccbff5b3b';
const distDir = path.resolve(__dirname, '../../dist');

async function generate() {
  if (!fs.existsSync(artifactDir)) {
    fs.mkdirSync(artifactDir, { recursive: true });
  }
  if (!fs.existsSync(distDir)) {
    fs.mkdirSync(distDir, { recursive: true });
  }

  const artifactQrPath = path.join(artifactDir, 'schoolmini_duty_mode_qr.png');
  const distQrPath = path.join(distDir, 'schoolmini_duty_mode_qr.png');

  // Options for high quality scannable QR
  const options = {
    errorCorrectionLevel: 'H',
    type: 'image/png',
    quality: 1.0,
    margin: 3,
    color: {
      dark: '#050811',
      light: '#FFFFFF'
    },
    width: 600
  };

  await QRCode.toFile(artifactQrPath, QR_PAYLOAD, options);
  await QRCode.toFile(distQrPath, QR_PAYLOAD, options);

  const qrDataUrl = await QRCode.toDataURL(QR_PAYLOAD, options);

  // Generate a standalone HTML display page
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>SchoolMini - Official Duty Mode Activation QR</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: #090D16;
      color: #F8FAFC;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }
    .card {
      background: #111827;
      border: 1px solid #1F2937;
      border-radius: 24px;
      padding: 36px 32px;
      max-width: 440px;
      width: 100%;
      text-align: center;
      box-shadow: 0 20px 40px rgba(0,0,0,0.6);
    }
    .badge {
      display: inline-block;
      background: #064E3B;
      color: #6EE7B7;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      padding: 6px 14px;
      border-radius: 9999px;
      margin-bottom: 16px;
    }
    h1 {
      font-size: 26px;
      font-weight: 800;
      letter-spacing: -0.02em;
      margin-bottom: 8px;
    }
    p.subtitle {
      color: #94A3B8;
      font-size: 14px;
      line-height: 1.5;
      margin-bottom: 24px;
    }
    .qr-container {
      background: #FFFFFF;
      padding: 16px;
      border-radius: 20px;
      display: inline-block;
      margin-bottom: 24px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.25);
    }
    .qr-container img {
      display: block;
      width: 260px;
      height: 260px;
    }
    .code-box {
      background: #0B0F19;
      border: 1px dashed #374151;
      border-radius: 12px;
      padding: 12px;
      font-family: monospace;
      font-size: 14px;
      color: #38BDF8;
      margin-bottom: 20px;
      word-break: break-all;
    }
    .whitelist {
      background: #1F2937;
      border-radius: 14px;
      padding: 16px;
      text-align: left;
      font-size: 13px;
    }
    .whitelist-title {
      font-weight: 700;
      color: #E2E8F0;
      margin-bottom: 8px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .whitelist ul {
      list-style-type: none;
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 6px;
      color: #A7F3D0;
    }
    .whitelist li::before {
      content: "✓ ";
      color: #10B981;
      font-weight: bold;
    }
    .footer {
      margin-top: 24px;
      color: #64748B;
      font-size: 12px;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">SchoolMini Security Guard</div>
    <h1>Duty Mode Activation</h1>
    <p class="subtitle">Scan this QR code with the SchoolMini app camera to instantly activate full-device Duty Mode firewall.</p>
    
    <div class="qr-container">
      <img src="${qrDataUrl}" alt="Duty Mode QR Code" />
    </div>

    <div class="code-box">${QR_PAYLOAD}</div>

    <div class="whitelist">
      <div class="whitelist-title">🛡️ Permitted Apps in Duty Mode:</div>
      <ul>
        <li>Phone / Dialer</li>
        <li>Messages (SMS)</li>
        <li>Google Chrome</li>
        <li>Camera</li>
        <li>ChatGPT / Gemini</li>
        <li>SchoolMini App</li>
      </ul>
      <p style="margin-top: 10px; font-size: 11px; color: #94A3B8;">All other games, social media, and distracting apps will be blocked until school ends at 3:00 PM.</p>
    </div>

    <div class="footer">School Session Hours: 09:00 AM - 03:00 PM</div>
  </div>
</body>
</html>`;

  fs.writeFileSync(path.join(distDir, 'qr_display.html'), html);
  console.log('✓ QR code generated successfully at:', artifactQrPath);
  console.log('✓ QR code also generated at:', distQrPath);
  console.log('✓ Display page generated at:', path.join(distDir, 'qr_display.html'));
}

generate().catch(console.error);
