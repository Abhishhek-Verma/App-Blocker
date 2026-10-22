const express = require('express');
const router = express.Router();

const appsController = require('../controllers/appsController');
const settingsController = require('../controllers/settingsController');
const eventsController = require('../controllers/eventsController');
const authController = require('../controllers/authController');
const { authenticateToken } = require('../middleware/auth');

// Authentication (JWT)
router.post('/auth/register', authController.register);
router.post('/auth/login', authController.login);
router.get('/auth/me', authenticateToken, authController.getMe);

// Apps management
router.get('/apps', appsController.getBlockedApps);
router.post('/apps/toggle', appsController.toggleBlockedApp);
router.post('/apps/batch', appsController.batchSyncApps);
router.delete('/apps/:package_name', appsController.deleteBlockedApp);

// Settings & Security
router.get('/settings', settingsController.getSettings);
router.post('/settings', settingsController.updateSettings);
router.post('/settings/verify-pin', settingsController.verifyPin);
router.get('/duty-mode/qr', settingsController.getDutyModeQR);

// Interception logging & statistics
router.post('/events', eventsController.logBlockEvent);
router.get('/stats', eventsController.getStats);

module.exports = router;

