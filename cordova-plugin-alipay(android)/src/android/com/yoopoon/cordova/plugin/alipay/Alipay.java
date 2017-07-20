package com.yoopoon.cordova.plugin.alipay;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.alipay.sdk.app.PayTask;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class Alipay extends CordovaPlugin {
    public static String appID;
    public static String rsa_private;
    
    private static final int SDK_PAY_FLAG = 1;
    private static final int SDK_CHECK_FLAG = 2;
    CallbackContext currentCallbackContext;
    
    @Override
    public boolean execute(String action, CordovaArgs args,
                           CallbackContext callbackContext) throws JSONException {
        currentCallbackContext = callbackContext;
        if (action.equals("pay")) {
            return pay(args);
        }
        return true;
    }
    
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        
    }
    
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SDK_PAY_FLAG: {
                    Result payResult = new Result((Map<String, String>) msg.obj);
                    String resultInfo = payResult.getResult();
                    String resultStatus = payResult.getResultStatus();
                    currentCallbackContext.success(resultStatus);
                    break;
                }
                case SDK_CHECK_FLAG: {
                    Toast.makeText(cordova.getActivity(), "检查结果为：" + msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                }
                default:
                    break;
            }
        }
    };

    private boolean pay(CordovaArgs args) {
        try {
            JSONObject orderInfoArgs = args.getJSONObject(0);
            String subject = orderInfoArgs.getString("subject");
            String body = orderInfoArgs.getString("body");
            String price = orderInfoArgs.getString("price");
            String timeout = orderInfoArgs.getString("timeout");
            String notifyUrl = orderInfoArgs.getString("notifyUrl");
            String seller = orderInfoArgs.getString("seller");
			String out_trade_no = orderInfoArgs.getString("out_trade_no");
            appID = orderInfoArgs.getString("appID");
            rsa_private = orderInfoArgs.getString("rsa_private");
			

            Map<String, String> params = buildOrderParamMap(seller, subject, body, price, timeout, notifyUrl, out_trade_no);
            String orderParam = buildOrderParam(params);
            String sign = getSign(params, rsa_private, true);
            final String orderInfo = orderParam + "&" + sign;

            Runnable payRunnable = new Runnable() {
                @Override
                public void run() {
                    PayTask alipay = new PayTask(cordova.getActivity());
                    Map<String, String> result = alipay.payV2(orderInfo, true);
                    Log.i("msp", result.toString());

                    Message msg = new Message();
                    msg.what = SDK_PAY_FLAG;
                    msg.obj = result;
                    mHandler.sendMessage(msg);
                }
            };
            Thread payThread = new Thread(payRunnable);
            payThread.start();
        } catch (JSONException e1) {
            e1.printStackTrace();
            currentCallbackContext.error("订单参数不正确");
        }
        return true;
    }

    /**
     * check whether the device has authentication alipay account.
     * 查询终端设备是否存在支付宝认证账户
     */
    public void check(View v) {
        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                PayTask payTask = new PayTask(cordova.getActivity());
                boolean isExist = true;

                Message msg = new Message();
                msg.what = SDK_CHECK_FLAG;
                msg.obj = isExist;
                mHandler.sendMessage(msg);
            }
        };
        Thread checkThread = new Thread(checkRunnable);
        checkThread.start();
    }

    /**
     * get the sdk version. 获取SDK版本号
     */
    public void getSDKVersion() {
        PayTask payTask = new PayTask(cordova.getActivity());
        String version = payTask.getVersion();
        Toast.makeText(cordova.getActivity(), version, Toast.LENGTH_SHORT).show();
    }

    /**
     * create the order info. 创建订单信息
     */
    private Map<String, String> buildOrderParamMap(String seller, String subject, String body, String price, String timeout, String notifyUrl, String out_trade_no) {
        Map<String, String> keyValues = new HashMap<String, String>();
        keyValues.put("app_id", appID);
        String biz_content = "{\"timeout_express\":\"" + timeout + "\"," +
            "\"seller_id\":\"" + seller + "\"," +
            "\"product_code\":\"QUICK_MSECURITY_PAY\"," +
            "\"total_amount\":\"" + price + "\"," +
            "\"subject\":\"" + subject + "\"," +
            "\"body\":\"" + body + "\"," +
            "\"out_trade_no\":\"" + out_trade_no + "\"}";
        keyValues.put("biz_content", biz_content);
        keyValues.put("charset", "utf-8");
        keyValues.put("method", "alipay.trade.app.pay");
        keyValues.put("sign_type", "RSA2");
        String timestamp = getCurrentDate();
        keyValues.put("timestamp", timestamp);
        keyValues.put("version", "1.0");
		keyValues.put("notify_url", notifyUrl);
        return keyValues;
    }

    private String getOutTradeNo() {
        SimpleDateFormat format = new SimpleDateFormat("MMddHHmmss", Locale.getDefault());
        Date date = new Date();
        String key = format.format(date);

        Random r = new Random();
        key = key + r.nextInt();
        key = key.substring(0, 15);
        return key;
    }

    private String getCurrentDate() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date currentDate = new Date(System.currentTimeMillis());
        return formatter.format(currentDate);
    }

    private String buildOrderParam(Map<String, String> map) {
        List<String> keys = new ArrayList<String>(map.keySet());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size() - 1; i++) {
            String key = keys.get(i);
            String value = map.get(key);
            sb.append(buildKeyValue(key, value, true));
            sb.append("&");
        }

        String tailKey = keys.get(keys.size() - 1);
        String tailValue = map.get(tailKey);
        sb.append(buildKeyValue(tailKey, tailValue, true));
        return sb.toString();
    }

    private String buildKeyValue(String key, String value, boolean isEncode) {
        StringBuilder sb = new StringBuilder();
        sb.append(key);
        sb.append("=");
        if (isEncode) {
            try {
                sb.append(URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                sb.append(value);
            }
        } else {
            sb.append(value);
        }
        return sb.toString();
    }

    private String getSign(Map<String, String> map, String rsaKey, boolean rsa2) {
        List<String> keys = new ArrayList<String>(map.keySet());
        Collections.sort(keys);
        StringBuilder authInfo = new StringBuilder();
        for (int i = 0; i < keys.size() - 1; i++) {
            String key = keys.get(i);
            String value = map.get(key);
            authInfo.append(buildKeyValue(key, value, false));
            authInfo.append("&");
        }

        String tailKey = keys.get(keys.size() - 1);
        String tailValue = map.get(tailKey);
        authInfo.append(buildKeyValue(tailKey, tailValue, false));
        String oriSign = SignUtils.sign(authInfo.toString(), rsaKey, rsa2);
        String encodedSign = "";

        try {
            encodedSign = URLEncoder.encode(oriSign, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "sign=" + encodedSign;
    }
}
