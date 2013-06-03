package me.shumei.open.oks.discuzx;

import java.io.IOException;
import java.util.HashMap;

import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
	String resultFlag = "false";
	String resultStr = "未知错误！";
	
	/**
	 * <p><b>程序的签到入口</b></p>
	 * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
	 * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
	 * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
	 * @param cfg “配置”栏内输入的数据
	 * @param user 用户名
	 * @param pwd 解密后的明文密码
	 * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
	 */
	public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
		//把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
		CaptchaUtil.context = ctx;
		//标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
		CaptchaUtil.isAutoSign = isAutoSign;
		
		try{
			//论坛首页地址
			String baseUrl = cfg.trim();
			//账号登录地址
			String loginPageUrl = baseUrl + "/member.php?mod=logging&action=login&mobile=yes";
			//账号信息提交地址
			String loginSubmitUrl = baseUrl + "/member.php?mod=logging&action=login&loginsubmit=yes&loginhash=LNvu3&mobile=yes";
			//签到页面地址
			String signPageUrl = baseUrl + "/plugin.php?id=dsu_paulsign:sign";
			//签到信息提交地址
			String signSubmitUrl = baseUrl + "/plugin.php?id=dsu_paulsign:sign&operation=qiandao&infloat=0&inajax=0&mobile=yes";
			
			//存放Cookies的HashMap
			HashMap<String, String> cookies = new HashMap<String, String>();
			//Jsoup的Response
			Response res;
			
			//使用Android的UA模拟手机访问论坛登录页面，比访问电脑版页面省流量
			res = Jsoup.connect(loginPageUrl).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
			//把Jsoup访问页面得到的Cookies全部存入HashMap容器
			//这一步非常重要，以后每使用Jsoup进行一次访问都最好马上把cookies保存起来
			cookies.putAll(res.cookies());
			//获取DiscuzX论坛的formhash验证串
			String formhash = res.parse().getElementsByAttributeValue("name", "formhash").first().val();
			
			//携带Cookies提交登录信息
			res = Jsoup.connect(loginSubmitUrl).data("username", user).data("password", pwd).data("formhash", formhash).data("submit", "登录").cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
			//追加保存登录后获得的Cookies，在后续的访问过程中都会用到
			cookies.putAll(res.cookies());
			
			//访问签到页面
			res = Jsoup.connect(signPageUrl).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
			cookies.putAll(res.cookies());

			//根据签到页面上的文字来判断今天是否已经签到
			if(res.parse().toString().contains("今天已签到"))
			{
				resultFlag = "true";
				//捕捉一下异常，防止获取数据时发生错误
				try {
					Elements paragraphs = res.parse().select("div.bm_c > p");
					resultStr = "今天已签过到\n";
					resultStr += paragraphs.eq(0).text() + "\n";
					resultStr += paragraphs.eq(1).text() + "\n";
					resultStr += paragraphs.eq(4).text() + "\n";
					resultStr += paragraphs.eq(5).text() + "\n";
				} catch (Exception e) {
					resultStr = "今天已签到";
				}
			}
			else
			{
				//获取心情
				String qdxq = getXinQing();
				//获取要发表的话
				String todaysay = getTodaySay();
				//提交签到信息
				try {
					formhash = res.parse().getElementsByAttributeValue("name", "formhash").first().val();
					res = Jsoup.connect(signSubmitUrl)
							.data("qdmode","1")
							.data("formhash",formhash)
							.data("qdxq",qdxq)
							.data("fastreply", "0")
							.data("todaysay", todaysay)
							.cookies(cookies)
							.userAgent(UA_ANDROID)
							.timeout(TIME_OUT)
							.ignoreContentType(true)
							.method(Method.POST)
							.execute();
					
					this.resultFlag = "true";
					try {
						this.resultStr = res.parse().select("div#messagetext > p").eq(0).text();
					} catch (Exception e) {
						this.resultStr = "签到成功";
					}
				} catch (Exception e) {
					this.resultFlag = "false";
					this.resultStr = "签到插件不存在";
				}
			}
			
			
		} catch (IOException e) {
			this.resultFlag = "false";
			this.resultStr = "连接超时";
			e.printStackTrace();
		} catch (Exception e) {
			this.resultFlag = "false";
			this.resultStr = "未知错误！";
			e.printStackTrace();
		}
		
		return new String[]{resultFlag, resultStr};
	}
	
	
	/**
	 * 获取心情，与想说的话没有一一对应关系
	 * @return
	 */
	private String getXinQing()
	{
		//要发的表情，共9个
		//开心，难过，郁闷，无聊，怒，擦汗，奋斗，慵懒，衰
		String[] qdxqArr = {"kx","ng","ym","wl","nu","ch","fd","yl","shuai"};
		int randNum = (int)(Math.random() * 8);
		String qdxq = qdxqArr[randNum];
		return qdxq;
	}
	
	
	/**
	 * 获取今天想要说的话，这个与“心情”没有一一对应关系，
	 * 比如心情是“kx”，这里想说的话可以是“难过.....”
	 * @return
	 */
	private String getTodaySay()
	{
		//要发的表情，共9个
		//开心，难过，郁闷，无聊，怒，擦汗，奋斗，慵懒，衰
		String[] todaysayArr = {"开心~~~","难过.....","郁闷　　","无聊　　","怒!!!!","擦汗-_-","奋斗　　","慵懒　　","衰　　"};
		int randNum = (int)(Math.random() * 8);
		String todaysay = todaysayArr[randNum];
		return todaysay;
	}
	
	
}
