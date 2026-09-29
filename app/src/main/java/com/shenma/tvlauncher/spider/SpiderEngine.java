package com.shenma.tvlauncher.spider;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.catvod.Init;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.utils.Module;
import com.shenma.tvlauncher.utils.Logger;

import android.text.TextUtils;

import dalvik.system.DexClassLoader;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 爬虫分发：按站点 api/jar 的类型选择 JAR(DexClassLoader) / JS(QuickJS) / Python(Chaquopy)
 * 同一站点复用同一个 Spider 实例，所有方法均为阻塞调用，需在子线程执行
 */
public class SpiderEngine {

	private static final String TAG = "SpiderEngine";

	private static SpiderEngine mInstance;

	public static synchronized SpiderEngine get() {
		if (mInstance == null) {
			mInstance = new SpiderEngine();
		}
		return mInstance;
	}

	private final Map<String, Spider> mJarSpiders = new ConcurrentHashMap<String, Spider>();
	private final Map<String, Spider> mJsSpiders = new ConcurrentHashMap<String, Spider>();
	private final Map<String, Spider> mPySpiders = new ConcurrentHashMap<String, Spider>();
	private final Map<String, DexClassLoader> mDex = new ConcurrentHashMap<String, DexClassLoader>();
	private final Object mLock = new Object();

	/** 取站点对应的爬虫实例，首次会下载/加载 jar、js、py */
	public Spider getSpider(SpiderSite site) {
		if (site == null) {
			return new SpiderNull();
		}
		if (site.isPy()) {
			return getPySpider(site);
		}
		if (site.isJs()) {
			return getJsSpider(site);
		}
		return getJarSpider(site);
	}

	/* ==================== Python ==================== */

	private Spider getPySpider(SpiderSite site) {
		Spider hit = mPySpiders.get(site.key);
		if (hit != null) {
			return hit;
		}
		synchronized (mLock) {
			hit = mPySpiders.get(site.key);
			if (hit != null) {
				return hit;
			}
			try {
				// python 侧按 basename 落到 Path.py() 目录，api 为 http 地址时由 python 自行下载
				String py = site.api.endsWith(".py") ? site.api : site.jar;
				Spider spider = new com.fongmi.chaquo.Loader().spider(py);
				spider.siteKey = site.key;
				spider.init(Init.context(), site.ext);
				mPySpiders.put(site.key, spider);
				return spider;
			} catch (Throwable e) {
				Logger.e(TAG, "py spider fail " + site.key + " " + e);
				return new SpiderNull();
			}
		}
	}

	/* ==================== JavaScript ==================== */

	private Spider getJsSpider(SpiderSite site) {
		Spider hit = mJsSpiders.get(site.key);
		if (hit != null) {
			return hit;
		}
		synchronized (mLock) {
			hit = mJsSpiders.get(site.key);
			if (hit != null) {
				return hit;
			}
			try {
				// api 为 csp_ 形式时 js 由 jar 字段给出，否则 api 本身就是 js 地址
				String js = site.jar.endsWith(".js") ? site.jar : site.api;
				Spider spider = new Loader().spider(js, null);
				spider.siteKey = site.key;
				spider.init(Init.context(), site.ext);
				mJsSpiders.put(site.key, spider);
				return spider;
			} catch (Throwable e) {
				Logger.e(TAG, "js spider fail " + site.key + " " + e);
				return new SpiderNull();
			}
		}
	}

	/* ==================== Java JAR ==================== */

	private Spider getJarSpider(SpiderSite site) {
		Spider hit = mJarSpiders.get(site.key);
		if (hit != null) {
			return hit;
		}
		synchronized (mLock) {
			hit = mJarSpiders.get(site.key);
			if (hit != null) {
				return hit;
			}
			try {
				String cls = site.api.contains("csp_") ? site.api.substring(site.api.indexOf("csp_") + 4) : site.api;
				ClassLoader loader = dex(site.jar);
				if (loader == null) {
					return new SpiderNull();
				}
				Spider spider = (Spider) loader.loadClass("com.github.catvod.spider." + cls).newInstance();
				spider.siteKey = site.key;
				spider.init(Init.context(), site.ext);
				mJarSpiders.put(site.key, spider);
				return spider;
			} catch (Throwable e) {
				Logger.e(TAG, "jar spider fail " + site.key + " " + e);
				return new SpiderNull();
			}
		}
	}

	/** 下载并加载 jar，支持 "url;md5;md5值" 形式 */
	public DexClassLoader dex(String spec) {
		if (TextUtils.isEmpty(spec)) {
			return null;
		}
		String[] texts = spec.split(";md5;");
		String url = texts[0];
		String md5 = texts.length > 1 ? texts[1].trim() : "";
		String key = Util.md5(url);
		DexClassLoader hit = mDex.get(key);
		if (hit != null) {
			return hit;
		}
		synchronized (mLock) {
			hit = mDex.get(key);
			if (hit != null) {
				return hit;
			}
			File file = new File(Path.jar(), key + ".jar");
			try {
				if (md5.startsWith("http")) {
					md5 = OkHttp.string(md5).trim();
				}
				boolean cacheHit = Path.exists(file) && (md5.length() == 0 || md5.equalsIgnoreCase(Util.md5(file)));
				if (!cacheHit) {
					file.delete();
					download(url, file);
				}
				if (!Path.exists(file)) {
					Logger.e(TAG, "jar not found " + url);
					return null;
				}
				String path = Path.jar().getAbsolutePath();
				DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), path, path, Init.context().getClassLoader());
				invokeInit(loader);
				mDex.put(key, loader);
				return loader;
			} catch (Throwable e) {
				Logger.e(TAG, "load jar fail " + url + " " + e);
				return null;
			}
		}
	}

	/** jar 包内若带 Init 类则调用一次 */
	private void invokeInit(ClassLoader loader) {
		try {
			Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
			clz.getMethod("init", android.content.Context.class).invoke(null, Init.context());
		} catch (Throwable e) {
			// 大多数 jar 没有 Init，忽略
		}
	}

	/* ==================== 工具 ==================== */

	private void download(String url, File file) throws Exception {
		if (!url.startsWith("http")) {
			File src = new File(url.replace("file://", ""));
			if (!Path.exists(src)) {
				throw new Exception("file not found " + url);
			}
			Path.copy(src, file);
			return;
		}
		Response res = OkHttp.newCall(url).execute();
		ResponseBody body = res.body();
		if (res.code() != 200 || body == null) {
			throw new Exception("http " + res.code() + " " + url);
		}
		Path.write(file, body.byteStream());
	}

	/** 切换配置时释放全部爬虫 */
	public void clear() {
		destroy(mJarSpiders);
		destroy(mJsSpiders);
		destroy(mPySpiders);
		mJarSpiders.clear();
		mJsSpiders.clear();
		mPySpiders.clear();
		mDex.clear();
		Module.get().clear();
	}

	private void destroy(Map<String, Spider> map) {
		for (Spider spider : map.values()) {
			try {
				spider.destroy();
			} catch (Throwable e) {
				// ignore
			}
		}
	}
}
