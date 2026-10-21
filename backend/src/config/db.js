const { PrismaClient } = require('@prisma/client');
require('dotenv').config();

const prisma = new PrismaClient({
  log: process.env.NODE_ENV === 'development' ? ['query', 'error', 'warn'] : ['error']
});

let isConnected = false;

async function initDB() {
  try {
    await prisma.$connect();
    isConnected = true;
    console.log('[Prisma] Connected to Neon PostgreSQL successfully!');

    // Seed default settings and Instagram if not present
    await prisma.appSetting.upsert({
      where: { deviceId: 'default_device' },
      update: {},
      create: {
        deviceId: 'default_device',
        masterBlockerActive: true,
        requirePinToUnlock: false
      }
    });

    await prisma.blockedApp.upsert({
      where: { packageName: 'com.instagram.android' },
      update: {},
      create: {
        packageName: 'com.instagram.android',
        appName: 'Instagram',
        isBlocked: true
      }
    });

    console.log('[Prisma] Default settings and Instagram pre-seeded in Neon DB.');
  } catch (err) {
    isConnected = false;
    console.error('[Prisma Connection Error]:', err.message);
  }
}

module.exports = {
  prisma,
  initDB,
  isConnected: () => isConnected
};
