//
//  CDVAlipay.m
//  X5
//
//  Created by 007slm on 12/8/14.
//
//

#import "Alipay.h"
#import "Order.h"
#import "RSADataSigner.h"
#import <AlipaySDK/AlipaySDK.h>

@implementation Alipay

- (void)handleOpenURL:(NSNotification *)notification {
    NSURL* url = [notification object];
    //跳转支付宝钱包进行支付，需要将支付宝钱包的支付结果回传给SDK
    if (url!=nil && [url.host isEqualToString:@"safepay"]) {
        [[AlipaySDK defaultService]
         processOrderWithPaymentResult:url
         standbyCallback:^(NSDictionary *resultDic) {
//             NSLog(@"result = %@", resultDic);
             CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsString:[NSString stringWithFormat:@"%@",resultDic[@"resultStatus"]]];
             [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
             [self endForExec];
         }];
    }
}

- (void)pluginInitialize {
    
}

-(void) prepareForExec:(CDVInvokedUrlCommand *)command{
    self.currentCallbackId = command.callbackId;
    
}

-(NSDictionary *)checkArgs:(CDVInvokedUrlCommand *) command{
    // check arguments
    NSDictionary *params = [command.arguments objectAtIndex:0];
    if (!params)
    {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"参数错误"] callbackId:command.callbackId];
        
        [self endForExec];
        return nil;
    }
    return params;
}

-(void) endForExec{
    self.currentCallbackId = nil;
}
- (NSString *)generateTradeNO
{
    static int kNumber = 15;
    
    NSString *sourceStr = @"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    NSMutableString *resultStr = [[NSMutableString alloc] init];
    srand((unsigned)time(0));
    for (int i = 0; i < kNumber; i++)
    {
        unsigned index = rand() % [sourceStr length];
        NSString *oneStr = [sourceStr substringWithRange:NSMakeRange(index, 1)];
        [resultStr appendString:oneStr];
    }
    return resultStr;
}


- (void)pay:(CDVInvokedUrlCommand*)command{
    [self prepareForExec:command];
    [self pay1:command];
}

- (void)pay1:(CDVInvokedUrlCommand *)command{
    NSDictionary *orderInfoArgs = [self checkArgs:command];
    NSString *appID = orderInfoArgs[@"appID"];
    NSString *rsa_private = orderInfoArgs[@"rsa_private"];
    NSString *body = orderInfoArgs[@"body"];
    NSString *subject = orderInfoArgs[@"subject"];
    NSString *timeout = orderInfoArgs[@"timeout"];
    NSString *price = orderInfoArgs[@"price"];
    NSString *seller = orderInfoArgs[@"seller"];
    NSString *notifyUrl = orderInfoArgs[@"notifyUrl"];
    NSString *appScheme = orderInfoArgs[@"appScheme"];
    NSString *out_trade_no = orderInfoArgs[@"out_trade_no"];

    Order *order = [[Order alloc] init];
    order.app_id = appID;
    order.method = @"alipay.trade.app.pay";
    order.charset = @"utf-8";
    NSDateFormatter* formatter = [NSDateFormatter new];
    [formatter setDateFormat:@"yyyy-MM-dd HH:mm:ss"];
    order.timestamp = [formatter stringFromDate:[NSDate date]];
    order.version = @"1.0";
    order.sign_type = @"RSA2";
    order.biz_content = [[BizContent alloc] init];
    order.biz_content.body = body;
    order.biz_content.subject = subject;
    order.biz_content.out_trade_no = out_trade_no;
    order.biz_content.timeout_express = timeout;
    order.biz_content.total_amount = price;
    order.biz_content.seller_id = seller;
    order.notify_url = notifyUrl;
    
    //将商品信息拼接成字符串
    NSString *orderInfo = [order orderInfoEncoded:NO];
    NSString *orderInfoEncoded = [order orderInfoEncoded:YES];
//    NSLog(@"orderSpec = %@",orderInfo);
    
    //获取私钥并将商户信息签名,外部商户可以根据情况存放私钥和签名,只需要遵循RSA签名规范,并将签名字符串base64编码和UrlEncode
    RSADataSigner *signer = [[RSADataSigner alloc] initWithPrivateKey:rsa_private];
    NSString *signedString = [signer signString:orderInfo withRSA2:YES];
    
    // NOTE: 如果加签成功，则继续执行支付
    if (signedString != nil) {
        // NOTE: 将签名成功字符串格式化为订单字符串,请严格按照该格式
        NSString *orderString = [NSString stringWithFormat:@"%@&sign=%@", orderInfoEncoded, signedString];
        
//        NSLog(@"%@", orderString);
        // NOTE: 调用支付结果开始支付
        [[AlipaySDK defaultService] payOrder:orderString fromScheme:appScheme callback:^(NSDictionary *resultDic) {
//            NSLog(@"reslut = %@",resultDic);
            CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsString:[NSString stringWithFormat:@"%@",resultDic[@"resultStatus"]]];
            [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
            [self endForExec];
        }];
    }
}

@end
