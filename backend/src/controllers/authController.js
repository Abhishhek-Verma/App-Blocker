const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { prisma } = require('../config/db');
const { JWT_SECRET } = require('../middleware/auth');

/**
 * Register a new user
 * POST /api/auth/register
 * Body: { name, email, password }
 */
async function register(req, res) {
  try {
    const { name, email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({
        success: false,
        error: 'Email and password are required'
      });
    }

    const trimmedEmail = email.trim().toLowerCase();
    const trimmedName = (name || '').trim();

    if (!trimmedEmail.includes('@') || !trimmedEmail.includes('.')) {
      return res.status(400).json({
        success: false,
        error: 'Please enter a valid email address'
      });
    }

    if (password.length < 4) {
      return res.status(400).json({
        success: false,
        error: 'Password must be at least 4 characters'
      });
    }

    // Check if email already registered
    const existingUser = await prisma.user.findUnique({
      where: { email: trimmedEmail }
    });

    if (existingUser) {
      return res.status(409).json({
        success: false,
        error: 'An account with this email already exists. Please log in.'
      });
    }

    // Hash password
    const hashedPassword = await bcrypt.hash(password, 10);

    const newUser = await prisma.user.create({
      data: {
        name: trimmedName || trimmedEmail.split('@')[0],
        email: trimmedEmail,
        password: hashedPassword
      }
    });

    // Generate JWT token
    const token = jwt.sign(
      { userId: newUser.id, email: newUser.email, name: newUser.name },
      JWT_SECRET,
      { expiresIn: '30d' }
    );

    return res.status(201).json({
      success: true,
      message: 'Account created successfully',
      token,
      user: {
        id: newUser.id,
        name: newUser.name,
        email: newUser.email
      }
    });
  } catch (err) {
    console.error('[Auth Register Error]:', err);
    return res.status(500).json({
      success: false,
      error: 'Failed to create account: ' + err.message
    });
  }
}

/**
 * Login user
 * POST /api/auth/login
 * Body: { email, password }
 */
async function login(req, res) {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({
        success: false,
        error: 'Email and password are required'
      });
    }

    const trimmedEmail = email.trim().toLowerCase();

    const user = await prisma.user.findUnique({
      where: { email: trimmedEmail }
    });

    if (!user) {
      return res.status(401).json({
        success: false,
        error: 'Invalid email or password'
      });
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return res.status(401).json({
        success: false,
        error: 'Invalid email or password'
      });
    }

    // Generate JWT token
    const token = jwt.sign(
      { userId: user.id, email: user.email, name: user.name },
      JWT_SECRET,
      { expiresIn: '30d' }
    );

    return res.json({
      success: true,
      message: 'Login successful',
      token,
      user: {
        id: user.id,
        name: user.name,
        email: user.email
      }
    });
  } catch (err) {
    console.error('[Auth Login Error]:', err);
    return res.status(500).json({
      success: false,
      error: 'Authentication failed: ' + err.message
    });
  }
}

/**
 * Verify current JWT token
 * GET /api/auth/me
 */
async function getMe(req, res) {
  try {
    const user = await prisma.user.findUnique({
      where: { id: req.user.userId },
      select: { id: true, name: true, email: true, createdAt: true }
    });

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    return res.json({ success: true, user });
  } catch (err) {
    console.error('[Auth Me Error]:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
}

module.exports = {
  register,
  login,
  getMe
};
