package com.shenma.tvlauncher.netsource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.shenma.tvlauncher.spider.SpiderSite;
import com.shenma.tvlauncher.utils.Constant;

import android.content.Context;
import android.util.Log;

/**
 * TVBox 风格网络源配置加载器
 * 在线为主（forever.json），失败时依次回退：本地缓存 -> assets 内置 forever.json
 * 解析出 lives（直播列表）与 sites（点播 CMS 接口）
 */
public class TvBoxConfig {

	private static final String TAG = "TvBoxConfig";
	private static final String CACHE_FILE = "tvbox_config.json";

	public static class Site {
		public String name;
		public String api;
	}

	public static class Live {
		public String name;
		public String url;
	}

	private static ArrayList<Site> mSites;
	private static ArrayList<Live> mLives;
	private static ArrayList<SpiderSite> mSpiders;

	/**
	 * 获取点播站点列表（只保留 type=1 且为 http 直连 CMS 接口的源）
	 */
	public static synchronized ArrayList<Site> getSites(Context ctx) {
		ensureLoaded(ctx);
		return mSites;
	}

	/**
	 * 获取爬虫站点列表（type=3，支持 jar / js / py）
	 */
	public static synchronized ArrayList<SpiderSite> getSpiders(Context ctx) {
		ensureLoaded(ctx);
		return mSpiders;
	}

	/**
	 * 获取直播频道列表
	 */
	public static synchronized ArrayList<Live> getLives(Context ctx) {
		ensureLoaded(ctx);
		return mLives;
	}

	private static void ensureLoaded(Context ctx) {
		if (mSites != null && mLives != null && mSpiders != null) {
			return;
		}
		mSites = new ArrayList<Site>();
		mLives = new ArrayList<Live>();
		mSpiders = new ArrayList<SpiderSite>();

		// 1. 在线拉取
		String json = null;
		try {
			json = fetchText(Constant.TVBOX_CONFIG_URL, 12000);
		} catch (Exception e) {
			Log.w(TAG, "online config failed: " + e);
		}
		// 2. 在线成功则写缓存
		if (json != null && json.length() > 0) {
			if (parseJson(json)) {
				writeCache(ctx, json);
				return;
			}
		}
		// 3. 过期/离线时读缓存
		String cache = readCache(ctx);
		if (cache != null && parseJson(cache)) {
			return;
		}
		// 4. 兜底：assets 内置配置
		try {
			json = readAsset(ctx, "forever.json");
			parseJson(json);
		} catch (Exception e) {
			Log.e(TAG, "asset config failed", e);
		}
	}

	private static boolean parseJson(String json) {
		if (json == null || json.length() == 0) {
			return false;
		}
		try {
			JSONObject root = new JSONObject(json);
			ArrayList<Site> sites = new ArrayList<Site>();
			ArrayList<Live> lives = new ArrayList<Live>();
			ArrayList<SpiderSite> spiders = new ArrayList<SpiderSite>();
			// 配置级 spider（jar 地址），站点 jar 为空时继承
			String spider = root.optString("spider", "");
			String base = configBase();

			// 点播：type=1 且为 http(s) 直连接口（排除 py/js/php 脚本源与 xml 源）
			JSONArray sa = root.optJSONArray("sites");
			if (sa != null) {
				for (int i = 0; i < sa.length(); i++) {
					JSONObject s = sa.optJSONObject(i);
					if (s == null) {
						continue;
					}
					int type = s.optInt("type", -1);
					// 爬虫：type=3，api 为 csp_ClassName 或 .js / .py 地址
					if (type == 3) {
						SpiderSite spiderSite = SpiderSite.from(s, spider, base);
						if (spiderSite.api.length() == 0) {
							continue;
						}
						spiders.add(spiderSite);
						continue;
					}
					if (type != 1) {
						continue;
					}
					String api = s.optString("api", "");
					if (!api.startsWith("http://") && !api.startsWith("https://")) {
						continue;
					}
					String low = api.toLowerCase();
					if (low.endsWith(".py") || low.endsWith(".js") || low.endsWith(".php")
							|| low.contains("/at/xml")) {
						continue;
					}
					Site site = new Site();
					site.name = s.optString("name", "未命名");
					if (site.name.length() > 14) {
						site.name = site.name.substring(0, 14);
					}
					site.api = api;
					sites.add(site);
				}
			}

			// 直播：name + url（支持 ./ 相对路径）
			JSONArray la = root.optJSONArray("lives");
			if (la != null) {
				for (int i = 0; i < la.length(); i++) {
					JSONObject l = la.optJSONObject(i);
					if (l == null) {
						continue;
					}
					String url = l.optString("url", "");
					if (url.length() == 0) {
						continue;
					}
					Live live = new Live();
					live.name = l.optString("name", "直播");
					if (live.name.length() > 14) {
						live.name = live.name.substring(0, 14);
					}
					live.url = absUrl(url);
					lives.add(live);
				}
			}

			if (sites.isEmpty() && lives.isEmpty() && spiders.isEmpty()) {
				return false;
			}
			mSites = sites;
			mLives = lives;
			mSpiders = spiders;
			return true;
		} catch (Exception e) {
			Log.w(TAG, "parse config error: " + e);
			return false;
		}
	}

	/** 配置内 ./ 相对路径转绝对路径 */
	public static String absUrl(String u) {
		if (u != null && u.startsWith("./")) {
			return configBase() + u.substring(2);
		}
		return u;
	}

	/** 配置地址所在目录，jar/js/py 的相对路径以此为基地址 */
	public static String configBase() {
		String base = Constant.TVBOX_CONFIG_URL;
		return base.substring(0, base.lastIndexOf('/') + 1);
	}

	/**
	 * 抓取文本（UTF-8 优先，检测乱码回退 GBK）
	 */
	public static String fetchText(String urlStr, int timeout) throws Exception {
		byte[] data = fetchBytes(urlStr, timeout);
		if (data == null) {
			return null;
		}
		String text = new String(data, "UTF-8");
		if (text.indexOf('\uFFFD') >= 0) {
			try {
				text = new String(data, "GBK");
			} catch (Exception e) {
			}
		}
		return text;
	}

	/**
	 * 抓取原始字节（图片等）
	 */
	public static byte[] fetchBytes(String urlStr, int timeout) throws Exception {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(urlStr);
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(timeout);
			conn.setReadTimeout(timeout);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SMTVLauncher");
			InputStream is = conn.getInputStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int len;
			while ((len = is.read(buf)) != -1) {
				bos.write(buf, 0, len);
			}
			is.close();
			return bos.toByteArray();
		} finally {
			if (conn != null) {
				try {
					conn.disconnect();
				} catch (Exception e) {
				}
			}
		}
	}

	private static void writeCache(Context ctx, String json) {
		try {
			File f = new File(ctx.getFilesDir(), CACHE_FILE);
			FileOutputStream fos = new FileOutputStream(f);
			fos.write(json.getBytes("UTF-8"));
			fos.close();
		} catch (Exception e) {
			Log.w(TAG, "write cache error: " + e);
		}
	}

	private static String readCache(Context ctx) {
		try {
			File f = new File(ctx.getFilesDir(), CACHE_FILE);
			if (!f.exists()) {
				return null;
			}
			FileInputStream fis = new FileInputStream(f);
			byte[] buf = new byte[(int) f.length()];
			fis.read(buf);
			fis.close();
			return new String(buf, "UTF-8");
		} catch (Exception e) {
			return null;
		}
	}

	private static String readAsset(Context ctx, String name) throws Exception {
		InputStream is = ctx.getAssets().open(name);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int len;
		while ((len = is.read(buf)) != -1) {
			bos.write(buf, 0, len);
		}
		is.close();
		return new String(bos.toByteArray(), "UTF-8");
	}
}
