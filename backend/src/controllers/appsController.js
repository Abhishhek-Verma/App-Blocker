const { prisma } = require('../config/db');

async function getBlockedApps(req, res) {
  try {
    const apps = await prisma.blockedApp.findMany({
      orderBy: { id: 'asc' }
    });
    return res.json({ success: true, data: apps });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
}

async function toggleBlockedApp(req, res) {
  const { package_name, app_name, is_blocked } = req.body;

  if (!package_name) {
    return res.status(400).json({ success: false, error: 'package_name is required' });
  }

  const resolvedAppName = app_name || package_name.split('.').pop() || 'Unknown App';
  const newBlockedState = is_blocked !== undefined ? Boolean(is_blocked) : true;

  try {
    const app = await prisma.blockedApp.upsert({
      where: { packageName: package_name },
      update: {
        isBlocked: newBlockedState,
        appName: resolvedAppName
      },
      create: {
        packageName: package_name,
        appName: resolvedAppName,
        isBlocked: newBlockedState
      }
    });

    return res.json({ success: true, data: app });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
}

async function batchSyncApps(req, res) {
  const { apps } = req.body;
  if (!Array.isArray(apps)) {
    return res.status(400).json({ success: false, error: 'apps array is required' });
  }

  try {
    const results = [];
    for (const app of apps) {
      if (!app.package_name) continue;
      const appName = app.app_name || app.package_name;
      const isBlocked = Boolean(app.is_blocked);

      const record = await prisma.blockedApp.upsert({
        where: { packageName: app.package_name },
        update: {
          isBlocked,
          appName
        },
        create: {
          packageName: app.package_name,
          appName,
          isBlocked
        }
      });
      results.push(record);
    }
    return res.json({ success: true, count: results.length, data: results });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
}

async function deleteBlockedApp(req, res) {
  const { package_name } = req.params;
  try {
    await prisma.blockedApp.deleteMany({
      where: { packageName: package_name }
    });
    return res.json({ success: true, message: `Removed ${package_name}` });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
}

module.exports = {
  getBlockedApps,
  toggleBlockedApp,
  batchSyncApps,
  deleteBlockedApp
};
