package com.shenma.tvlauncher.netsource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.catvod.crawler.Spider;
import com.shenma.tvlauncher.R;
import com.shenma.tvlauncher.spider.SpiderApi;
import com.shenma.tvlauncher.spider.SpiderEngine;
import com.shenma.tvlauncher.spider.SpiderSite;
import com.shenma.tvlauncher.utils.Logger;
import com.shenma.tvlauncher.vod.WebVideoPlayerActivity;
import com.shenma.tvlauncher.vod.domain.VideoInfo;
import com.shenma.tvlauncher.vod.domain.VideoList;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * TVBox 爬虫站点（type=3）浏览页，支持 jar / js / py 三类爬虫
 * 一次只运行一个爬虫站点，避免多源并发带来的内存与网络压力
 * 详情页采用"一剧多源"样式：海报 + 信息 + 源选择 + 剧集网格
 */
public class SpiderVodActivity extends Activity {

	private static final String TAG = "SpiderVod";

	private LinearLayout mSiteRow;
	private LinearLayout mCatRow;
	private GridView mGrid;
	private TextView mPageInfo;
	private TextView mLoading;
	private TextView mTitle;
	private EditText mSearchInput;
	private View mDetailPanel;
	private View mFilterPanel;
	private TextView mDetailName;
	private TextView mDetailDirector;
	private TextView mDetailActors;
	private TextView mDetailArea;
	private TextView mDetailYear;
	private TextView mDetailType;
	private TextView mDetailRemarks;
	private TextView mDetailIntro;
	private ImageView mDetailPic;
	private RadioGroup mDetailSources;
	private Button mDetailPlay;
	private GridView mDetailEpisodes;

	private ArrayList<SpiderSite> mSites = new ArrayList<SpiderSite>();
	private ArrayList<JSONObject> mClasses = new ArrayList<JSONObject>();
	private ArrayList<SpiderApi.Line> mLines = new ArrayList<SpiderApi.Line>();

	private SpiderSite mSite;
	private Spider mSpider;
	private JSONObject mFilters = new JSONObject();
	private HashMap<String, String> mExtend = new HashMap<String, String>();

	private int mSiteIdx = 0;
	private int mCatIdx = -1;
	private int mLineIdx = 0;
	private int mPage = 1;
	private int mPageCount = 1;
	private String mKeyword = "";
	private String mTypeName = "";
	private JSONArray mList = new JSONArray();
	private GridAdapter mAdapter;
	private EpisodeAdapter mEpisodeAdapter;
	private LruCache<String, Bitmap> mPicCache = new LruCache<String, Bitmap>(64);
	private Handler mUi = new Handler();
	private ExecutorService mPool = Executors.newSingleThreadExecutor();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.net_vod_main);
		findViews();
		mAdapter = new GridAdapter();
		mGrid.setAdapter(mAdapter);
		mGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				openDetail(position);
			}
		});
		mEpisodeAdapter = new EpisodeAdapter();
		mDetailEpisodes.setAdapter(mEpisodeAdapter);
		mDetailEpisodes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				playAt(position);
			}
		});
		setBtn(R.id.net_search_btn, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mKeyword = mSearchInput.getText().toString().trim();
				mPage = 1;
				loadList();
			}
		});
		setBtn(R.id.net_page_prev, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mPage > 1) {
					mPage--;
					loadList();
				}
			}
		});
		setBtn(R.id.net_page_next, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mPage < mPageCount) {
					mPage++;
					loadList();
				}
			}
		});
		setBtn(R.id.net_detail_close, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mDetailPanel.setVisibility(View.GONE);
			}
		});
		setBtn(R.id.net_detail_play, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				playAt(0);
			}
		});
		setBtn(R.id.net_menu_btn, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mFilterPanel.setVisibility(mFilterPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
			}
		});
		setBtn(R.id.net_spider_btn, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(SpiderVodActivity.this, NetVodActivity.class));
			}
		});
		mFilterPanel.setVisibility(View.VISIBLE);
		loadSites();
	}

	/* ==================== 站点 ==================== */

	private void loadSites() {
		mLoading.setText("正在加载爬虫源...");
		mLoading.setVisibility(View.VISIBLE);
		run(new Runnable() {
			@Override
			public void run() {
				final ArrayList<SpiderSite> sites = TvBoxConfig.getSpiders(SpiderVodActivity.this);
				mUi.post(new Runnable() {
					@Override
					public void run() {
						mLoading.setVisibility(View.GONE);
						mSites = sites == null ? new ArrayList<SpiderSite>() : sites;
						if (mSites.isEmpty()) {
							Toast.makeText(SpiderVodActivity.this, "未找到爬虫源(type=3)，请检查配置", Toast.LENGTH_LONG).show();
							finish();
							return;
						}
						buildSiteRow();
						String key = getIntent().getStringExtra("siteKey");
						int idx = 0;
						for (int i = 0; i < mSites.size(); i++) {
							if (mSites.get(i).key.equals(key)) {
								idx = i;
								break;
							}
						}
						selectSite(idx);
					}
				});
			}
		});
	}

	private void buildSiteRow() {
		mSiteRow.removeAllViews();
		for (int i = 0; i < mSites.size(); i++) {
			final int idx = i;
			SpiderSite site = mSites.get(i);
			Button b = makeChip(site.name + kind(site), i == mSiteIdx);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (idx == mSiteIdx && mSpider != null) {
						return;
					}
					selectSite(idx);
				}
			});
			mSiteRow.addView(b);
		}
	}

	private static String kind(SpiderSite site) {
		if (site.isPy()) {
			return "  PY";
		}
		if (site.isJs()) {
			return "  JS";
		}
		return "";
	}

	private void selectSite(final int idx) {
		mSiteIdx = idx;
		mSite = mSites.get(idx);
		mTitle.setText(mSite.name);
		mCatIdx = -1;
		mKeyword = "";
		mSearchInput.setText("");
		mLines = new ArrayList<SpiderApi.Line>();
		mDetailPanel.setVisibility(View.GONE);
		buildSiteRow();
		mCatRow.removeAllViews();
		mList = new JSONArray();
		mAdapter.notifyDataSetChanged();
		mLoading.setText("正在初始化 " + mSite.name + " ...");
		mLoading.setVisibility(View.VISIBLE);
		run(new Runnable() {
			@Override
			public void run() {
				final Spider spider = SpiderEngine.get().getSpider(mSite);
				final String name = mSite.name;
				mUi.post(new Runnable() {
					@Override
					public void run() {
						mLoading.setVisibility(View.GONE);
						if (spider == null) {
							Toast.makeText(SpiderVodActivity.this, name + " 加载失败", Toast.LENGTH_LONG).show();
							return;
						}
						mSpider = spider;
						loadHome();
					}
				});
			}
		});
	}

	/* ==================== 分类 ==================== */

	private void loadHome() {
		mLoading.setText("正在获取分类...");
		mLoading.setVisibility(View.VISIBLE);
		run(new Runnable() {
			@Override
			public void run() {
				final JSONArray classes = SpiderApi.homeClasses(mSpider, true);
				final JSONObject filters = SpiderApi.homeFilters(mSpider);
				mUi.post(new Runnable() {
					@Override
					public void run() {
						mLoading.setVisibility(View.GONE);
						mFilters = filters;
						mClasses = new ArrayList<JSONObject>();
						for (int i = 0; i < classes.length(); i++) {
							JSONObject c = classes.optJSONObject(i);
							if (c != null) {
								mClasses.add(c);
							}
						}
						buildCatRow();
						if (mClasses.isEmpty()) {
							mCatIdx = -1;
							loadList();
						} else {
							selectCat(0);
						}
					}
				});
			}
		});
	}

	private void buildCatRow() {
		mCatRow.removeAllViews();
		for (int i = 0; i < mClasses.size(); i++) {
			final int idx = i;
			Button b = makeChip(mClasses.get(i).optString("type_name"), i == mCatIdx);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectCat(idx);
				}
			});
			mCatRow.addView(b);
		}
	}

	private void selectCat(int idx) {
		mCatIdx = idx;
		mExtend = new HashMap<String, String>();
		mKeyword = "";
		mPage = 1;
		buildCatRow();
		loadList();
	}

	/* ==================== 列表 ==================== */

	private void loadList() {
		if (mSpider == null) {
			return;
		}
		mLoading.setText("加载中...");
		mLoading.setVisibility(View.VISIBLE);
		run(new Runnable() {
			@Override
			public void run() {
				JSONObject ret;
				if (mKeyword.length() > 0) {
					ret = SpiderApi.search(mSpider, mKeyword, false);
				} else if (mCatIdx >= 0 && mCatIdx < mClasses.size()) {
					JSONObject cls = mClasses.get(mCatIdx);
					ret = SpiderApi.category(mSpider, cls.optString("type_id"), String.valueOf(mPage), mExtend.isEmpty() == false, mExtend);
				} else {
					ret = SpiderApi.homeVideo(mSpider);
				}
				final JSONObject result = ret;
				mUi.post(new Runnable() {
					@Override
					public void run() {
						mLoading.setVisibility(View.GONE);
						mList = result.optJSONArray("list");
						if (mList == null) {
							mList = new JSONArray();
						}
						mPageCount = Math.max(1, result.optInt("pagecount", 1));
						mPage = Math.max(1, result.optInt("page", mPage));
						mPageInfo.setText(mPage + " / " + mPageCount);
						mTypeName = mKeyword.length() > 0 ? "搜索" : (mCatIdx >= 0 ? mClasses.get(mCatIdx).optString("type_name") : "推荐");
						mAdapter.notifyDataSetChanged();
					}
				});
			}
		});
	}

	/* ==================== 详情 ==================== */

	private void openDetail(int position) {
		JSONObject item = mList.optJSONObject(position);
		if (item == null) {
			return;
		}
		final String id = item.optString("vod_id");
		if (id.length() == 0) {
			return;
		}
		mLoading.setText("正在获取详情...");
		mLoading.setVisibility(View.VISIBLE);
		run(new Runnable() {
			@Override
			public void run() {
				final JSONObject detail = SpiderApi.detail(mSpider, id);
				mUi.post(new Runnable() {
					@Override
					public void run() {
						mLoading.setVisibility(View.GONE);
						if (detail == null || detail.length() == 0) {
							Toast.makeText(SpiderVodActivity.this, "详情获取失败", Toast.LENGTH_SHORT).show();
							return;
						}
						showDetail(detail);
					}
				});
			}
		});
	}

	private void showDetail(JSONObject vod) {
		mDetailName.setText(vod.optString("vod_name"));
		mDetailDirector.setText("导演：" + vod.optString("vod_director"));
		mDetailActors.setText("主演：" + vod.optString("vod_actor"));
		mDetailArea.setText("地区：" + vod.optString("vod_area"));
		mDetailYear.setText("年代：" + vod.optString("vod_year"));
		mDetailType.setText("类型：" + vod.optString("vod_class"));
		mDetailRemarks.setText("备注：" + vod.optString("vod_remarks"));
		mDetailIntro.setText(vod.optString("vod_content"));
		loadPic(vod.optString("vod_pic"), mDetailPic);
		mLines = SpiderApi.lines(vod);
		mLineIdx = 0;
		buildSourceRow();
		buildEpisodeGrid();
		mDetailPanel.setVisibility(View.VISIBLE);
	}

	/**
	 * 构建源选择 RadioGroup（一剧多源）
	 */
	private void buildSourceRow() {
		mDetailSources.removeAllViews();
		if (mLines.isEmpty()) {
			return;
		}
		for (int i = 0; i < mLines.size(); i++) {
			final int idx = i;
			RadioButton rb = new RadioButton(this);
			rb.setText(mLines.get(i).getFlag());
			rb.setTextColor(Color.WHITE);
			rb.setButtonDrawable(null);
			rb.setPadding(16, 4, 16, 4);
			rb.setGravity(Gravity.CENTER);
			rb.setBackgroundColor(i == mLineIdx ? 0xFF4FC3F7 : 0xFF2A313D);
			rb.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mLineIdx = idx;
					buildSourceRow();
					buildEpisodeGrid();
				}
			});
			RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.rightMargin = 8;
			mDetailSources.addView(rb, lp);
		}
	}

	/**
	 * 构建剧集 GridView
	 */
	private void buildEpisodeGrid() {
		if (mLines.isEmpty()) {
			mEpisodeAdapter.notifyDataSetChanged();
			return;
		}
		mEpisodeAdapter.notifyDataSetChanged();
	}

	/* ==================== 播放 ==================== */

	private void playAt(final int idx) {
		if (mLines.isEmpty()) {
			return;
		}
		ArrayList<SpiderApi.Item> items = mLines.get(mLineIdx).getItems();
		if (idx < 0 || idx >= items.size()) {
			return;
		}
		final SpiderApi.Item item = items.get(idx);
		final String flag = mLines.get(mLineIdx).getFlag();
		if (item.id.startsWith("http")) {
			startPlayer(item.id, buildEpisodeList(items, idx), idx);
			return;
		}
		mLoading.setText("正在解析播放地址...");
		mLoading.setVisibility(View.VISIBLE);
		run(new Runnable() {
			@Override
			public void run() {
				final SpiderApi.PlayUrl playUrl = SpiderApi.play(mSpider, flag, item.id, new ArrayList<String>());
				mUi.post(new Runnable() {
					@Override
					public void run() {
						mLoading.setVisibility(View.GONE);
						String url = playUrl == null ? "" : playUrl.url;
						if (url == null || url.length() == 0) {
							Toast.makeText(SpiderVodActivity.this, "解析失败", Toast.LENGTH_SHORT).show();
							return;
						}
						ArrayList<VideoInfo> infos = new ArrayList<VideoInfo>();
						VideoInfo info = new VideoInfo();
						info.title = item.name;
						info.url = url;
						infos.add(info);
						startPlayer(url, infos, 0, playUrl.parse != 0);
					}
				});
			}
		});
	}

	private static ArrayList<VideoInfo> buildEpisodeList(ArrayList<SpiderApi.Item> items, int idx) {
		ArrayList<VideoInfo> infos = new ArrayList<VideoInfo>();
		for (SpiderApi.Item item : items) {
			VideoInfo info = new VideoInfo();
			info.title = item.name;
			info.url = item.id;
			infos.add(info);
		}
		return infos;
	}

	private void startPlayer(String url, ArrayList<VideoInfo> infos, int idx) {
		startPlayer(url, infos, idx, !VideoList.getProxiedUrl(url).equals(url));
	}

	private void startPlayer(String url, ArrayList<VideoInfo> infos, int idx, boolean web) {
		Intent it = new Intent();
		if (!web) {
			it.setClass(this, NetVodPlayerActivity.class);
		} else {
			for (VideoInfo info : infos) {
				info.url = VideoList.getProxiedUrl(info.url);
			}
			it.setClass(this, WebVideoPlayerActivity.class);
		}
		it.putParcelableArrayListExtra("videoinfo", infos);
		it.putExtra("albumPic", "");
		it.putExtra("vodtype", mTypeName.length() == 0 ? "其它" : mTypeName);
		it.putExtra("videoId", "");
		it.putExtra("vodname", mDetailName.getText().toString());
		it.putExtra("sourceId", mSite == null ? "" : mSite.name);
		it.putExtra("playIndex", idx);
		it.putExtra("collectionTime", 0);
		startActivity(it);
	}

	/* ==================== 通用 ==================== */

	private void findViews() {
		mSiteRow = (LinearLayout) findViewById(R.id.net_site_row);
		mCatRow = (LinearLayout) findViewById(R.id.net_cat_row);
		mGrid = (GridView) findViewById(R.id.net_vod_grid);
		mPageInfo = (TextView) findViewById(R.id.net_page_info);
		mLoading = (TextView) findViewById(R.id.net_vod_loading);
		mDetailPanel = findViewById(R.id.net_detail_panel);
		mFilterPanel = findViewById(R.id.net_filter_panel);
		mDetailName = (TextView) findViewById(R.id.net_detail_name);
		mDetailDirector = (TextView) findViewById(R.id.net_detail_director);
		mDetailActors = (TextView) findViewById(R.id.net_detail_actors);
		mDetailArea = (TextView) findViewById(R.id.net_detail_area);
		mDetailYear = (TextView) findViewById(R.id.net_detail_year);
		mDetailType = (TextView) findViewById(R.id.net_detail_type);
		mDetailRemarks = (TextView) findViewById(R.id.net_detail_remarks);
		mDetailIntro = (TextView) findViewById(R.id.net_detail_intro);
		mDetailPic = (ImageView) findViewById(R.id.net_detail_pic);
		mDetailSources = (RadioGroup) findViewById(R.id.net_detail_sources);
		mDetailPlay = (Button) findViewById(R.id.net_detail_play);
		mDetailEpisodes = (GridView) findViewById(R.id.net_detail_episodes);
		mSearchInput = (EditText) findViewById(R.id.net_search_input);
		mTitle = (TextView) findViewById(R.id.net_title);
		mLoading.setText("加载中...");
	}

	private void setBtn(int id, View.OnClickListener l) {
		((Button) findViewById(id)).setOnClickListener(l);
	}

	private Button makeChip(String text, boolean active) {
		Button b = new Button(this);
		b.setText(text);
		b.setTextSize(13);
		b.setPadding(20, 6, 20, 6);
		b.setTextColor(active ? 0xFF12151C : 0xFFE6E9EE);
		b.setBackgroundColor(active ? 0xFF4FC3F7 : 0xFF2A313D);
		b.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.rightMargin = 8;
		b.setLayoutParams(lp);
		return b;
	}

	private void run(Runnable r) {
		mPool.execute(r);
	}

	private void loadPic(final String url, final ImageView view) {
		if (url == null || url.length() == 0) {
			view.setImageBitmap(null);
			return;
		}
		Bitmap cached = mPicCache.get(url);
		if (cached != null) {
			view.setImageBitmap(cached);
			return;
		}
		view.setImageBitmap(null);
		view.setTag(url);
		new Thread(new Runnable() {
			@Override
			public void run() {
				Bitmap bmp = null;
				try {
					byte[] data = TvBoxConfig.fetchBytes(url, 10000);
					if (data != null) {
						bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
					}
				} catch (Exception e) {
				}
				if (bmp != null) {
					mPicCache.put(url, bmp);
				}
				final Bitmap fBmp = bmp;
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (url.equals(view.getTag())) {
							view.setImageBitmap(fBmp);
						}
					}
				});
			}
		}, "spider-pic").start();
	}

	/* ==================== 列表适配器 ==================== */

	private class GridAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return mList.length();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				v = LayoutInflater.from(SpiderVodActivity.this).inflate(R.layout.net_vod_item, parent, false);
			}
			JSONObject item = mList.optJSONObject(position);
			if (item == null) {
				return v;
			}
			ImageView pic = (ImageView) v.findViewById(R.id.net_item_pic);
			TextView name = (TextView) v.findViewById(R.id.net_item_name);
			TextView remarks = (TextView) v.findViewById(R.id.net_item_remarks);
			name.setText(item.optString("vod_name"));
			remarks.setText(item.optString("vod_remarks"));
			loadPic(item.optString("vod_pic"), pic);
			return v;
		}
	}

	/* ==================== 剧集适配器 ==================== */

	private class EpisodeAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			if (mLines.isEmpty()) {
				return 0;
			}
			return mLines.get(mLineIdx).getItems().size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView tv;
			if (convertView instanceof TextView) {
				tv = (TextView) convertView;
			} else {
				tv = new TextView(SpiderVodActivity.this);
				tv.setPadding(12, 8, 12, 8);
				tv.setTextColor(Color.WHITE);
				tv.setTextSize(13);
				tv.setGravity(Gravity.CENTER);
				tv.setBackgroundColor(0xFF2A313D);
			}
			tv.setText(mLines.get(mLineIdx).getItems().get(position).name);
			return tv;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mPool.shutdownNow();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && mDetailPanel.getVisibility() == View.VISIBLE) {
			mDetailPanel.setVisibility(View.GONE);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}
