const express = require('express');
const axios = require('axios');
const moment = require('moment');
const router = express.Router();

// Get tsunami alerts from GDACS
router.get('/alerts', async (req, res) => {
    try {
        console.log('🌊 Fetching tsunami alerts from GDACS...');
        
        const response = await axios.get('https://www.gdacs.org/gdacsapi/api/events/geteventlist/tsunami', {
            timeout: 10000,
            headers: {
                'User-Agent': 'Tsunami-Tide-Tracker/1.0'
            }
        });

        let alerts = [];
        
        if (response.data && response.data.features) {
            alerts = response.data.features.map(event => ({
                id: event.properties.eventid,
                episodeId: event.properties.episodeid,
                type: event.properties.eventtype,
                alertLevel: event.properties.alertlevel,
                fromDate: event.properties.fromdate,
                arrivalTime: event.properties.arrivaltime,
                latitude: event.properties.latitude || event.geometry?.coordinates[1],
                longitude: event.properties.longitude || event.geometry?.coordinates[0],
                magnitude: event.properties.magnitude,
                depth: event.properties.depth,
                location: event.properties.country || event.properties.name,
                description: event.properties.description,
                severity: event.properties.severitydata,
                timestamp: moment().utc().toISOString()
            }));
        }

        // Filter for active high-priority alerts
        const criticalAlerts = alerts.filter(alert => 
            alert.alertLevel && ['Red', 'Orange', 'Yellow'].includes(alert.alertLevel)
        );

        console.log(`📊 Found ${alerts.length} total alerts, ${criticalAlerts.length} critical`);

        res.json({
            success: true,
            timestamp: moment().utc().toISOString(),
            total: alerts.length,
            critical: criticalAlerts.length,
            alerts: criticalAlerts.length > 0 ? criticalAlerts : alerts.slice(0, 5), // Show recent if no critical
            status: criticalAlerts.length > 0 ? 'ACTIVE_ALERTS' : 'MONITORING'
        });

    } catch (error) {
        console.error('❌ Error fetching tsunami data:', error.message);
        
        // Return mock data for testing when API is unavailable
        const mockAlert = {
            id: 'TEST_' + Date.now(),
            episodeId: 'MOCK_123',
            type: 'EQ',
            alertLevel: 'Orange',
            fromDate: moment().utc().toISOString(),
            arrivalTime: moment().utc().add(2, 'hours').toISOString(),
            latitude: 37.7749,
            longitude: -122.4194,
            magnitude: 8.2,
            depth: 10,
            location: 'San Francisco Bay Area (TEST)',
            description: 'Test tsunami alert for demonstration',
            severity: 'High',
            timestamp: moment().utc().toISOString()
        };

        res.json({
            success: false,
            error: 'API temporarily unavailable',
            timestamp: moment().utc().toISOString(),
            total: 1,
            critical: 1,
            alerts: [mockAlert],
            status: 'TESTING_MODE',
            note: 'This is mock data for demonstration purposes'
        });
    }
});

module.exports = router;