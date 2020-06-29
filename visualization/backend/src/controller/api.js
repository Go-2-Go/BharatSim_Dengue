const router = require('express').Router();
const csvService = require ('../services/csvService.js')

router.get('/', (req, res) => res.send('Hello World! Welcome'))

router.get('/data', function (req, res) {
    res.json(csvService.getData())
})

module.exports=router;