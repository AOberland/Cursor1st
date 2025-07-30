const express = require('express');
const axios = require('axios');
const moment = require('moment');
const router = express.Router();

// NOAA station mapping for major coastal areas
const STATION_MAP = {
    // US West Coast
    'san_francisco': '9414290',
    'los_angeles': '9410660',
    'seattle': '9447130',
    'san_diego': '9410170',
    
    // US East Coast
    'new_york': '8518750',
    'boston': '8443970',
    'miami': '8723214',
    'charleston': '8665530',
    
    // Hawaii
    'honolulu': '1612340',
    'hilo': '1617760',
    
    // Alaska
    'anchorage': '9455920',
    'juneau': '9452210'
};

// Get tide data for a specific station
router.get('/station/:stationId', async (req, res) => {
    try {
        const { stationId } = req.params;
        const days = req.query.days || 2;
        
        const today = moment().format('YYYYMMDD');
        const endDate = moment().add(parseInt(days), 'days').format('YYYYMMDD');
        
        console.log(`🌊 Fetching tide data for station ${stationId}...`);
        
        const url = `https://api.tidesandcurrents.noaa.gov/api/prod/datagetter`;
        const params = {
            product: 'predictions',
            application: 'NOS.COOPS.TAC.WL',
            station: stationId,
            begin_date: today,
            end_date: endDate,
            datum: 'MLLW',
            units: 'english',
            time_zone: 'lst_ldt',
            format: 'json',
            interval: 'hilo' // High and low tides only
        };

        const response = await axios.get(url, { params, timeout: 10000 });
        
        if (response.data && response.data.predictions) {
            const tides = response.data.predictions.map(prediction => ({
                time: prediction.t,
                height: parseFloat(prediction.v),
                type: prediction.type === 'H' ? 'High' : 'Low',
                timestamp: moment(prediction.t).utc().toISOString()
            }));

            res.json({
                success: true,
                station: stationId,
                timestamp: moment().utc().toISOString(),
                count: tides.length,
                tides: tides,
                metadata: response.data.metadata || {}
            });
        } else {
            throw new Error('No tide data available');
        }

    } catch (error) {
        console.error('❌ Error fetching tide data:', error.message);
        
        // Return mock tide data for testing
        const mockTides = [
            {
                time: moment().add(1, 'hour').format('YYYY-MM-DD HH:mm'),
                height: 4.2,
                type: 'High',
                timestamp: moment().add(1, 'hour').utc().toISOString()
            },
            {
                time: moment().add(7, 'hours').format('YYYY-MM-DD HH:mm'),
                height: 0.8,
                type: 'Low',
                timestamp: moment().add(7, 'hours').utc().toISOString()
            },
            {
                time: moment().add(13, 'hours').format('YYYY-MM-DD HH:mm'),
                height: 4.5,
                type: 'High',
                timestamp: moment().add(13, 'hours').utc().toISOString()
            },
            {
                time: moment().add(19, 'hours').format('YYYY-MM-DD HH:mm'),
                height: 1.1,
                type: 'Low',
                timestamp: moment().add(19, 'hours').utc().toISOString()
            }
        ];

        res.json({
            success: false,
            error: 'NOAA API temporarily unavailable',
            station: req.params.stationId,
            timestamp: moment().utc().toISOString(),
            count: mockTides.length,
            tides: mockTides,
            note: 'This is mock data for demonstration purposes'
        });
    }
});

// Get tide data by location name
router.get('/location/:location', async (req, res) => {
    const location = req.params.location.toLowerCase().replace(/\s+/g, '_');
    const stationId = STATION_MAP[location];
    
    if (!stationId) {
        return res.status(404).json({
            success: false,
            error: 'Location not found',
            availableLocations: Object.keys(STATION_MAP)
        });
    }
    
    // Redirect to station endpoint
    req.params.stationId = stationId;
    return router.handle(req, res);
});

// Get all available stations
router.get('/stations', (req, res) => {
    res.json({
        success: true,
        stations: STATION_MAP,
        count: Object.keys(STATION_MAP).length
    });
});

module.exports = router;