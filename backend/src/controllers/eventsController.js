const { prisma } = require('../config/db');

async function logBlockEvent(req, res) {
  const { package_name, app_name, unlocked_via_pin } = req.body;
  if (!package_name) {
    return res.status(400).json({ success: false, error: 'package_name is required' });
  }

  const resolvedAppName = app_name || package_name.split('.').pop() || 'Unknown App';
  const viaPin = Boolean(unlocked_via_pin);

  try {
    const event = await prisma.blockEvent.create({
      data: {
        packageName: package_name,
        appName: resolvedAppName,
        unlockedViaPin: viaPin
      }
    });
    return res.status(201).json({ success: true, data: event });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
}

async function getStats(req, res) {
  try {
    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);

    const [todayCount, totalCount, topBlocked] = await Promise.all([
      prisma.blockEvent.count({
        where: { eventTimestamp: { gte: startOfToday } }
      }),
      prisma.blockEvent.count(),
      prisma.blockEvent.groupBy({
        by: ['appName', 'packageName'],
        _count: { id: true },
        orderBy: { _count: { id: 'desc' } },
        take: 5
      })
    ]);

    return res.json({
      success: true,
      data: {
        blocked_today: todayCount,
        blocked_total: totalCount,
        top_blocked: topBlocked.map(t => ({
          app_name: t.appName,
          package_name: t.packageName,
          attempts: t._count.id
        }))
      }
    });
  } catch (error) {
    return res.status(500).json({ success: false, error: error.message });
  }
}

module.exports = {
  logBlockEvent,
  getStats
};
