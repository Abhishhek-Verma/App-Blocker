const express = require('express');
const cors = require('cors');
require('dotenv').config();

const db = require('./config/db');
const apiRoutes = require('./routes/api');

const app = express();
const PORT = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());

// Health Check
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    postgres_connected: db.isConnected(),
    orm: 'prisma',
    database: 'Neon Cloud PostgreSQL'
  });
});

// Mount API routes
app.use('/api', apiRoutes);

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('[Unhandled Error]', err);
  res.status(500).json({ success: false, error: err.message || 'Internal Server Error' });
});

// Initialize database and start listening if run directly
if (require.main === module) {
  db.initDB().then(() => {
    app.listen(PORT, () => {
      console.log(`[Server] App Blocker API running on http://127.0.0.1:${PORT}`);
      console.log(`[Server] Ready to serve React Native / Android mobile client.`);
    });
  });
}

module.exports = app;

