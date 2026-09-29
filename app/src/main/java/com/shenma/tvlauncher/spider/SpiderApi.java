package com.shenma.tvlauncher.spider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.github.catvod.crawler.Spider;
import com.shenma.tvlauncher.utils.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 把 TVBox 爬虫返回的数据整理成统一的结构，供界面直接使用
 */
public class SpiderApi {

	private static final String TAG = "SpiderApi";

	/** 首页分类 */
	public static JSONArray homeClasses(Spider spider, boolean filter) {
		try {
			String json = spider.homeContent(filter);
			if (json == null) {
				return new JSONArray();
			}
			JSONObject ret = new JSONObject(json);
			JSONArray classes = ret.optJSONArray("classes");
			return classes == null ? new JSONArray() : classes;
		} catch (Throwable e) {
			Logger.e(TAG, "homeContent " + e);
			return new JSONArray();
		}
	}

	/** 首页筛选：classId -> filters */
	public static JSONObject homeFilters(Spider spider) {
		try {
			JSONObject ret = new JSONObject(spider.homeContent(true));
			JSONObject filters = ret.optJSONObject("filters");
			return filters == null ? new JSONObject() : filters;
		} catch (Throwable e) {
			Logger.e(TAG, "homeFilters " + e);
			return new JSONObject();
		}
	}

	/** 首页推荐 */
	public static JSONObject homeVideo(Spider spider) {
		try {
			String json = spider.homeVideoContent();
			JSONObject ret = json == null ? new JSONObject() : new JSONObject(json);
			if (ret.optJSONArray("list") == null) {
				ret.put("list", new JSONArray());
			}
			return ret;
		} catch (Throwable e) {
			Logger.e(TAG, "homeVideoContent " + e);
			return emptyList();
		}
	}

	/** 分类/筛选结果 */
	public static JSONObject category(Spider spider, String tid, String pg, boolean filter, HashMap<String, String> extend) {
		try {
			String json = spider.categoryContent(tid, pg, filter, extend);
			JSONObject ret = json == null ? new JSONObject() : new JSONObject(json);
			if (ret.optJSONArray("list") == null) {
				ret.put("list", new JSONArray());
			}
			return ret;
		} catch (Throwable e) {
			Logger.e(TAG, "categoryContent " + e);
			return emptyList();
		}
	}

	public static JSONObject search(Spider spider, String key, boolean quick) {
		try {
			String json = spider.searchContent(key, quick);
			JSONObject ret = json == null ? new JSONObject() : new JSONObject(json);
			if (ret.optJSONArray("list") == null) {
				ret.put("list", new JSONArray());
			}
			return ret;
		} catch (Throwable e) {
			Logger.e(TAG, "searchContent " + e);
			return emptyList();
		}
	}

	/** 详情，返回 list[0] */
	public static JSONObject detail(Spider spider, String id) {
		try {
			List<String> ids = new ArrayList<String>();
			ids.add(id);
			String json = spider.detailContent(ids);
			JSONObject ret = json == null ? new JSONObject() : new JSONObject(json);
			JSONArray list = ret.optJSONArray("list");
			if (list == null || list.length() == 0) {
				return new JSONObject();
			}
			return list.getJSONObject(0);
		} catch (Throwable e) {
			Logger.e(TAG, "detailContent " + e);
			return new JSONObject();
		}
	}

	/**
	 * 播放解析
	 * 返回 PlayUrl：parse=0 直接播放，1 嗅探，3 web 解析
	 */
	public static PlayUrl play(Spider spider, String flag, String id, List<String> vipFlags) {
		try {
			String json = spider.playerContent(flag, id, vipFlags == null ? new ArrayList<String>() : vipFlags);
			JSONObject ret = json == null ? new JSONObject() : new JSONObject(json);
			String url = ret.optString("url");
			if (url.length() == 0) {
				// 部分爬虫把地址放在 playUrl
				String playUrl = ret.optString("playUrl");
				int p = playUrl.indexOf("?url=");
				url = p > -1 ? playUrl.substring(p + 5) : playUrl;
			}
			int parse = ret.optInt("parse", url.startsWith("http") ? 0 : 1);
			JSONObject header = ret.optJSONObject("header");
			PlayUrl playUrl = new PlayUrl(parse, url);
			if (header != null && header.length() > 0) {
				playUrl.headers = header;
			}
			return playUrl;
		} catch (Throwable e) {
			Logger.e(TAG, "playerContent " + e);
			return new PlayUrl(1, id);
		}
	}

	/** 解析 vod_play_from / vod_play_url */
	public static ArrayList<Line> lines(JSONObject vod) {
		ArrayList<Line> lines = new ArrayList<Line>();
		String from = vod.optString("vod_play_from");
		String url = vod.optString("vod_play_url");
		if (url.length() == 0) {
			return lines;
		}
		String[] flags = from.split("$$$");
		String[] lists = url.split("$$$");
		for (int i = 0; i < lists.length; i++) {
			String[] items = lists[i].split("#");
			Line line = new Line();
			line.setFlag(i < flags.length ? flags[i].trim() : String.valueOf(i));
			for (String item : items) {
				String[] pos = item.split("\\$");
				if (pos.length >= 2) {
					line.getItems().add(new Item(pos[0].trim(), pos[1].trim()));
				} else if (pos.length == 1 && pos[0].trim().length() > 0) {
					line.getItems().add(new Item(String.valueOf(line.getItems().size()), pos[0].trim()));
				}
			}
			if (!line.getItems().isEmpty()) {
				lines.add(line);
			}
		}
		return lines;
	}

	/** 从过滤条件里取出用户选择的 extend */
	public static HashMap<String, String> extend(JSONObject filters, HashMap<String, String> selected) {
		HashMap<String, String> extend = new HashMap<String, String>();
		if (filters == null) {
			return extend;
		}
		for (String key : selected.keySet()) {
			String value = selected.get(key);
			JSONArray options = filters.optJSONArray(key);
			if (options == null) {
				continue;
			}
			for (int i = 0; i < options.length(); i++) {
				JSONObject option = options.optJSONObject(i);
				if (option == null) {
					continue;
				}
				if (option.optString("name").equals(value) || option.optString("value").equals(value)) {
					extend.put(option.optString("key"), option.optString("value"));
				}
			}
		}
		return extend;
	}

	private static JSONObject emptyList() {
		JSONObject ret = new JSONObject();
		try {
			ret.put("list", new JSONArray());
			ret.put("page", 1);
			ret.put("pagecount", 1);
		} catch (Exception e) {
			// ignore
		}
		return ret;
	}

	public static class PlayUrl {
		public int parse;
		public String url;
		public JSONObject headers;

		public PlayUrl(int parse, String url) {
			this.parse = parse;
			this.url = url;
		}
	}

	public static class Line {
		private String flag;
		private final ArrayList<Item> items = new ArrayList<Item>();

		public String getFlag() {
			return flag;
		}

		public void setFlag(String flag) {
			this.flag = flag;
		}

		public ArrayList<Item> getItems() {
			return items;
		}
	}

	public static class Item {
		public final String name;
		public final String id;

		public Item(String name, String id) {
			this.name = name;
			this.id = id;
		}
	}
}
