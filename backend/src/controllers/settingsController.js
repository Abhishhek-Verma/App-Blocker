const { prisma } = require('../config/db');

async function getSettings(req, res) {
  try {
    let setting = await prisma.appSetting.findUnique({
      where: { deviceId: 'default_device' }
    });

    if (!setting) {
      setting = await prisma.appSetting.create({
        data: {
          deviceId: 'default_device',
          masterBlockerActive: true,
          requirePinToUnlock: false
        }
      });
    }

    return res.json({
      success: true,
      data: {
        device_id: setting.deviceId,
        master_blocker_active: setting.masterBlockerActive,
        require_pin_to_unlock: setting.requirePinToUnlock,
        has_pin: Boolean(setting.pinCode)
      }
    });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
}

async function updateSettings(req, res) {
  const { master_blocker_active, require_pin_to_unlock, pin_code } = req.body;

  try {
    const data = {};
    if (master_blocker_active !== undefined) {
      data.masterBlockerActive = Boolean(master_blocker_active);
    }
    if (require_pin_to_unlock !== undefined) {
      data.requirePinToUnlock = Boolean(require_pin_to_unlock);
    }
    if (pin_code !== undefined) {
      data.pinCode = pin_code ? String(pin_code).trim() : null;
    }

    const updated = await prisma.appSetting.upsert({
      where: { deviceId: 'default_device' },
      update: data,
      create: {
        deviceId: 'default_device',
        masterBlockerActive: data.masterBlockerActive ?? true,
        requirePinToUnlock: data.requirePinToUnlock ?? false,
        pinCode: data.pinCode ?? null
      }
    });

    return res.json({
      success: true,
      data: {
        device_id: updated.deviceId,
        master_blocker_active: updated.masterBlockerActive,
        require_pin_to_unlock: updated.requirePinToUnlock,
        has_pin: Boolean(updated.pinCode)
      }
    });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
}

async function verifyPin(req, res) {
  const { pin } = req.body;
  if (!pin) {
    return res.status(400).json({ success: false, error: 'PIN is required' });
  }

  try {
    const setting = await prisma.appSetting.findUnique({
      where: { deviceId: 'default_device' }
    });

    if (!setting || !setting.pinCode) {
      return res.json({ success: true, verified: true, message: 'No PIN configured' });
    }

    const isValid = String(setting.pinCode).trim() === String(pin).trim();
    return res.json({
      success: true,
      verified: isValid,
      message: isValid ? 'PIN verified successfully' : 'Incorrect PIN entered'
    });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
}

module.exports = {
  getSettings,
  updateSettings,
  verifyPin
};
