const express = require('express');
const cors = require('cors');
const path = require('path');
require('dotenv').config();

const tsunamiRoutes = require('./routes/tsunami');
const tideRoutes = require('./routes/tide');
const correlationRoutes = require('./routes/correlation');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static('public'));

// API Routes
app.use('/api/tsunami', tsunamiRoutes);
app.use('/api/tide', tideRoutes);
app.use('/api/correlation', correlationRoutes);

// Serve main page
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Error handling middleware
app.use((err, req, res, next) => {
    console.error(err.stack);
    res.status(500).json({ error: 'Something went wrong!' });
});

app.listen(PORT, () => {
    console.log(`🌊 Tsunami-Tide Tracker running on http://localhost:${PORT}`);
    console.log('📊 Real-time monitoring active...');
});