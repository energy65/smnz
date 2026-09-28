package com.shenma.tvlauncher.netsource;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import com.shenma.tvlauncher.utils.Logger;

import android.content.Context;

/**
 * 把 TVBox 配置的直播列表（TVBox txt / M3U 频道表）转换为
 * SMTVLauncher 直播播放器使用的 data.xml 格式
 *
 * 输出结构与 NetMediaXmlParse 对应：
 * <list>
 *   <class classname="分组">
 *     <channel name="频道" epg="">
 *       <tvlink link="播放地址" source="线路1"/>
 *     </channel>
 *   </class>
 * </list>
 */
public class TvBoxLiveLoader {

	private static final String TAG = "TvBoxLiveLoader";

	public static class Channel {
		public String group = "";
		public String name = "";
		public String url = "";
		public ArrayList<String> urls;// 多线路地址（含 url），mergeChannels 后使用
	}

	/**
	 * 拉取 forever.json 的 lives 并生成 data.xml（阻塞方法，需在子线程调用）
	 * @return 是否成功生成
	 */
	public static boolean buildDataXml(Context ctx) {
		ArrayList<TvBoxConfig.Live> lives = TvBoxConfig.getLives(ctx);
		ArrayList<Channel> all = new ArrayList<Channel>();
		for (TvBoxConfig.Live live : lives) {
			try {
				String text = TvBoxConfig.fetchText(live.url, 15000);
				if (text == null || text.length() == 0) {
					continue;
				}
				ArrayList<Channel> chans;
				if (text.contains("#EXTM3U") || text.contains("#EXTINF")) {
					chans = parseM3U(text);
				} else {
					chans = parseLiveTxt(text);
				}
				Logger.d(TAG, "live[" + live.name + "] channels=" + chans.size());
				all.addAll(chans);
			} catch (Exception e) {
				Logger.w(TAG, "live[" + live.name + "] load failed: " + e);
			}
		}
		if (all.isEmpty()) {
			return false;
		}
		return writeDataXml(ctx, all);
	}

	/**
	 * 拉取指定地址的直播频道表（TVBox txt / M3U），同名频道合并为多线路后生成 data.xml
	 * （阻塞方法，需在子线程调用）
	 * @return 是否成功生成
	 */
	public static boolean buildDataXmlFromUrl(Context ctx, String url) {
		ArrayList<Channel> all = new ArrayList<Channel>();
		try {
			String text = TvBoxConfig.fetchText(url, 15000);
			if (text != null && text.length() > 0) {
				if (text.contains("#EXTM3U") || text.contains("#EXTINF")) {
					all = parseM3U(text);
				} else {
					all = parseLiveTxt(text);
				}
			}
		} catch (Exception e) {
			Logger.w(TAG, "live url load failed: " + e);
		}
		all = mergeChannels(all);
		if (all.isEmpty()) {
			return false;
		}
		return writeDataXml(ctx, all);
	}

	/** 同名频道合并：保留首次出现的分组与顺序，url 汇总为 urls 线路列表 */
	public static ArrayList<Channel> mergeChannels(ArrayList<Channel> src) {
		ArrayList<Channel> out = new ArrayList<Channel>();
		HashMap<String, Channel> idx = new HashMap<String, Channel>();
		for (Channel c : src) {
			Channel hit = idx.get(c.name);
			if (hit == null) {
				c.urls = new ArrayList<String>();
				c.urls.add(c.url);
				idx.put(c.name, c);
				out.add(c);
			} else {
				hit.urls.add(c.url);
			}
		}
		return out;
	}

	/** 频道列表写为 data.xml */
	private static boolean writeDataXml(Context ctx, ArrayList<Channel> all) {
		String xml = toDataXml(all);
		try {
			File f = new File(ctx.getFilesDir(), "data.xml");
			FileOutputStream fos = new FileOutputStream(f);
			fos.write(xml.getBytes("UTF-8"));
			fos.close();
			return true;
		} catch (Exception e) {
			Logger.e(TAG, "write data.xml failed: " + e);
			return false;
		}
	}

	/** M3U 解析：#EXTINF 行取尾部频道名，下一非注释行为 url */
	public static ArrayList<Channel> parseM3U(String text) {
		ArrayList<Channel> channels = new ArrayList<Channel>();
		String[] lines = text.split("\r\n|\r|\n");
		String pendingName = null;
		for (int i = 0; i < lines.length; i++) {
			String ln = lines[i].trim();
			if (ln.length() == 0) {
				continue;
			}
			if (ln.startsWith("#EXTINF")) {
				int comma = ln.lastIndexOf(',');
				pendingName = comma > -1 ? ln.substring(comma + 1).trim() : null;
			} else if (ln.charAt(0) != '#') {
				if (ln.startsWith("http://") || ln.startsWith("https://")) {
					Channel c = new Channel();
					c.name = pendingName != null ? pendingName : "频道" + (channels.size() + 1);
					c.url = ln;
					channels.add(c);
					pendingName = null;
				}
			}
		}
		return channels;
	}

	/** TVBox txt 解析：分组,#genre# 行 + 频道名,url 行（多源 # 分隔取第一个） */
	public static ArrayList<Channel> parseLiveTxt(String text) {
		ArrayList<Channel> channels = new ArrayList<Channel>();
		String[] lines = text.split("\r\n|\r|\n");
		String group = "";
		for (int i = 0; i < lines.length; i++) {
			String ln = lines[i].trim();
			if (ln.length() == 0) {
				continue;
			}
			if (ln.contains("#genre#")) {
				group = ln.replace(",#genre#", "").replace("#genre#", "").trim();
				continue;
			}
			int comma = ln.indexOf(',');
			if (comma > 0) {
				String name = ln.substring(0, comma).trim();
				String urlPart = ln.substring(comma + 1).trim();
				String url = urlPart.split("#")[0].trim();
				if (name.length() > 0 && (url.startsWith("http://") || url.startsWith("https://"))) {
					Channel c = new Channel();
					c.group = group;
					c.name = name;
					c.url = url;
					channels.add(c);
				}
			}
		}
		return channels;
	}

	private static String toDataXml(ArrayList<Channel> channels) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append("<list>\n");
		String curGroup = null;
		for (Channel c : channels) {
			String g = c.group == null || c.group.length() == 0 ? "默认分组" : c.group;
			if (!g.equals(curGroup)) {
				if (curGroup != null) {
					sb.append("  </class>\n");
				}
				sb.append("  <class classname=\"").append(esc(g)).append("\">\n");
				curGroup = g;
			}
			sb.append("    <channel name=\"").append(esc(c.name)).append("\" epg=\"\">\n");
			if (c.urls != null && !c.urls.isEmpty()) {
				for (int k = 0; k < c.urls.size(); k++) {
					sb.append("      <tvlink link=\"").append(esc(c.urls.get(k)))
							.append("\" source=\"线路").append(k + 1).append("\"/>\n");
				}
			} else {
				sb.append("      <tvlink link=\"").append(esc(c.url)).append("\" source=\"线路1\"/>\n");
			}
			sb.append("    </channel>\n");
		}
		if (curGroup != null) {
			sb.append("  </class>\n");
		}
		sb.append("</list>\n");
		return sb.toString();
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&apos;");
	}
}
