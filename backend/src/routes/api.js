const express = require('express');
const router = express.Router();

const appsController = require('../controllers/appsController');
const settingsController = require('../controllers/settingsController');
const eventsController = require('../controllers/eventsController');

// Apps management
router.get('/apps', appsController.getBlockedApps);
router.post('/apps/toggle', appsController.toggleBlockedApp);
router.post('/apps/batch', appsController.batchSyncApps);
router.delete('/apps/:package_name', appsController.deleteBlockedApp);

// Settings & Security
router.get('/settings', settingsController.getSettings);
router.post('/settings', settingsController.updateSettings);
router.post('/settings/verify-pin', settingsController.verifyPin);

// Interception logging & statistics
router.post('/events', eventsController.logBlockEvent);
router.get('/stats', eventsController.getStats);

module.exports = router;
