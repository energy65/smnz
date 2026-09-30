package com.shenma.tvlauncher.vod;

import java.net.URLEncoder;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.shenma.tvlauncher.R;
import com.shenma.tvlauncher.netsource.TvBoxConfig;
import com.shenma.tvlauncher.spider.SpiderApi;
import com.shenma.tvlauncher.spider.SpiderEngine;
import com.shenma.tvlauncher.spider.SpiderSite;
import com.github.catvod.crawler.Spider;
import com.shenma.tvlauncher.utils.Logger;
import com.shenma.tvlauncher.utils.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;

public class SearchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mv_search_new);
        context = SearchActivity.this;
        initIntent();
        initView();
    }

    @Override
    protected void onStop() {
        super.onStop();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void initView() {
        findViewById();
        loadViewLayout();
        setListener();
    }

    private void findViewById() {
        sb = new StringBuilder();
        search_keybord_input = (EditText) findViewById(R.id.search_keybord_input);
        tv_search = (TextView) findViewById(R.id.search_keybord_hint);
        tv_search_empty_text = (TextView) findViewById(R.id.search_empty_text);
        search_keybord_full_layout = (LinearLayout) findViewById(R.id.search_keybord_full_layout);
        gv_search_result = (GridView) findViewById(R.id.search_result);
        gv_search_result.setSelector(new ColorDrawable(Color.TRANSPARENT));
        mAdapter = new NetAdapter();
        gv_search_result.setAdapter(mAdapter);
        tv_search_empty_text.setText("输入片名搜索（中文请用输入法在上方输入框键入）");
        tv_search.setText("按拼音首字母速查（如《太极》TJ），或在上方输入框直接输入中文片名");
        // 允许 IME 直接输入中文
        search_keybord_input.setFocusable(true);
        search_keybord_input.setFocusableInTouchMode(true);
        search_keybord_input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (mSyncingInput) return;
                sb = new StringBuilder(s == null ? "" : s.toString());
                readyToSearch();
            }
        });
    }

    private void loadViewLayout() {}

    private void setListener() {
        bindKeybordButtons(search_keybord_full_layout);
        gv_search_result.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                NetItem it = mAdapter.getItem(position);
                Intent intent = new Intent(SearchActivity.this,
                        com.shenma.tvlauncher.netsource.NetVodActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra("openVodId", it.id);
                intent.putExtra("presetSite", it.siteName);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
        gv_search_result.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                if (mSearchingHan) return;
                int i = totalItemCount - visibleItemCount;
                if (firstVisibleItem < i) return;
                pageDown();
            }
        });
    }

    private void initIntent() {
        Intent intent = getIntent();
        type = intent.getStringExtra("TYPE");
    }

    /** 递归为键盘上所有可点击子 View 绑定点击 */
    private void bindKeybordButtons(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                bindKeybordButtons(vg.getChildAt(i));
            }
            return;
        }
        if (view.isClickable()) {
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { doClick(v); }
            });
        }
    }

    public void doClick(View target) {
        int tag = target.getId();
        if (tag == R.id.search_keybord_full_clear) {
            sb = new StringBuilder();
            readyToSearch();
            return;
        }
        if (tag == R.id.search_keybord_full_del) {
            if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
            readyToSearch();
            return;
        }
        if (tag == R.id.search_keybord_sj) {
            type = "TVPLAY";
            readyToSearch();
            return;
        }
        if (tag == R.id.search_keybord_sp) {
            type = "MOVIE";
            readyToSearch();
            return;
        }
        Object obj = target.getTag();
        sb.append(obj);
        readyToSearch();
    }

    private void readyToSearch() {
        String str = sb.toString().trim();
        mSyncingInput = true;
        search_keybord_input.setText(str);
        search_keybord_input.setSelection(str.length());
        mSyncingInput = false;
        // 输入过程中的防抖：连续按键只发起最后一次搜索
        if (mPendingSearch != null) {
            mHandler.removeCallbacks(mPendingSearch);
            mPendingSearch = null;
        }
        if (str.length() == 0) {
            // 空输入：作废在途请求并清空结果
            mSearchSeq++;
            mPage = 1;
            mAdapter.clear();
            tv_search_empty_text.setVisibility(View.VISIBLE);
            tv_search_empty_text.setText("输入片名搜索（中文请用输入法在上方输入框键入）");
            return;
        }
        Logger.v("joychang", "搜索====" + str);
        mPage = 1;
        mPendingSearch = new Runnable() {
            @Override
            public void run() {
                mPendingSearch = null;
                doSearch(str);
            }
        };
        mHandler.postDelayed(mPendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void pageDown() {
        if (mLoading || mPage >= mPageCount) return;
        mPage++;
        doSearch(mKeyword);
    }

    /**
     * 搜索逻辑（并行查询所有 CMS 站点和爬虫站点，总超时 12 秒）：
     * - 中文关键词：逐站点 wd= 搜索(CMS) / searchContent(爬虫)
     * - 字母/数字：CMS 用 &letter= 首字母服务端过滤 + 本地 matchLetter 精筛；
     *   爬虫站点直接 searchContent 后本地 matchLetter 精筛
     * - 爬虫站点不参与翻页，仅首页搜索，避免重复结果
     */
    private void doSearch(String keyword) {
        if (keyword == null || keyword.trim().length() == 0) return;
        mKeyword = keyword;
        mSearchingHan = containsHan(keyword);
        showProgressDialog();
        mLoading = true;
        // 新查询作废所有在途旧查询（mSearchSeq 比对会让旧线程自行丢弃结果）
        final int seq = ++mSearchSeq;
        final String kw = keyword;
        final int page = mPage;
        final boolean isHan = mSearchingHan;
        final long deadline = System.currentTimeMillis() + 12000;

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<NetItem> out = new ArrayList<NetItem>();
                final int[] totalPage = {1};
                try {
                    // 1. 搜索 CMS 站点
                    ArrayList<TvBoxConfig.Site> sites = TvBoxConfig.getSites(context);
                    if (sites != null && !sites.isEmpty()) {
                        int n = sites.size();
                        java.util.concurrent.CountDownLatch latch =
                                new java.util.concurrent.CountDownLatch(n);
                        for (int s = 0; s < n; s++) {
                            final TvBoxConfig.Site site = sites.get(s);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        queryOneSite(site, kw, page, isHan, out, totalPage);
                                    } catch (Exception e) {
                                    } finally {
                                        latch.countDown();
                                    }
                                }
                            }).start();
                        }
                        latch.await(remainMs(deadline), java.util.concurrent.TimeUnit.MILLISECONDS);
                    }

                    // 2. 搜索爬虫站点（仅首页，避免翻页时重复返回相同结果）
                    if (page == 1) {
                        ArrayList<SpiderSite> spiderSites = TvBoxConfig.getSpiders(context);
                        if (spiderSites != null && !spiderSites.isEmpty()) {
                            int n = spiderSites.size();
                            java.util.concurrent.CountDownLatch latch2 =
                                    new java.util.concurrent.CountDownLatch(n);
                            for (int s = 0; s < n; s++) {
                                final SpiderSite spiderSite = spiderSites.get(s);
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            querySpiderSite(spiderSite, kw, isHan, out);
                                        } catch (Exception e) {
                                        } finally {
                                            latch2.countDown();
                                        }
                                    }
                                }).start();
                            }
                            latch2.await(remainMs(deadline), java.util.concurrent.TimeUnit.MILLISECONDS);
                        }
                    }
                } catch (Exception e) {}
                final int fTotalPage = totalPage[0];
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != mSearchSeq) return;
                        mLoading = false;
                        closeProgressDialog();
                        mPageCount = fTotalPage;
                        if (out.isEmpty()) {
                            if (page == 1) {
                                mAdapter.clear();
                                tv_search_empty_text.setVisibility(View.VISIBLE);
                                Utils.showToast(context, "亲，没有搜索到相关内容！", R.drawable.toast_err);
                            }
                            return;
                        }
                        if (page == 1) mAdapter.setData(out);
                        else mAdapter.addData(out);
                        tv_search_empty_text.setVisibility(View.GONE);
                    }
                });
            }
        }, "net-search").start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPendingSearch != null) {
            mHandler.removeCallbacks(mPendingSearch);
            mPendingSearch = null;
        }
        mSearchSeq++;
    }

    /** 剩余超时毫秒数，供两阶段搜索共用同一时间预算 */
    private static long remainMs(long deadline) {        long left = deadline - System.currentTimeMillis();
        return left > 0 ? left : 1;
    }

    /** 查询单个爬虫站点（在工作线程中调用，结果同步合并到 out） */
    private void querySpiderSite(SpiderSite spiderSite, String kw, boolean isHan,
            ArrayList<NetItem> out) {
        ArrayList<NetItem> siteResults = new ArrayList<NetItem>();
        try {
            Spider spider = SpiderEngine.get().getSpider(spiderSite);
            if (spider == null) return;
            JSONObject ret = SpiderApi.search(spider, kw, false);
            JSONArray list = ret.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject v = list.optJSONObject(i);
                    if (v != null) {
                        if (isHan || matchLetter(v, kw)) {
                            siteResults.add(NetItem.from(v, spiderSite.name));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.w("joychang", "spider site[" + spiderSite.name + "] failed: " + e);
        }
        synchronized (out) {
            out.addAll(siteResults);
        }
    }

    /** 查询单个站点（在工作线程中调用，结果同步合并到 out） */
    private void queryOneSite(TvBoxConfig.Site site, String kw, int page,
            boolean isHan, ArrayList<NetItem> out, int[] totalPage) {
        ArrayList<NetItem> siteResults = new ArrayList<NetItem>();
        int sitePages = 1;
        try {
            if (isHan) {
                String url = site.api + "?ac=videolist&wd=" + URLEncoder.encode(kw, "UTF-8");
                JSONObject j = new JSONObject(TvBoxConfig.fetchText(url, 6000));
                JSONArray list = j.optJSONArray("list");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject v = list.optJSONObject(i);
                        if (v != null) siteResults.add(NetItem.from(v, site.name));
                    }
                }
            } else {
                try {
                    String url = site.api + "?ac=videolist&pg=" + page
                            + "&letter=" + URLEncoder.encode(kw.substring(0, 1), "UTF-8");
                    JSONObject j = new JSONObject(TvBoxConfig.fetchText(url, 6000));
                    sitePages = j.optInt("pagecount", 1);
                    JSONArray list = j.optJSONArray("list");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject v = list.optJSONObject(i);
                            if (v == null) continue;
                            if (kw.length() <= 1 || matchLetter(v, kw)) {
                                siteResults.add(NetItem.from(v, site.name));
                            }
                        }
                    }
                } catch (Exception e) {
                    // letter 参数失败时回退：无 letter 取最新列表本地过滤
                    String url = site.api + "?ac=videolist&pg=" + page;
                    JSONObject j = new JSONObject(TvBoxConfig.fetchText(url, 6000));
                    sitePages = j.optInt("pagecount", 1);
                    JSONArray list = j.optJSONArray("list");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject v = list.optJSONObject(i);
                            if (v == null) continue;
                            if (matchLetter(v, kw)) {
                                siteResults.add(NetItem.from(v, site.name));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.w("joychang", "site[" + site.name + "] failed: " + e);
        }
        synchronized (out) {
            out.addAll(siteResults);
            if (sitePages > totalPage[0]) totalPage[0] = sitePages;
        }
    }

    /** 是否含中文 */
    private static boolean containsHan(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x4E00) return true;
        }
        return false;
    }

    /** vod_letter 字段或标题拼音首字母前缀匹配 */
    private static boolean matchLetter(JSONObject v, String kw) {
        String letter = v.optString("vod_letter").toUpperCase();
        String k = kw.toUpperCase();
        if (letter.length() > 0 && letter.startsWith(k)) return true;
        String name = v.optString("vod_name");
        return name.length() > 0 && toPinyinInitials(name).startsWith(k);
    }

    /** 一级汉字各首字母的起始区位码（GB2312 16~55 区按拼音顺序排列） */
    private static final int[] PY_AREA = {
            1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787, 3106, 3212,
            3472, 3635, 3722, 3730, 3858, 4027, 4086, 4390, 4558, 4684, 4925, 5249};

    private static final char[] PY_LETTER = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'O',
            'P', 'Q', 'R', 'S', 'T', 'W', 'X', 'Y', 'Z'};

    private static final int PY_AREA_MAX = 5594;

    /** 汉字转拼音首字母，无法识别的生僻字跳过而非写入占位符 */
    private static String toPinyinInitials(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                out.append(Character.toUpperCase(c));
            } else if (c >= 0x4E00) {
                char l = hanToLetter(c);
                if (l != '#') out.append(l);
            }
        }
        return out.toString();
    }

    /** 单个汉字转首字母，非 GB2312 一级汉字返回 '#' */
    private static char hanToLetter(char c) {
        byte[] b;
        try {
            b = String.valueOf(c).getBytes("GBK");
        } catch (Exception e) {
            return '#';
        }
        if (b.length != 2) return '#';
        int area = (b[0] & 0xFF) - 160;
        int pos = (b[1] & 0xFF) - 160;
        if (area < 16 || area > 55) return '#';
        int code = area * 100 + pos;
        if (code < PY_AREA[0] || code > PY_AREA_MAX) return '#';
        for (int i = PY_LETTER.length - 1; i >= 0; i--) {
            if (code >= PY_AREA[i]) return PY_LETTER[i];
        }
        return '#';
    }

    protected void showProgressDialog() {
        Utils.loadingShow_tv(SearchActivity.this, R.string.str_data_loading);
    }

    protected void closeProgressDialog() {
        Utils.loadingClose_Tv();
    }

    // ==================== Data ====================

    static class NetItem {
        String id, title, pic, state, siteName;

        static NetItem from(JSONObject v, String siteName) {
            NetItem it = new NetItem();
            it.id = v.optString("vod_id");
            it.title = v.optString("vod_name");
            it.pic = v.optString("vod_pic");
            it.siteName = siteName;
            String remarks = v.optString("vod_remarks");
            if (remarks == null || remarks.length() == 0) {
                remarks = v.optString("vod_state");
            }
            it.state = remarks;
            return it;
        }
    }

    // ==================== Adapter ====================

    private class NetAdapter extends BaseAdapter {
        private final ArrayList<NetItem> data = new ArrayList<NetItem>();
        private final LayoutInflater inflater;
        private final DisplayImageOptions options;

        NetAdapter() {
            inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            options = new DisplayImageOptions.Builder()
                    .showStubImage(R.drawable.default_film_img)
                    .showImageForEmptyUri(R.drawable.default_film_img)
                    .showImageOnFail(R.drawable.default_film_img)
                    .resetViewBeforeLoading(true)
                    .cacheInMemory(true)
                    .cacheOnDisc(true)
                    .imageScaleType(ImageScaleType.EXACTLY)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .displayer(new FadeInBitmapDisplayer(300))
                    .build();
        }

        void setData(ArrayList<NetItem> items) {
            data.clear();
            data.addAll(items);
            notifyDataSetChanged();
        }

        void addData(ArrayList<NetItem> items) {
            data.addAll(items);
            notifyDataSetChanged();
        }

        void clear() {
            data.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() { return data.size(); }

        @Override
        public NetItem getItem(int position) { return data.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.mv_type_details_item, null);
                holder = new ViewHolder();
                holder.poster = (ImageView) convertView.findViewById(R.id.video_poster);
                holder.state = (TextView) convertView.findViewById(R.id.video_state);
                holder.name = (TextView) convertView.findViewById(R.id.video_name);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            NetItem it = data.get(position);
            imageLoader.displayImage(it.pic, holder.poster, options);
            holder.name.setText(it.title);
            holder.state.setText(it.siteName + " " + it.state);
            return convertView;
        }

        class ViewHolder {
            ImageView poster;
            TextView state, name;
        }
    }

    // ==================== Fields ====================

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private NetAdapter mAdapter;
    private int mSearchSeq = 0;
    private int mPage = 1, mPageCount = 1;
    private boolean mLoading = false, mSearchingHan = false;
    private String mKeyword = "";
    private boolean mSyncingInput = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mPendingSearch;
    private static final long SEARCH_DEBOUNCE_MS = 350L;
    private String type = null;
    private EditText search_keybord_input;
    private TextView tv_search, tv_search_empty_text;
    private LinearLayout search_keybord_full_layout;
    private GridView gv_search_result;
    private StringBuilder sb;
    private Context context;
}
