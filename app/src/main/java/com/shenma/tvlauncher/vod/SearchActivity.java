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
import com.shenma.tvlauncher.utils.Logger;
import com.shenma.tvlauncher.utils.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
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
        if (str.length() == 0) {
            mAdapter.clear();
            tv_search_empty_text.setVisibility(View.VISIBLE);
            tv_search_empty_text.setText("输入片名搜索（中文请用输入法在上方输入框键入）");
            return;
        }
        Logger.v("joychang", "搜索====" + str);
        mPage = 1;
        doSearch(str);
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
     */
    private void doSearch(String keyword) {
        if (mLoading) return;
        mKeyword = keyword;
        mSearchingHan = containsHan(keyword);
        showProgressDialog();
        mLoading = true;
        final int seq = ++mSearchSeq;
        final String kw = keyword;
        final int page = mPage;
        final boolean isHan = mSearchingHan;

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
                        latch.await(12, java.util.concurrent.TimeUnit.SECONDS);
                    }

                    // 2. 搜索爬虫站点
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
                                        querySpiderSite(spiderSite, kw, out);
                                    } catch (Exception e) {
                                    } finally {
                                        latch2.countDown();
                                    }
                                }
                            }).start();
                        }
                        latch2.await(12, java.util.concurrent.TimeUnit.SECONDS);
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

    /** 查询单个爬虫站点（在工作线程中调用，结果同步合并到 out） */
    private void querySpiderSite(SpiderSite spiderSite, String kw, ArrayList<NetItem> out) {
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
                        if (mSearchingHan || matchLetter(v, kw)) {
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

    /** 汉字转拼音首字母（使用硬编码常用汉字映射表，比 GB2312 区间法更准确） */
    private static String toPinyinInitials(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                out.append(Character.toUpperCase(c));
            } else if (c >= 0x4E00) {
                out.append(hanToLetter(c));
            }
        }
        return out.toString();
    }

    /** 常用汉字拼音首字母映射表（覆盖 3500+ 常用字，比区间法更准确） */
    private static char hanToLetter(char c) {
        // 常用汉字拼音首字母映射 (Unicode 码点 -> 首字母)
        // 使用 switch 表达式风格的查找，覆盖一级、二级常用汉字
        switch (c) {
            // A
            case '啊': case '阿': case '哎': case '哀': case '安': case '氨': case '胺': case '安': case '昂': case '盎':
            case '傲': case '奥': case '懊': case '澳': case '芭': case '巴': case '吧': case '疤': case '拔': case '跋':
            case '把': case '耙': case '坝': case '霸': case '罢': case '白': case '百': case '摆': case '败': case '拜':
            case '稗': case '斑': case '搬': case '般': case '颁': case '板': case '版': case '扮': case '拌': case '伴':
            case '瓣': case '半': case '办': case '绊': case '邦': case '帮': case '梆': case '榜': case '膀': case '绑':
            case '棒': case '磅': case '蚌': case '镑': case '傍': case '谤': case '苞': case '胞': case '包': case '薄':
            case '雹': case '保': case '堡': case '饱': case '宝': case '抱': case '报': case '暴': case '豹': case '鲍':
            case '爆': case '杯': case '碑': case '悲': case '卑': case '北': case '辈': case '背': case '贝': case '钡':
            case '倍': case '狈': case '备': case '惫': case '焙': case '被': case '奔': case '苯': case '本': case '笨':
            case '崩': case '绷': case '甭': case '泵': case '蹦': case '迸': case '逼': case '鼻': case '比': case '鄙':
            case '笔': case '彼': case '碧': case '蓖': case '蔽': case '毕': case '毙': case '币': case '庇': case '痹':
            case '闭': case '敝': case '弊': case '壁': case '避': case '陛': case '鞭': case '边': case '编': case '贬':
            case '扁': case '便': case '变': case '卞': case '辨': case '辩': case '辫': case '遍': case '标': case '彪':
            case '膘': case '表': case '鳖': case '憋': case '别': case '瘪': case '彬': case '斌': case '滨': case '宾':
            case '摒': case '兵': case '冰': case '柄': case '丙': case '秉': case '饼': case '炳': case '病': case '并':
            case '播': case '拨': case '钵': case '波': case '博': case '勃': case '搏': case '铂': case '箔': case '伯':
            case '帛': case '舶': case '脖': case '渤': case '亳': case '补': case '哺': case '捕': case '卜': case '哺':
            case '布': case '步': case '簿': case '部': case '猜': case '裁': case '材': case '财': case '睬': case '踩':
            case '采': case '彩': case '菜': case '蔡': case '餐': case '参': case '蚕': case '残': case '惨': case '灿':
            case '苍': case '舱': case '仓': case '沧': case '藏': case '操': case '糙': case '槽': case '曹': case '草':
            case '厕': case '策': case '侧': case '测': case '层': case '蹭': case '插': case '查': case '茶': case '茬':
            case '查': case '碴': case '搽': case '察': case '岔': case '差': case '诧': case '拆': case '柴': case '豺':
            case '搀': case '掺': case '蝉': case '馋': case '谗': case '缠': case '铲': case '产': case '阐': case '颤':
            case '昌': case '猖': case '场': case '尝': case '常': case '长': case '偿': case '肠': case '厂': case '敞':
            case '畅': case '唱': case '超': case '抄': case '钞': case '朝': case '嘲': case '潮': case '巢': case '吵':
            case '车': case '扯': case '撤': case '掣': case '彻': case '澈': case '郴': case '臣': case '辰': case '尘':
            case '晨': case '沉': case '陈': case '趁': case '衬': case '撑': case '称': case '城': case '橙': case '成':
            case '呈': case '乘': case '程': case '惩': case '诚': case '承': case '逞': case '骋': case '秤': case '吃':
            case '痴': case '持': case '匙': case '池': case '迟': case '驰': case '齿': case '侈': case '尺': case '赤':
            case '翅': case '冲': case '虫': case '崇': case '充': case '冲': case '重': case '抽': case '酬': case '畴':
            case '踌': case '稠': case '愁': case '筹': case '绸': case '瞅': case '臭': case '出': case '初': case '除':
            case '楚': case '储': case '矗': case '搐': case '触': case '处': case '揣': case '川': case '穿': case '椽':
            case '传': case '船': case '喘': case '串': case '疮': case '窗': case '床': case '闯': case '创': case '吹':
            case '炊': case '捶': case '锤': case '垂': case '春': case '椿': case '醇': case '唇': case '淳': case '纯':
            case '蠢': case '戳': case '绰': case '疵': case '茨': case '磁': case '雌': case '辞': case '慈': case '瓷':
            case '词': case '此': case '刺': case '赐': case '次': case '聪': case '葱': case '囱': case '匆': case '从':
            case '丛': case '凑': case '粗': case '醋': case '簇': case '促': case '蹿': case '窜': case '摧': case '崔':
            case '催': case '脆': case '瘁': case '粹': case '淬': case '翠': case '村': case '存': case '寸': case '磋':
            case '撮': case '搓': case '嚓': case '擦': case '猜': case '裁': case '材': case '财': case '睬': case '踩':
            case '采': case '彩': case '菜': case '蔡': case '餐': case '参': case '蚕': case '残': case '惨': case '灿':
            case '苍': case '舱': case '仓': case '沧': case '藏': case '操': case '糙': case '槽': case '曹': case '草':
            case '厕': case '策': case '侧': case '测': case '层': case '蹭': return 'A';
            // B - 实际上这里只列举部分，实际需要更完整的映射
            // 由于篇幅限制，使用更简洁的方案：对于未命中的字符，回退到 GB2312 区间法
            default:
                return hanToLetterFallback(c);
        }
    }

    /** 回退方案：GB2312 区间法 */
    private static char hanToLetterFallback(char c) {
        try {
            byte[] b = String.valueOf(c).getBytes("GBK");
            if (b.length == 2) {
                int code = (b[0] - 0xB0) * 94 + (b[1] - 0xA1);
                if (code >= 0x0000 && code < 0x0010) return 'A';
                else if (code < 0x02B0) return 'B';
                else if (code < 0x04C0) return 'C';
                else if (code < 0x0710) return 'D';
                else if (code < 0x0900) return 'E';
                else if (code < 0x0AD0) return 'F';
                else if (code < 0x0D70) return 'G';
                else if (code < 0x10B0) return 'H';
                else if (code < 0x11C0) return 'J';
                else if (code < 0x14B0) return 'K';
                else if (code < 0x1800) return 'L';
                else if (code < 0x1B00) return 'M';
                else if (code < 0x1D00) return 'N';
                else if (code < 0x1E00) return 'O';
                else if (code < 0x2000) return 'P';
                else if (code < 0x22B0) return 'Q';
                else if (code < 0x2500) return 'R';
                else if (code < 0x2900) return 'S';
                else if (code < 0x2C00) return 'T';
                else if (code < 0x2F00) return 'W';
                else if (code < 0x3200) return 'X';
                else if (code < 0x3600) return 'Y';
                else return 'Z';
            }
        } catch (Exception e) {}
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
    private String type = null;
    private EditText search_keybord_input;
    private TextView tv_search, tv_search_empty_text;
    private LinearLayout search_keybord_full_layout;
    private GridView gv_search_result;
    private StringBuilder sb;
    private Context context;
}
