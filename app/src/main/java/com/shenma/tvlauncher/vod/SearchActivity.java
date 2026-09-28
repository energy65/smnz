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
import com.shenma.tvlauncher.utils.Logger;
import com.shenma.tvlauncher.utils.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;

/**
 * 搜索页：深色主题，海报网格 + 底部筛选面板（类型/年份/地区/排序）
 * 搜索范围覆盖 forever.json 中所有 type=1 的 CMS 站点
 */
public class SearchActivity extends Activity {

    // 筛选数据：{API值, 显示文本}
    private static final String[][] FILTER_CLASS = {
            {"", "全部"}, {"古装", "古装"}, {"喜剧", "喜剧"}, {"爱情", "爱情"},
            {"动作", "动作"}, {"科幻", "科幻"}, {"悬疑", "悬疑"}, {"战争", "战争"},
            {"青春", "青春"}, {"偶像", "偶像"}, {"都市", "都市"}, {"家庭", "家庭"}
    };
    private static final String[][] FILTER_YEAR = {
            {"", "全部"}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"},
            {"2023", "2023"}, {"2022", "2022"}, {"2021", "2021"}, {"2020", "2020"},
            {"2019", "2019"}
    };
    private static final String[][] FILTER_AREA = {
            {"", "全部"}, {"内地", "内地"}, {"香港", "香港"}, {"台湾", "台湾"},
            {"美国", "美国"}, {"韩国", "韩国"}, {"日本", "日本"}, {"英国", "英国"}
    };
    private static final String[][] FILTER_SORT = {
            {"time", "最近更新"}, {"hits", "热度优先"}, {"score", "评分最高"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mv_search_new);
        context = SearchActivity.this;
        initView();
    }

    @Override
    protected void onStop() {
        super.onStop();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ==================== Views ====================
    private EditText search_input;
    private TextView tv_page_info, tv_empty;
    private GridView gv_result;
    private LinearLayout filter_columns;
    private NetAdapter mAdapter;

    // ==================== State ====================
    private Context context;
    private int mPage = 1, mPageCount = 1;
    private boolean mLoading = false;
    private String mKeyword = "";
    private int mSearchSeq = 0;

    // 筛选选中索引
    private int mSelClass = 0, mSelYear = 0, mSelArea = 0, mSelSort = 0;

    // 各列容器引用（用于切换高亮）
    private LinearLayout mColClass, mColYear, mColArea, mColSort;

    private void initView() {
        search_input = (EditText) findViewById(R.id.search_keybord_input);
        tv_page_info = (TextView) findViewById(R.id.search_page_info);
        tv_empty = (TextView) findViewById(R.id.search_empty_text);
        gv_result = (GridView) findViewById(R.id.search_result);
        gv_result.setSelector(new ColorDrawable(Color.TRANSPARENT));
        filter_columns = (LinearLayout) findViewById(R.id.filter_columns);

        mAdapter = new NetAdapter();
        gv_result.setAdapter(mAdapter);
        gv_result.setOnItemClickListener(new OnItemClickListener() {
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

        // 搜索按钮
        findViewById(R.id.search_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mKeyword = search_input.getText().toString().trim();
                mPage = 1;
                doSearch();
            }
        });
        // IME 回车搜索
        search_input.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    mKeyword = search_input.getText().toString().trim();
                    mPage = 1;
                    doSearch();
                    return true;
                }
                return false;
            }
        });

        // 翻页
        findViewById(R.id.search_page_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPage > 1) {
                    mPage--;
                    doSearch();
                }
            }
        });
        findViewById(R.id.search_page_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPage < mPageCount) {
                    mPage++;
                    doSearch();
                }
            }
        });

        // 清空筛选
        findViewById(R.id.clear_filter_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mKeyword = "";
                search_input.setText("");
                mSelClass = mSelYear = mSelArea = 0;
                mSelSort = 0;
                updateColumnHighlight(mColClass, mSelClass);
                updateColumnHighlight(mColYear, mSelYear);
                updateColumnHighlight(mColArea, mSelArea);
                updateColumnHighlight(mColSort, mSelSort);
                mPage = 1;
                doSearch();
            }
        });

        // 构建筛选面板
        buildFilterPanel();

        // 首次加载
        doSearch();
    }

    // ==================== 筛选面板 ====================

    private void buildFilterPanel() {
        mColClass = buildColumn("类型", FILTER_CLASS, mSelClass);
        addDivider();
        mColYear = buildColumn("年份", FILTER_YEAR, mSelYear);
        addDivider();
        mColArea = buildColumn("地区", FILTER_AREA, mSelArea);
        addDivider();
        mColSort = buildColumn("排序", FILTER_SORT, mSelSort);
    }

    private LinearLayout buildColumn(String title, final String[][] data, final int selectedIdx) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) (getResources().getDisplayMetrics().density * 16);
        lp.rightMargin = (int) (getResources().getDisplayMetrics().density * 16);
        col.setLayoutParams(lp);

        // 列标题
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(Color.WHITE);
        header.setTextSize(14);
        header.setPadding(0, 0, 0, 6);
        col.addView(header);

        // 筛选项
        for (int i = 0; i < data.length; i++) {
            final int idx = i;
            TextView item = new TextView(this);
            item.setText(data[i][1]);
            item.setTextSize(13);
            item.setTextColor(i == selectedIdx ? 0xFF4FC3F7 : 0xFFB0B0B0);
            item.setPadding(0, 4, 0, 4);
            item.setFocusable(true);
            item.setFocusableInTouchMode(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (data == FILTER_CLASS) {
                        mSelClass = idx;
                        updateColumnHighlight(mColClass, idx);
                    } else if (data == FILTER_YEAR) {
                        mSelYear = idx;
                        updateColumnHighlight(mColYear, idx);
                    } else if (data == FILTER_AREA) {
                        mSelArea = idx;
                        updateColumnHighlight(mColArea, idx);
                    } else if (data == FILTER_SORT) {
                        mSelSort = idx;
                        updateColumnHighlight(mColSort, idx);
                    }
                    mPage = 1;
                    doSearch();
                }
            });
            col.addView(item);
        }

        filter_columns.addView(col);
        return col;
    }

    private void addDivider() {
        View div = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.leftMargin = (int) (getResources().getDisplayMetrics().density * 16);
        lp.rightMargin = (int) (getResources().getDisplayMetrics().density * 16);
        div.setLayoutParams(lp);
        div.setBackgroundColor(0xFF2A3A4A);
        filter_columns.addView(div);
    }

    private void updateColumnHighlight(LinearLayout col, int selectedIdx) {
        if (col == null) return;
        for (int i = 1; i < col.getChildCount(); i++) {
            View child = col.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(i - 1 == selectedIdx ? 0xFF4FC3F7 : 0xFFB0B0B0);
            }
        }
    }

    // ==================== 搜索 ====================

    private String buildFilterParams() {
        StringBuilder sb = new StringBuilder();
        String cls = FILTER_CLASS[mSelClass][0];
        if (cls.length() > 0) sb.append("&class=").append(cls);
        String yr = FILTER_YEAR[mSelYear][0];
        if (yr.length() > 0) sb.append("&year=").append(yr);
        String area = FILTER_AREA[mSelArea][0];
        if (area.length() > 0) sb.append("&area=").append(area);
        String by = FILTER_SORT[mSelSort][0];
        if (by.length() > 0) sb.append("&by=").append(by);
        return sb.toString();
    }

    private void doSearch() {
        if (mLoading) return;
        showProgressDialog();
        mLoading = true;
        final int seq = ++mSearchSeq;
        final String kw = mKeyword;
        final int page = mPage;
        final String filterParams = buildFilterParams();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<NetItem> out = new ArrayList<NetItem>();
                int totalPage = 1;
                try {
                    ArrayList<TvBoxConfig.Site> sites = TvBoxConfig.getSites(context);
                    if (sites != null) {
                        for (int s = 0; s < sites.size(); s++) {
                            TvBoxConfig.Site site = sites.get(s);
                            try {
                                StringBuilder url = new StringBuilder(site.api);
                                url.append("?ac=videolist&pg=").append(page);
                                if (kw != null && kw.length() > 0) {
                                    url.append("&wd=").append(URLEncoder.encode(kw, "UTF-8"));
                                }
                                url.append(filterParams);

                                JSONObject j = new JSONObject(TvBoxConfig.fetchText(url.toString(), 10000));
                                totalPage = Math.max(totalPage, j.optInt("pagecount", 1));
                                JSONArray list = j.optJSONArray("list");
                                if (list != null) {
                                    for (int i = 0; i < list.length(); i++) {
                                        JSONObject v = list.optJSONObject(i);
                                        if (v != null) {
                                            out.add(NetItem.from(v, site.name));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Logger.w("joychang", "search site[" + site.name + "] failed: " + e);
                            }
                        }
                    }
                } catch (Exception e) {
                }
                final int fTotalPage = totalPage;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != mSearchSeq) return;
                        mLoading = false;
                        closeProgressDialog();
                        mPageCount = fTotalPage;
                        tv_page_info.setText(mPage + "/" + mPageCount);

                        if (out.isEmpty()) {
                            if (page == 1) {
                                mAdapter.clear();
                                tv_empty.setVisibility(View.VISIBLE);
                                tv_empty.setText("没有搜索到相关内容");
                            }
                            return;
                        }
                        if (page == 1) {
                            mAdapter.setData(out);
                        } else {
                            mAdapter.addData(out);
                        }
                        tv_empty.setVisibility(View.GONE);
                    }
                });
            }
        }, "net-search").start();
    }

    // ==================== Loading ====================

    protected void showProgressDialog() {
        Utils.loadingShow_tv(SearchActivity.this, R.string.str_data_loading);
    }

    protected void closeProgressDialog() {
        Utils.loadingClose_Tv();
    }

    // ==================== Data Model ====================

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
            holder.state.setText(it.state);
            return convertView;
        }

        class ViewHolder {
            ImageView poster;
            TextView state, name;
        }
    }

    private ImageLoader imageLoader = ImageLoader.getInstance();
}
