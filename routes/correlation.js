const express = require('express');
const axios = require('axios');
const moment = require('moment');
const router = express.Router();

// Correlate tsunami arrival with tide levels
router.get('/analyze', async (req, res) => {
    try {
        console.log('🔍 Analyzing tsunami-tide correlation...');

        // Get tsunami alerts
        const tsunamiResponse = await axios.get('http://localhost:3000/api/tsunami/alerts');
        const tsunamiData = tsunamiResponse.data;

        if (!tsunamiData.alerts || tsunamiData.alerts.length === 0) {
            return res.json({
                success: true,
                message: 'No active tsunami alerts',
                status: 'ALL_CLEAR',
                timestamp: moment().utc().toISOString()
            });
        }

        const correlations = [];

        for (const alert of tsunamiData.alerts) {
            // Find nearest tide station (simplified - using San Francisco for demo)
            const stationId = '9414290'; // San Francisco
            
            try {
                const tideResponse = await axios.get(`http://localhost:3000/api/tide/station/${stationId}`);
                const tideData = tideResponse.data;

                if (tideData.tides && alert.arrivalTime) {
                    const arrivalMoment = moment(alert.arrivalTime);
                    
                    // Find closest tide events
                    const closestTides = tideData.tides
                        .map(tide => ({
                            ...tide,
                            timeDiff: Math.abs(moment(tide.timestamp).diff(arrivalMoment, 'minutes'))
                        }))
                        .sort((a, b) => a.timeDiff - b.timeDiff)
                        .slice(0, 2);

                    const riskLevel = calculateRiskLevel(alert, closestTides);

                    correlations.push({
                        tsunamiAlert: {
                            id: alert.id,
                            location: alert.location,
                            magnitude: alert.magnitude,
                            arrivalTime: alert.arrivalTime,
                            alertLevel: alert.alertLevel
                        },
                        tideConditions: closestTides,
                        riskAssessment: {
                            level: riskLevel.level,
                            score: riskLevel.score,
                            factors: riskLevel.factors,
                            recommendation: riskLevel.recommendation
                        },
                        stationInfo: {
                            id: stationId,
                            name: 'San Francisco'
                        }
                    });
                }
            } catch (tideError) {
                console.log(`⚠️ Could not get tide data for station ${stationId}`);
            }
        }

        res.json({
            success: true,
            timestamp: moment().utc().toISOString(),
            totalAlerts: tsunamiData.alerts.length,
            analyzedCorrelations: correlations.length,
            correlations: correlations,
            status: correlations.length > 0 ? 'ANALYSIS_COMPLETE' : 'NO_CORRELATIONS'
        });

    } catch (error) {
        console.error('❌ Error in correlation analysis:', error.message);
        res.status(500).json({
            success: false,
            error: 'Correlation analysis failed',
            timestamp: moment().utc().toISOString()
        });
    }
});

function calculateRiskLevel(tsunamiAlert, tideData) {
    let score = 0;
    let factors = [];

    // Tsunami magnitude factor
    if (tsunamiAlert.magnitude >= 8.5) {
        score += 40;
        factors.push('Extremely high magnitude earthquake');
    } else if (tsunamiAlert.magnitude >= 8.0) {
        score += 30;
        factors.push('High magnitude earthquake');
    } else if (tsunamiAlert.magnitude >= 7.5) {
        score += 20;
        factors.push('Moderate magnitude earthquake');
    }

    // Alert level factor
    if (tsunamiAlert.alertLevel === 'Red') {
        score += 30;
        factors.push('Red alert level');
    } else if (tsunamiAlert.alertLevel === 'Orange') {
        score += 20;
        factors.push('Orange alert level');
    } else if (tsunamiAlert.alertLevel === 'Yellow') {
        score += 10;
        factors.push('Yellow alert level');
    }

    // Tide conditions factor
    const highTides = tideData.filter(t => t.type === 'High');
    if (highTides.length > 0) {
        const maxHeight = Math.max(...highTides.map(t => t.height));
        if (maxHeight > 5.0) {
            score += 20;
            factors.push('High tide conditions (>5ft)');
        } else if (maxHeight > 3.0) {
            score += 10;
            factors.push('Moderate tide conditions');
        }
    }

    // Determine risk level
    let level, recommendation;
    if (score >= 70) {
        level = 'EXTREME';
        recommendation = 'Immediate evacuation recommended for all coastal areas';
    } else if (score >= 50) {
        level = 'HIGH';
        recommendation = 'Evacuate low-lying coastal areas immediately';
    } else if (score >= 30) {
        level = 'MODERATE';
        recommendation = 'Stay alert, avoid beaches and harbors';
    } else {
        level = 'LOW';
        recommendation = 'Monitor conditions, exercise normal precautions';
    }

    return { level, score, factors, recommendation };
}

module.exports = router;