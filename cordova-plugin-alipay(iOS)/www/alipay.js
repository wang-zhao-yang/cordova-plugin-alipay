var exec = require('cordova/exec');

module.exports = {
    pay: function(orderInfo, onSuccess, onError) {
        exec(onSuccess, onError, "Alipay", "pay", [orderInfo]);
    }
};
