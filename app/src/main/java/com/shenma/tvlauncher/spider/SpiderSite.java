package com.shenma.tvlauncher.spider;

import org.json.JSONObject;

/**
 * TVBox 配置里的爬虫站点（type=3）
 * 支持三种形态：Java JAR（csp_ClassName）、JavaScript（.js）、Python（.py）
 */
public class SpiderSite {

	public String key = "";
	public String name = "";
	public int type = 3;
	/** csp_ClassName / .js 地址 / .py 地址 */
	public String api = "";
	/** jar(.jar/.js/.py) 地址，为空时取配置级 spider */
	public String jar = "";
	/** 初始化参数，JSON 对象会被序列化成字符串 */
	public String ext = "";
	public boolean searchable = true;
	public boolean filterable = true;
	public int timeout = 0;
	/** 配置地址，./ 相对路径的基地址 */
	public String base = "";

	public static SpiderSite from(JSONObject o, String spider, String base) {
		SpiderSite site = new SpiderSite();
		site.base = base == null ? "" : base;
		site.key = o.optString("key", "");
		site.name = o.optString("name", site.key);
		if (site.key.length() == 0) {
			site.key = site.name;
		}
		if (site.name.length() > 14) {
			site.name = site.name.substring(0, 14);
		}
		site.type = o.optInt("type", 3);
		site.api = site.resolve(o.optString("api", ""));
		site.jar = site.resolve(o.optString("jar", ""));
		if (site.jar.length() == 0) {
			site.jar = site.resolve(spider);
		}
		site.ext = extToString(o.opt("ext"));
		site.searchable = o.optInt("searchable", 1) != 0;
		site.filterable = o.optInt("filterable", 1) != 0;
		site.timeout = o.optInt("timeout", 0);
		return site;
	}

	/** ext 允许是字符串、JSON 对象或数字，统一转字符串 */
	private static String extToString(Object ext) {
		if (ext == null || ext == JSONObject.NULL) {
			return "";
		}
		if (ext instanceof JSONObject) {
			return ext.toString();
		}
		return String.valueOf(ext).trim();
	}

	/** ./ 相对路径转绝对路径 */
	public String resolve(String url) {
		if (url == null) {
			return "";
		}
		if (url.startsWith("./")) {
			return base + url.substring(2);
		}
		return url;
	}

	public boolean isCsp() {
		return api.startsWith("csp_");
	}

	public boolean isPy() {
		return api.endsWith(".py") || jar.endsWith(".py");
	}

	public boolean isJs() {
		return api.endsWith(".js") || jar.endsWith(".js");
	}

	public boolean isJar() {
		return isCsp() && !isPy() && !isJs();
	}

	/** 爬虫所在文件名，用于缓存与日志 */
	public String cacheName() {
		if (isPy()) {
			return fileName(api);
		}
		if (isJs() && !isCsp()) {
			return fileName(api);
		}
		return jar.replaceAll("[^A-Za-z0-9.\\-]", "_");
	}

	private static String fileName(String url) {
		int p = url.lastIndexOf('/');
		return p > -1 ? url.substring(p + 1) : url;
	}
}
