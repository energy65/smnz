package com.shenma.tvlauncher.netsource;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 影片聚合模型：把多个源里同一部影片合并成一条记录，每条记录带多个线路（每个源算一条线路）。
 * 用于列表、搜索结果的去重展示，以及详情页的线路选择。
 */
public class VodFilm {

    /** 一条线路 = 某个源上的同一部影片 */
    public static class Ref {
        public String siteKey = "";
        public String siteName = "";
        public String vodId = "";
        public JSONObject raw;
    }

    /** 聚合后的影片 */
    public String title = "";
    public String pic = "";
    public String remarks = "";
    public String year = "";
    public final ArrayList<Ref> refs = new ArrayList<Ref>();

    /** 线路数量，即有几个源提供了这部影片 */
    public int lineCount() {
        return refs.size();
    }

    /** 展示副标题：优先年份，其次原始备注 */
    public String subtitle() {
        if (year != null && year.length() > 0) {
            return year;
        }
        return remarks == null ? "" : remarks;
    }

    /**
     * 把某个源返回的一条影片记录并入聚合表。
     * 用归一化标题做 key，同名影片自动合并为一条并追加线路。
     *
     * @return 合并后的影片记录（新建或已存在）
     */
    public static VodFilm put(Map<String, VodFilm> map, JSONObject v, String siteKey, String siteName) {
        if (v == null) {
            return null;
        }
        String title = v.optString("vod_name");
        if (title == null || title.trim().length() == 0) {
            return null;
        }
        String key = normalize(title);
        if (key.length() == 0) {
            return null;
        }
        VodFilm f = map.get(key);
        if (f == null) {
            f = new VodFilm();
            f.title = title.trim();
            f.pic = v.optString("vod_pic");
            f.remarks = v.optString("vod_remarks");
            if (f.remarks == null || f.remarks.length() == 0) {
                f.remarks = v.optString("vod_state");
            }
            f.year = v.optString("vod_year");
            map.put(key, f);
        } else {
            // 已有记录时补齐缺失的元信息，不覆盖先到的值
            if ((f.pic == null || f.pic.length() == 0)) {
                f.pic = v.optString("vod_pic");
            }
            if ((f.remarks == null || f.remarks.length() == 0)) {
                String r = v.optString("vod_remarks");
                f.remarks = (r == null || r.length() == 0) ? v.optString("vod_state") : r;
            }
            if ((f.year == null || f.year.length() == 0)) {
                f.year = v.optString("vod_year");
            }
        }
        // 同一源上的同一部影片只记一条线路，避免翻页/重复返回导致线路数虚高
        String vodId = v.optString("vod_id");
        for (int i = 0; i < f.refs.size(); i++) {
            Ref old = f.refs.get(i);
            if (old.siteKey.equals(siteKey) && old.vodId.equals(vodId)) {
                return f;// 已收录，忽略重复
            }
        }
        Ref ref = new Ref();
        ref.siteKey = siteKey;
        ref.siteName = siteName;
        ref.vodId = vodId;
        ref.raw = v;
        f.refs.add(ref);
        return f;
    }

    public static Map<String, VodFilm> newMap() {
        return new HashMap<String, VodFilm>();
    }

    /* ==================== 标题归一化 ==================== */

    /** 需要在合并时被忽略的画质/版本修饰词 */
    private static final String[] NOISE = {
            "高清", "超清", "蓝光", "原盘", "Remux", "WEB-DL", "WEBRip", "WEBrip",
            "BD1080", "BD720", "1080P", "1080p", "720P", "720p", "4K", "8K",
            "完整版", "完整", "未删减", "未删节", "国语", "粤语", "国粤", "中字", "中英",
            "在线观看", "在线播放", "在线", "观看", "播放", "免费观看", "免费",
            "高清在线", "抢先版", "抢先看",
            "HD", "BD", "HDTS", "DVDRip", "HDRip", "BDMV", "REMUX", "HLG", "ATMOS"
    };

    /**
     * 归一化片名，作为合并同一部影片的 key。
     * 全角转半角、去空白、去括号内的画质版本词、英文转小写。
     * 保留年份与"第N季"等区分信息，避免把第一季和第二季合并。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.length() == 0) {
            return "";
        }
        // 全角字符转半角
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\uFF01' && c <= '\uFF5E') {
                c = (char) (c - '\uFF01' + '!');
            } else if (c == '\u3000') {
                c = ' ';
            }
            sb.append(c);
        }
        s = sb.toString();
        // 反复剥离括号块，只要括号内容是纯修饰词就丢弃
        boolean changed = true;
        while (changed) {
            changed = false;
            int open = s.indexOf('(');
            if (open < 0) {
                open = s.indexOf('（');
            }
            if (open < 0) {
                break;
            }
            char closeCh = s.charAt(open) == '(' ? ')' : '）';
            int close = s.indexOf(closeCh, open + 1);
            if (close < 0) {
                break;
            }
            String inner = s.substring(open + 1, close);
            if (isNoiseOnly(inner)) {
                s = s.substring(0, open) + " " + s.substring(close + 1);
                changed = true;
            } else {
                break;
            }
        }
        // 去掉残留修饰词与所有空白
        String upper = s.toUpperCase();
        for (String n : NOISE) {
            int at = upper.indexOf(n.toUpperCase());
            while (at >= 0) {
                s = s.substring(0, at) + " " + s.substring(at + n.length());
                upper = s.toUpperCase();
                at = upper.indexOf(n.toUpperCase());
            }
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString().toLowerCase();
    }

    /**
     * 括号内容是否只由修饰词/数字/分隔符构成（即无语义信息）。
     * 做法是反复剥离 NOISE 修饰词和数字，剥完后若还剩字母数字，说明括号里是语义内容，必须保留。
     */
    private static boolean isNoiseOnly(String inner) {
        if (inner == null) {
            return true;
        }
        String t = inner.trim().toUpperCase();
        if (t.length() == 0) {
            return true;
        }
        boolean removed = true;
        while (removed) {
            removed = false;
            // 去掉数字和括号等符号，只留字母与空格
            StringBuilder sb = new StringBuilder(t.length());
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (Character.isLetterOrDigit(c) || c == ' ') {
                    sb.append(c);
                }
            }
            t = sb.toString();
            for (int i = 0; i < NOISE.length; i++) {
                String n = NOISE[i].toUpperCase();
                int at = t.indexOf(n);
                if (at >= 0) {
                    t = t.substring(0, at) + " " + t.substring(at + n.length());
                    removed = true;
                }
            }
            t = t.trim();
            if (t.length() == 0) {
                return true;
            }
        }
        // 剥完后还剩数字，直接丢弃（年份/分辨率对合并无意义）；只剩字母才是语义内容
        StringBuilder left = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetter(c)) {
                left.append(c);
            }
        }
        return left.length() == 0;
    }
}
